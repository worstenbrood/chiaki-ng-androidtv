// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks connected gamepads and assigns each a player slot (0..MAX_PADS-1), mirroring the
 * Remote Play protocol's 7-slot userId model (the official core keys pad data, rumble and
 * lightbar by userId). Slots are stable across device removal; a missing player never renumbers the other pads.
 */
class PadManager(context: Context)
{
	companion object
	{
		const val MAX_PADS = 7

		fun isGamepad(device: InputDevice?): Boolean
		{
			// Strict on purpose: SOURCE_GAMEPAD (0x400) is also set by TV remotes and virtual
			// keyboards; SOURCE_JOYSTICK is the stick/gamepad class that real pads
			// (DualSense, Xbox, 8BitDo, ...) all report.
			if(device == null || device.isVirtual)
				return false
			return device.sources and InputDevice.SOURCE_JOYSTICK != 0
		}
	}

	private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
	private var registered = false
	private val deviceSlots = PadSlots(MAX_PADS)
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
		override fun onInputDeviceAdded(deviceId: Int) { refresh() }
		override fun onInputDeviceChanged(deviceId: Int) { refresh() }
		override fun onInputDeviceRemoved(deviceId: Int) { refresh() }
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
		registered = true
		refresh()
	}

	fun stop()
	{
		if(!registered)
			return
		inputManager.unregisterInputDeviceListener(listener)
		registered = false
	}

	/**
	 * True when events from [deviceId] should reach the session: the primary pad — or any
	 * device while no gamepad is connected at all (remote-only setups keep working).
	 */
	fun isPrimary(deviceId: Int): Boolean = _pads.value.isEmpty() || deviceId == primaryDeviceId

	/** Stable protocol slot (0 = primary), or null if not a tracked gamepad. */
	fun slotOf(deviceId: Int): Int? = deviceSlots.slotOf(deviceId)

	private fun refresh()
	{
		val previous = slots
		val devices = InputDevice.getDeviceIds().asSequence().mapNotNull { InputDevice.getDevice(it) }
			.filter { isGamepad(it) }.toList()
		deviceSlots.update(devices.map { it.id })
		primaryDeviceId = slots[0] ?: -1
		_pads.value = devices.filter { deviceSlots.slotOf(it.id) != null }.sortedBy { deviceSlots.slotOf(it.id) }
		if(previous != slots) slotsChangedCallback?.invoke(slots)
	}
}
