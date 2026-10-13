// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.content.Context
import android.content.SharedPreferences
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.VideoFPSPreset
import com.metallic.chiaki.lib.VideoResolutionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

class Preferences(context: Context)
{
	enum class Resolution(val value: String, val title: String, val preset: VideoResolutionPreset)
	{
		RES_360P("360p", "360p", VideoResolutionPreset.RES_360P),
		RES_540P("540p", "540p", VideoResolutionPreset.RES_540P),
		RES_720P("720p", "720p", VideoResolutionPreset.RES_720P),
		RES_1080P("1080p", "1080p", VideoResolutionPreset.RES_1080P),
	}

	enum class FPS(val value: String, val title: String, val preset: VideoFPSPreset)
	{
		FPS_30("30", "30 FPS", VideoFPSPreset.FPS_30),
		FPS_60("60", "60 FPS", VideoFPSPreset.FPS_60)
	}

	enum class Codec(val value: String, val title: String, val codec: com.metallic.chiaki.lib.Codec)
	{
		CODEC_H264("h264", "H.264", com.metallic.chiaki.lib.Codec.CODEC_H264),
		CODEC_H265("h265", "H.265", com.metallic.chiaki.lib.Codec.CODEC_H265),
		CODEC_H265_HDR("h265_hdr", "H.265 HDR", com.metallic.chiaki.lib.Codec.CODEC_H265_HDR)
	}

	enum class MenuShortcut(val value: String, val title: String)
	{
		L3_R3("l3_r3", "L3 + R3"),
		SELECT_START("select_start", "Select + Start"),
		SHOULDERS("shoulders", "L1 + R1 + L2 + R2")
	}

	enum class DisplayMode(val value: String, val title: String)
	{
		FIT("fit", "Fit"),
		ZOOM("zoom", "Zoom"),
		STRETCH("stretch", "Stretch")
	}

	enum class SleepConsoleOnExit(val value: String, val title: String)
	{
		NEVER("never", "Never"),
		ASK("ask", "Ask"),
		ALWAYS("always", "Always")
	}

	companion object
	{
		const val PREFS_NAME = "chiaki_tv_preferences"

		const val resolutionDefaultKey = "resolution"
		const val fpsKey = "fps"
		const val bitrateKey = "bitrate"
		const val codecKey = "codec"
		const val discoveryEnabledKey = "discovery_enabled"
		const val rumbleEnabledKey = "rumble_enabled"
		const val motionEnabledKey = "motion_enabled"
		const val buttonHapticEnabledKey = "button_haptic_enabled"
		const val logVerboseKey = "log_verbose"
		const val swapCrossMoonKey = "swap_cross_moon"
		const val dpadAsControllerKey = "dpad_as_controller"
		const val displayModeKey = "display_mode"
		const val audioVolumeKey = "audio_volume"
		const val audioDeviceIdKey = "audio_device_id"
		const val audioDeviceNameKey = "audio_device_name"
		const val tvChannelIdKey = "tv_channel_id"
		const val audioBufferMsKey = "audio_buffer_ms"
		const val autoConnectEnabledKey = "auto_connect_enabled"
		const val autoConnectMacKey = "auto_connect_mac"
		const val menuShortcutKey = "menu_shortcut"
		const val sleepConsoleOnExitKey = "sleep_console_on_exit"

		val resolutionDefault = Resolution.RES_1080P
		val fpsDefault = FPS.FPS_60
		val codecDefault = Codec.CODEC_H265
	}

