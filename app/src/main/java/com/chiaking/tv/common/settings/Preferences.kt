// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.settings

import android.content.Context
import android.content.SharedPreferences
import com.chiaking.tv.R
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

	// opus encoder CPU/quality presets (bitrate + bit depth are fixed by the PS5 protocol)
	enum class MicComplexity(val value: Int, val title: String)
	{
		LOW(3, "Low"),
		MEDIUM(5, "Medium"),
		HIGH(8, "High"),
		MAX(10, "Max")
	}

	// opus max-bandwidth constants; RESTRICTED_LOWDELAY caps at wideband (8 kHz)
	enum class MicBandwidth(val opusValue: Int, val title: String)
	{
		NARROWBAND(1101, "4 kHz"),
		MEDIUMBAND(1102, "6 kHz"),
		WIDEBAND(1103, "8 kHz")
	}

	enum class DisplayMode(val value: String, val titleRes: Int)
	{
		FIT("fit", R.string.display_mode_fit),
		ZOOM("zoom", R.string.display_mode_zoom),
		STRETCH("stretch", R.string.display_mode_stretch)
	}

	enum class DisconnectAction(val value: String, val titleRes: Int)
	{
		ASK("ask", R.string.value_ask),
		SLEEP("sleep", R.string.action_sleep_console),
		NOTHING("nothing", R.string.action_do_nothing)
	}

	enum class HudItemKind(val value: String, val titleRes: Int)
	{
		CONSOLE("console", R.string.hud_item_console),
		VIDEO("video", R.string.video),
		DISPLAY("display", R.string.display),
		STREAM("stream", R.string.stream),
		NETWORK("network", R.string.network),
		AUDIO("audio", R.string.audio),
		MIC("mic", R.string.microphone),
		PITCH("pitch", R.string.mic_pitch),
		CPU("cpu", R.string.cpu),
		CLOSE("close", R.string.hud_close)
	}

	data class HudItemEntry(val kind: HudItemKind, val enabled: Boolean)

	companion object
	{
		const val PREFS_NAME = "chiaki_tv_preferences"

		const val BITRATE_MIN_KBPS = 2000
		const val BITRATE_MAX_KBPS = 50000
		const val BITRATE_STEP_KBPS = 500

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
		const val autoRegistDuidsKey = "auto_regist_duids"
		const val remoteHostMacsKey = "remote_host_macs"
		const val loginPinKey = "login_pin"
		const val menuShortcutKey = "menu_shortcut"
		const val sleepConsoleOnExitKey = "sleep_console_on_exit"
		const val disconnectActionKey = "disconnect_action"
		const val idrOnFecFailureKey = "idr_on_fec_failure"
		const val hudShowCpuKey = "hud_show_cpu"
		const val hudItemsKey = "hud_items"
		const val micEnabledKey = "mic_enabled"
		const val micStartUnmutedKey = "mic_start_unmuted"
		const val micDeviceNameKey = "mic_device_name"
		const val micGainKey = "mic_gain"
		const val micAecKey = "mic_aec"
		const val micNsKey = "mic_ns"
		const val micAgcKey = "mic_agc"
	const val micComplexityKey = "mic_complexity"
	const val micBandwidthKey = "mic_bandwidth"
	const val micPitchEnabledKey = "mic_pitch_enabled"
	const val micPitchKey = "mic_pitch"

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

	var micEnabled
		get() = sharedPreferences.getBoolean(micEnabledKey, false)
		set(value) { sharedPreferences.edit().putBoolean(micEnabledKey, value).apply() }

	var micStartUnmuted
		get() = sharedPreferences.getBoolean(micStartUnmutedKey, false)
		set(value) { sharedPreferences.edit().putBoolean(micStartUnmutedKey, value).apply() }

	var micDeviceName
		get() = sharedPreferences.getString(micDeviceNameKey, "") ?: ""
		set(value) { sharedPreferences.edit().putString(micDeviceNameKey, value).apply() }

	var micGain
		get() = sharedPreferences.getInt(micGainKey, 100)
		set(value) { sharedPreferences.edit().putInt(micGainKey, value.coerceIn(25, 300)).apply() }

	var micAec
		get() = sharedPreferences.getBoolean(micAecKey, true)
		set(value) { sharedPreferences.edit().putBoolean(micAecKey, value).apply() }

	var micNs
		get() = sharedPreferences.getBoolean(micNsKey, true)
		set(value) { sharedPreferences.edit().putBoolean(micNsKey, value).apply() }

	var micAgc
		get() = sharedPreferences.getBoolean(micAgcKey, true)
		set(value) { sharedPreferences.edit().putBoolean(micAgcKey, value).apply() }

	var micComplexity
		get() = MicComplexity.values().firstOrNull { it.value == sharedPreferences.getInt(micComplexityKey, MicComplexity.MEDIUM.value) } ?: MicComplexity.MEDIUM
		set(value) { sharedPreferences.edit().putInt(micComplexityKey, value.value).apply() }

	var micBandwidth
		get() = MicBandwidth.values().firstOrNull { it.opusValue == sharedPreferences.getInt(micBandwidthKey, MicBandwidth.WIDEBAND.opusValue) } ?: MicBandwidth.WIDEBAND
		set(value) { sharedPreferences.edit().putInt(micBandwidthKey, value.opusValue).apply() }

	var micPitchEnabled
		get() = sharedPreferences.getBoolean(micPitchEnabledKey, false)
		set(value) { sharedPreferences.edit().putBoolean(micPitchEnabledKey, value).apply() }

	/** Pitch shift in semitones (-12..12); applied to mic audio with the Sonic library. */
	var micPitch
		get() = sharedPreferences.getInt(micPitchKey, 0).coerceIn(-12, 12)
		set(value) { sharedPreferences.edit().putInt(micPitchKey, value.coerceIn(-12, 12)).apply() }

	var autoConnectEnabled
		get() = sharedPreferences.getBoolean(autoConnectEnabledKey, false)
		set(value) { sharedPreferences.edit().putBoolean(autoConnectEnabledKey, value).apply() }

	var autoConnectMac
		get() = sharedPreferences.getString(autoConnectMacKey, "") ?: ""
		set(value) { sharedPreferences.edit().putString(autoConnectMacKey, value).apply() }

	var autoRegistDuids: Set<String>
		get() = sharedPreferences.getStringSet(autoRegistDuidsKey, emptySet()) ?: emptySet()
		set(value) { sharedPreferences.edit().putStringSet(autoRegistDuidsKey, value).apply() }

	/** Maps PSN device duid to console MAC as "duid|mac" so remote sessions can find their registered host. */
	var remoteHostMacs: Set<String>
		get() = sharedPreferences.getStringSet(remoteHostMacsKey, emptySet()) ?: emptySet()
		set(value) { sharedPreferences.edit().putStringSet(remoteHostMacsKey, value).apply() }

	var loginPin
		get() = sharedPreferences.getString(loginPinKey, "") ?: ""
		set(value) { sharedPreferences.edit().putString(loginPinKey, value).apply() }

	var menuShortcut
		get() = MenuShortcut.values().firstOrNull { it.value == sharedPreferences.getString(menuShortcutKey, MenuShortcut.L3_R3.value) } ?: MenuShortcut.L3_R3
		set(value) { sharedPreferences.edit().putString(menuShortcutKey, value.value).apply() }

	var displayMode
		get() = DisplayMode.values().firstOrNull { it.value == sharedPreferences.getString(displayModeKey, DisplayMode.FIT.value) } ?: DisplayMode.FIT
		set(value) { sharedPreferences.edit().putString(displayModeKey, value.value).apply() }

	/**
	 * What to do with the console when leaving the stream (explicit disconnect or backgrounding).
	 * Migrated from the legacy [sleepConsoleOnExitKey] when the merged pref was never set:
	 * always→SLEEP, never→NOTHING, ask→ASK.
	 */
	var disconnectAction: DisconnectAction
		get()
		{
			val stored = sharedPreferences.getString(disconnectActionKey, null)
			if(stored != null)
				return DisconnectAction.values().firstOrNull { it.value == stored } ?: DisconnectAction.ASK
			return when(sharedPreferences.getString(sleepConsoleOnExitKey, "ask"))
			{
				"always" -> DisconnectAction.SLEEP
				"never" -> DisconnectAction.NOTHING
				else -> DisconnectAction.ASK
			}
		}
		set(value) { sharedPreferences.edit().putString(disconnectActionKey, value.value).apply() }

	/** Default on (upstream Qt defaults off) — faster recovery from corrupted frames matters more on WiFi. */
	var idrOnFecFailure
		get() = sharedPreferences.getBoolean(idrOnFecFailureKey, true)
		set(value) { sharedPreferences.edit().putBoolean(idrOnFecFailureKey, value).apply() }

	/**
	 * Ordered HUD chips: each entry is "kind:0|1". Missing kinds are appended at the end;
	 * CPU's default comes from the legacy [hudShowCpuKey] so old installs keep their setting.
	 * CLOSE is always enabled (order-only).
	 */
	private var hudItemsCache: Pair<String?, List<HudItemEntry>>? = null
	var hudItems: List<HudItemEntry>
		get()
		{
			val raw = sharedPreferences.getString(hudItemsKey, null)
			hudItemsCache?.let { if(it.first == raw) return it.second }
			val cpuDefault = sharedPreferences.getBoolean(hudShowCpuKey, true)
			val parsed = (raw ?: "")
				.split(",")
				.mapNotNull { entry ->
					val kind = HudItemKind.entries.firstOrNull { it.value == entry.substringBefore(":").trim() }
						?: return@mapNotNull null
					HudItemEntry(kind, entry.substringAfter(":", "1").trim() != "0")
				}
				.distinctBy { it.kind }
			val missing = HudItemKind.entries
				.filter { kind -> parsed.none { it.kind == kind } }
				.map { HudItemEntry(it, it != HudItemKind.CPU || cpuDefault) }
			val result = (parsed + missing).map { if(it.kind == HudItemKind.CLOSE) it.copy(enabled = true) else it }
			hudItemsCache = raw to result
			return result
		}
		set(value)
		{
			hudItemsCache = null
			sharedPreferences.edit()
				.putString(hudItemsKey, value.joinToString(",") { "${it.kind.value}:${if(it.enabled) 1 else 0}" })
				.apply()
		}

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

	fun validateBitrate(bitrate: Int) = max(BITRATE_MIN_KBPS, min(BITRATE_MAX_KBPS, bitrate))
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
