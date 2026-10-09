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

	fun outputDevices(context: Context): List<Pair<String, Int>>
	{
		val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		return listOf("Default" to 0) + am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
			.filter { it.isSink && OUTPUT_TYPES.contains(it.type) }
			.map { (it.productName?.toString() ?: "Device ${it.type}") to it.id }
			.distinctBy { it.second }
	}
}
