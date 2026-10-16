// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

object AudioDevices
{
	private val OUTPUT_TYPES = setOf(
		AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
		AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
		AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
		AudioDeviceInfo.TYPE_WIRED_HEADSET,
		AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
		AudioDeviceInfo.TYPE_HDMI,
		AudioDeviceInfo.TYPE_HDMI_ARC,
		AudioDeviceInfo.TYPE_HDMI_EARC,
		AudioDeviceInfo.TYPE_USB_DEVICE,
		AudioDeviceInfo.TYPE_USB_HEADSET
	)

	private val INPUT_TYPES = setOf(
		AudioDeviceInfo.TYPE_BUILTIN_MIC,
		AudioDeviceInfo.TYPE_WIRED_HEADSET,
		AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
		AudioDeviceInfo.TYPE_USB_HEADSET,
		AudioDeviceInfo.TYPE_USB_DEVICE
	)

	fun inputDevices(context: Context): List<Pair<String, Int>>
	{
		val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		return listOf("Default" to 0) + am.getDevices(AudioManager.GET_DEVICES_INPUTS)
			.filter { it.isSource && INPUT_TYPES.contains(it.type) }
			.map { (it.productName?.toString() ?: "Input ${it.type}") to it.id }
			.distinctBy { it.second }
	}

	/** Resolves a saved input device name to its current device; null means "use the system default". */
	fun resolveInputDevice(context: Context, name: String): AudioDeviceInfo?
	{
		if(name.isEmpty() || name == "Default")
			return null
		val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		return am.getDevices(AudioManager.GET_DEVICES_INPUTS)
			.firstOrNull { it.isSource && it.productName?.toString() == name }
	}

	fun outputDevices(context: Context): List<Pair<String, Int>>
	{
		val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		return listOf("Default" to 0) + am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
			.filter { it.isSink && OUTPUT_TYPES.contains(it.type) }
			.map { (it.productName?.toString() ?: "Device ${it.type}") to it.id }
			.distinctBy { it.second }
	}

	/**
	 * Audio device ids are not stable across reconnects/reboots, so the saved name is the
	 * source of truth; resolve it to the device's current id. Falls back to the legacy saved
	 * id when no name was ever stored, and to the system default when the device is gone.
	 */
	fun resolveDeviceId(context: Context, name: String, fallbackId: Int): Int
	{
		if(name.isEmpty())
			return fallbackId
		if(name == "Default")
			return 0
		return outputDevices(context).firstOrNull { it.first == name }?.second ?: 0
	}
}