	private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		when(key)
		{
			resolutionDefaultKey -> _bitrateAutoFlow.value = bitrateAuto
		}
	}.also { sharedPreferences.registerOnSharedPreferenceChangeListener(it) }

	var discoveryEnabled
		get() = sharedPreferences.getBoolean(discoveryEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(discoveryEnabledKey, value).apply() }

	var rumbleEnabled
		get() = sharedPreferences.getBoolean(rumbleEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(rumbleEnabledKey, value).apply() }

	var motionEnabled
		get() = sharedPreferences.getBoolean(motionEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(motionEnabledKey, value).apply() }

	var buttonHapticEnabled
		get() = sharedPreferences.getBoolean(buttonHapticEnabledKey, true)
		set(value) { sharedPreferences.edit().putBoolean(buttonHapticEnabledKey, value).apply() }

	var logVerbose
		get() = sharedPreferences.getBoolean(logVerboseKey, false)
		set(value) { sharedPreferences.edit().putBoolean(logVerboseKey, value).apply() }

	var swapCrossMoon
		get() = sharedPreferences.getBoolean(swapCrossMoonKey, false)
		set(value) { sharedPreferences.edit().putBoolean(swapCrossMoonKey, value).apply() }

	var dpadAsController
		get() = sharedPreferences.getBoolean(dpadAsControllerKey, true)
		set(value) { sharedPreferences.edit().putBoolean(dpadAsControllerKey, value).apply() }

	var audioVolume
		get() = sharedPreferences.getInt(audioVolumeKey, 100)
		set(value) { sharedPreferences.edit().putInt(audioVolumeKey, value.coerceIn(0, 100)).apply() }

	var audioDeviceId
		get() = sharedPreferences.getInt(audioDeviceIdKey, 0)
		set(value) { sharedPreferences.edit().putInt(audioDeviceIdKey, value).apply() }

	var audioDeviceName
		get() = sharedPreferences.getString(audioDeviceNameKey, "") ?: ""
		set(value) { sharedPreferences.edit().putString(audioDeviceNameKey, value).apply() }

	var tvChannelId
		get() = sharedPreferences.getLong(tvChannelIdKey, 0L)
		set(value) { sharedPreferences.edit().putLong(tvChannelIdKey, value).apply() }

	var audioBufferMs
		get() = sharedPreferences.getInt(audioBufferMsKey, 0)
		set(value) { sharedPreferences.edit().putInt(audioBufferMsKey, value.coerceIn(0, 100)).apply() }

	var autoConnectEnabled
		get() = sharedPreferences.getBoolean(autoConnectEnabledKey, false)
		set(value) { sharedPreferences.edit().putBoolean(autoConnectEnabledKey, value).apply() }

	var autoConnectMac
		get() = sharedPreferences.getString(autoConnectMacKey, "") ?: ""
		set(value) { sharedPreferences.edit().putString(autoConnectMacKey, value).apply() }

	var menuShortcut
		get() = MenuShortcut.values().firstOrNull { it.value == sharedPreferences.getString(menuShortcutKey, MenuShortcut.L3_R3.value) } ?: MenuShortcut.L3_R3
		set(value) { sharedPreferences.edit().putString(menuShortcutKey, value.value).apply() }

	var displayMode
		get() = DisplayMode.values().firstOrNull { it.value == sharedPreferences.getString(displayModeKey, DisplayMode.FIT.value) } ?: DisplayMode.FIT
		set(value) { sharedPreferences.edit().putString(displayModeKey, value.value).apply() }

	var sleepConsoleOnExit
		get() = SleepConsoleOnExit.values().firstOrNull { it.value == sharedPreferences.getString(sleepConsoleOnExitKey, SleepConsoleOnExit.ASK.value) } ?: SleepConsoleOnExit.ASK
		set(value) { sharedPreferences.edit().putString(sleepConsoleOnExitKey, value.value).apply() }

	var resolution
		get() = sharedPreferences.getString(resolutionDefaultKey, resolutionDefault.value)?.let { value ->
			Resolution.values().firstOrNull { it.value == value }
		} ?: resolutionDefault
		set(value) { sharedPreferences.edit().putString(resolutionDefaultKey, value.value).apply() }

	var fps
		get() = sharedPreferences.getString(fpsKey, fpsDefault.value)?.let { value ->
			FPS.values().firstOrNull { it.value == value }
		} ?: fpsDefault
		set(value) { sharedPreferences.edit().putString(fpsKey, value.value).apply() }

	fun validateBitrate(bitrate: Int) = max(2000, min(50000, bitrate))
	var bitrate
		get() = sharedPreferences.getInt(bitrateKey, 0).let { if(it == 0) null else validateBitrate(it) }
		set(value) { sharedPreferences.edit().putInt(bitrateKey, if(value != null) validateBitrate(value) else 0).apply() }
	val bitrateAuto get() = videoProfileDefaultBitrate.bitrate
	private val _bitrateAutoFlow by lazy { MutableStateFlow(bitrateAuto) }
	val bitrateAutoFlow: StateFlow<Int> get() = _bitrateAutoFlow.asStateFlow()

	var codec
		get() = sharedPreferences.getString(codecKey, codecDefault.value)?.let { value ->
			Codec.values().firstOrNull { it.value == value }
		} ?: codecDefault
		set(value) { sharedPreferences.edit().putString(codecKey, value.value).apply() }

	private val videoProfileDefaultBitrate get() = ConnectVideoProfile.preset(resolution.preset, fps.preset, codec.codec)
	val videoProfile get() = videoProfileDefaultBitrate.let {
		val bitrate = bitrate
		if(bitrate == null)
			it
		else
			it.copy(bitrate = bitrate)
	}
}
