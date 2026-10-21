// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks connected gamepads and assigns each a player slot (0..MAX_PADS-1), mirroring the
 * Remote Play protocol's 7-slot userId model (the official core keys pad data, rumble and
 * lightbar by userId). Only the primary pad (slot 0) drives the session for now; once
 * upstream ships multi-controller support the slot becomes the userId handed to the session.
 */
class PadManager(context: Context)
{
	companion object
	{
		const val MAX_PADS = 7

		fun isGamepad(device: InputDevice?): Boolean
		{
			if(device == null)
				return false
			if(device.vendorId == 0x054c) // Sony Interactive Entertainment (DualShock/DualSense)
				return true
			return device.sources and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
		}
	}

	private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
	private var registered = false

	private val _pads = MutableStateFlow<List<InputDevice>>(emptyList())

	/** Connected gamepads in slot order (list index = slot / future userId). */
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
		inputManager.registerInputDeviceListener(listener, null)
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

	private fun refresh()
	{
		val pads = ArrayList<InputDevice>(MAX_PADS)
		for(id in InputDevice.getDeviceIds())
		{
			val device = InputDevice.getDevice(id) ?: continue
			if(isGamepad(device))
				pads.add(device)
			if(pads.size >= MAX_PADS)
				break
		}
		_pads.value = pads
		if(pads.none { d -> d.id == primaryDeviceId })
			primaryDeviceId = pads.firstOrNull()?.id ?: -1
	}
}
