// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.view.InputDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chiaking.tv.common.settings.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks connected gamepads and assigns each a player slot (0..MAX_PADS-1), mirroring the
 * Remote Play protocol's 7-slot userId model (the official core keys pad data, rumble and
 * lightbar by userId). A preferred primary may swap with slot 0; other slots stay stable.
 */
class PadManager(context: Context, private val preferences: Preferences)
{
	companion object
	{
		const val MAX_PADS = 7

		fun isGamepad(device: InputDevice?): Boolean
		{
			// Strict on purpose: SOURCE_GAMEPAD (0x400) is also set by TV remotes and virtual
			// keyboards; SOURCE_JOYSTICK is the stick/gamepad class that real pads
			// (DualSense, Xbox, 8BitDo, ...) all report.
			return device != null && isGamepadSource(device.sources, device.isVirtual)
		}

		fun connectedGamepads(): List<InputDevice> = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull()
			.filter { isGamepad(it) }
	}

	private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
	private val sharedPreferences = context.getSharedPreferences(Preferences.PREFS_NAME, Context.MODE_PRIVATE)
	private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if(key == Preferences.primaryControllerDescriptorKey || key == Preferences.navigationOnlyControllerDescriptorKey)
			refresh("controller preference changed")
	}
	private var registered = false
	private val deviceSlots = PadSlots(MAX_PADS)
	private var navigationOnlyDeviceIds = emptySet<Int>()
	val slots: Map<Int, Int> get() = deviceSlots.assignments
	var slotsChangedCallback: ((Map<Int, Int>) -> Unit)? = null

	private val _pads = MutableStateFlow<List<InputDevice>>(emptyList())

	/** Connected gamepads in slot order; use slotOf(), since disconnected slots leave gaps. */
	val pads: StateFlow<List<InputDevice>> = _pads.asStateFlow()

	/** The device driving player 1 (slot 0); -1 while no gamepad is connected. */
	@Volatile var primaryDeviceId: Int = -1
		private set

	private val listener = object: InputManager.InputDeviceListener
	{
		override fun onInputDeviceAdded(deviceId: Int) { refresh("added deviceId=$deviceId") }
		override fun onInputDeviceChanged(deviceId: Int) { refresh("changed deviceId=$deviceId") }
		override fun onInputDeviceRemoved(deviceId: Int) { refresh("removed deviceId=$deviceId") }
	}

	init
	{
		start()
	}

	fun start()
	{
		if(registered)
			return
		inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
		sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
		registered = true
		refresh("start")
	}

	fun stop()
	{
		if(!registered)
			return
		inputManager.unregisterInputDeviceListener(listener)
		sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
		registered = false
	}

	/**
	 * True when events from [deviceId] should reach the session: the primary pad — or any
	 * device while no gamepad is connected at all (remote-only setups keep working).
	 */
	fun isPrimary(deviceId: Int): Boolean = _pads.value.isEmpty() || deviceId == primaryDeviceId

	/** Stable protocol slot (0 = primary), or null if not a tracked gamepad. */
	fun slotOf(deviceId: Int): Int? = deviceSlots.slotOf(deviceId)

	fun isNavigationOnly(deviceId: Int): Boolean = deviceId in navigationOnlyDeviceIds

	fun isSessionGamepad(device: InputDevice?): Boolean = device != null && isGamepad(device) && !isNavigationOnly(device.id)

	private fun refresh(reason: String)
	{
		val previous = slots
		val allDevices = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull()
		val infos = allDevices.map { PadDeviceInfo(it.id, it.descriptor, it.name, it.vendorId, it.productId, it.sources, it.isVirtual) }
		val navigationOnly = preferences.navigationOnlyControllerDescriptor
		navigationOnlyDeviceIds = infos.filter { navigationOnly.isNotEmpty() && it.descriptor == navigationOnly }.map { it.id }.toSet()
		val preferred = preferredPrimaryPad(infos, preferences.primaryControllerDescriptor, primaryDeviceId, navigationOnly)
		val devices = allDevices.filter { isSessionGamepad(it) }
		deviceSlots.update(devices.map { it.id }, preferred)
		primaryDeviceId = slots[0] ?: -1
		_pads.value = devices.filter { deviceSlots.slotOf(it.id) != null }.sortedBy { deviceSlots.slotOf(it.id) }
		Log.i("PadManager", "Input refresh: $reason mode=${if(preferences.primaryControllerDescriptor.isEmpty()) "automatic" else "manual"} preferredDeviceId=$preferred primaryDeviceId=$primaryDeviceId slots=$slots")
		for(info in infos) {
			val slot = slotOf(info.id)
			Log.i("PadManager", "Input device: id=${info.id} name=${info.name} descriptor=${info.descriptor} " +
				"vendorId=0x${info.vendorId.toString(16)} productId=0x${info.productId.toString(16)} sources=0x${info.sources.toString(16)} " +
				"virtual=${info.virtual} gamepad=${info.isGamepad} dualSense=${info.isDualSense} navigationOnly=${isNavigationOnly(info.id)} " +
				"player=${slot?.plus(1)} protocolSlot=$slot axes=${allDevices.first { it.id == info.id }.motionRanges.map { it.axis }}")
		}
		if(previous != slots) {
			for((slot, deviceId) in previous)
				if(slots[slot] != deviceId)
					Log.i("PadManager", "Controller unassigned: deviceId=$deviceId player=${slot + 1} protocolSlot=$slot")
			for((slot, deviceId) in slots) {
				if(previous[slot] != deviceId)
					Log.i("PadManager", "Controller assigned: deviceId=$deviceId player=${slot + 1} protocolSlot=$slot")
			}
			slotsChangedCallback?.invoke(slots)
		}
	}
}
