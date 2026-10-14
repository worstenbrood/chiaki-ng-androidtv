// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.BuildConfig
import com.chiaking.tv.R
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.common.RegisteredHost
import com.chiaking.tv.ui.*
import kotlinx.coroutines.delay

// Keep in sync with CHIAKI_VERSION in external/chiaki-ng/CMakeLists.txt (see docs/UPSTREAM_SYNC.md)
const val UPSTREAM_CHIAKI_VERSION = "1.10.0"

private const val FOCUS_RESTORE_DELAY_MS = 100L

@Composable
fun SettingsScreen(
	preferences: Preferences,
	registeredHostCount: Int,
	registeredHosts: List<RegisteredHost>,
	onBack: () -> Unit,
	onShowLogs: () -> Unit,
	onManageHosts: () -> Unit,
	onExportSettings: () -> Unit,
	onImportSettings: () -> Unit
)
{
	var resolution by remember { mutableStateOf(preferences.resolution) }
	var fps by remember { mutableStateOf(preferences.fps) }
	var codec by remember { mutableStateOf(preferences.codec) }
	var bitrate by remember { mutableStateOf(preferences.bitrate) }
	var discovery by remember { mutableStateOf(preferences.discoveryEnabled) }
	var rumble by remember { mutableStateOf(preferences.rumbleEnabled) }
	var motion by remember { mutableStateOf(preferences.motionEnabled) }
	var haptics by remember { mutableStateOf(preferences.buttonHapticEnabled) }
	var swapCrossMoon by remember { mutableStateOf(preferences.swapCrossMoon) }
	var dpadAsController by remember { mutableStateOf(preferences.dpadAsController) }
	var logVerbose by remember { mutableStateOf(preferences.logVerbose) }
	var volume by remember { mutableStateOf(preferences.audioVolume) }
	var bufferMs by remember { mutableStateOf(preferences.audioBufferMs) }
	var autoConnect by remember { mutableStateOf(preferences.autoConnectEnabled) }
	var menuShortcut by remember { mutableStateOf(preferences.menuShortcut) }
	var displayMode by remember { mutableStateOf(preferences.displayMode) }
	var sleepConsoleOnExit by remember { mutableStateOf(preferences.sleepConsoleOnExit) }
	var disconnectAction by remember { mutableStateOf(preferences.disconnectAction) }
	var autoConnectIndex by remember { mutableStateOf(
		(registeredHosts.indexOfFirst { it.serverMac.toString() == preferences.autoConnectMac } + 1).coerceAtLeast(0)) }

	val autoConnectOptions = remember(registeredHosts) { listOf<RegisteredHost?>(null) + registeredHosts }

	val ctx = androidx.compose.ui.platform.LocalContext.current
	var psnLoggedIn by remember { mutableStateOf(com.chiaking.tv.common.psn.PsnTokenStore.get(ctx).isLoggedIn) }
	var showPsnLogin by remember { mutableStateOf(false) }
	val audioDevices = remember { AudioDevices.outputDevices(ctx) }
	var deviceIndex by remember { mutableStateOf(
		(audioDevices.indexOfFirst { it.first == preferences.audioDeviceName }
			.takeIf { it >= 0 }
			?: audioDevices.indexOfFirst { it.second == preferences.audioDeviceId })
			.coerceAtLeast(0)) }

	val restore = remember { FocusMemory.get("settings") }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		delay(FOCUS_RESTORE_DELAY_MS)
		try { if(restore == 0) backFocus.requestFocus() else rowFocus.requestFocus() } catch(e: Exception) { }
	}
	fun fr(i: Int) = if(i == restore) rowFocus else null
	fun fb(i: Int): (Boolean) -> Unit = { if(it) FocusMemory.set("settings", i) }

	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
		ScreenHeader(
			stringResource(R.string.settings), onBack,
			modifier = Modifier.padding(horizontal = 32.dp),
			backFocusRequester = if(restore == 0) backFocus else null,
			onBackFocus = { if(it) FocusMemory.set("settings", 0) }
		)

		SectionHeader(stringResource(R.string.stream))
		SettingsCard {
			TvCycleRow(
				title = stringResource(R.string.resolution),
				value = resolution.title,
				onNext = {
					resolution = Preferences.Resolution.values()[(resolution.ordinal + 1) % Preferences.Resolution.values().size]
					preferences.resolution = resolution
				},
				focusRequester = fr(1), onFocus = fb(1)
			)
			TvCycleRow(
				title = stringResource(R.string.frames_per_second),
				value = fps.title,
				onNext = {
					fps = Preferences.FPS.values()[(fps.ordinal + 1) % Preferences.FPS.values().size]
					preferences.fps = fps
				},
				focusRequester = fr(2), onFocus = fb(2)
			)
			TvCycleRow(
				title = stringResource(R.string.video_codec),
				value = codec.title,
				onNext = {
					codec = Preferences.Codec.values()[(codec.ordinal + 1) % Preferences.Codec.values().size]
					preferences.codec = codec
				},
				focusRequester = fr(3), onFocus = fb(3)
			)
			TvStepperRow(
				title = stringResource(R.string.bitrate),
				value = if(bitrate == null) stringResource(R.string.bitrate_auto, preferences.bitrateAuto)
					else stringResource(R.string.bitrate_kbps, bitrate!!),
				summary = stringResource(R.string.bitrate_summary),
				onStep = { steps ->
					val base = bitrate ?: preferences.bitrateAuto
					bitrate = (base + steps * Preferences.BITRATE_STEP_KBPS)
						.coerceIn(Preferences.BITRATE_MIN_KBPS, Preferences.BITRATE_MAX_KBPS)
					preferences.bitrate = bitrate
				},
				onReset = {
					bitrate = if(bitrate == null) preferences.bitrateAuto else null
					preferences.bitrate = bitrate
				},
				focusRequester = fr(4), onFocus = fb(4)
			)
			TvCycleRow(
				title = stringResource(R.string.display_mode),
				value = displayMode.title,
				onNext = {
					val values = Preferences.DisplayMode.values()
					displayMode = values[(displayMode.ordinal + 1) % values.size]
					preferences.displayMode = displayMode
				},
				focusRequester = fr(5), onFocus = fb(5)
			)
		}

		SectionHeader(stringResource(R.string.audio))
		SettingsCard {
			TvCycleRow(
				title = stringResource(R.string.volume),
				value = stringResource(R.string.volume_percent, volume),
				onNext = {
					volume = if(volume >= 100) 0 else volume + 25
					preferences.audioVolume = volume
				},
				focusRequester = fr(6), onFocus = fb(6)
			)
			TvCycleRow(
				title = stringResource(R.string.output_device),
				value = audioDevices[deviceIndex].first,
				onNext = {
					deviceIndex = (deviceIndex + 1) % audioDevices.size
					preferences.audioDeviceId = audioDevices[deviceIndex].second
					preferences.audioDeviceName = audioDevices[deviceIndex].first
				},
				focusRequester = fr(7), onFocus = fb(7)
			)
			TvCycleRow(
				title = stringResource(R.string.audio_buffer),
				value = if(bufferMs == 0) stringResource(R.string.auto) else stringResource(R.string.buffer_ms, bufferMs),
				onNext = {
					bufferMs = if(bufferMs >= 100) 0 else bufferMs + 10
					preferences.audioBufferMs = bufferMs
				},
				focusRequester = fr(8), onFocus = fb(8)
			)
		}

		SectionHeader(stringResource(R.string.controller))
		SettingsCard {
			TvSwitchRow(
				stringResource(R.string.swap_cross_moon),
				stringResource(R.string.swap_cross_moon_summary),
				swapCrossMoon,
				{ swapCrossMoon = it; preferences.swapCrossMoon = it },
				focusRequester = fr(9), onFocus = fb(9)
			)
			TvSwitchRow(
				stringResource(R.string.dpad_as_controller),
				stringResource(R.string.dpad_as_controller_summary),
				dpadAsController,
				{ dpadAsController = it; preferences.dpadAsController = it },
				focusRequester = fr(10), onFocus = fb(10)
			)
			TvSwitchRow(
				stringResource(R.string.rumble),
				stringResource(R.string.rumble_summary),
				rumble,
				{ rumble = it; preferences.rumbleEnabled = it },
				focusRequester = fr(11), onFocus = fb(11)
			)
			TvSwitchRow(
				stringResource(R.string.motion_controls),
				stringResource(R.string.motion_controls_summary),
				motion,
				{ motion = it; preferences.motionEnabled = it },
				focusRequester = fr(12), onFocus = fb(12)
			)
			TvSwitchRow(
				stringResource(R.string.button_haptics),
				stringResource(R.string.button_haptics_summary),
				haptics,
				{ haptics = it; preferences.buttonHapticEnabled = it },
				focusRequester = fr(13), onFocus = fb(13)
			)
			TvCycleRow(
				title = stringResource(R.string.hud_shortcut),
				value = menuShortcut.title,
				onNext = {
					val values = Preferences.MenuShortcut.values()
					menuShortcut = values[(menuShortcut.ordinal + 1) % values.size]
					preferences.menuShortcut = menuShortcut
				},
				focusRequester = fr(14), onFocus = fb(14)
			)
		}

		SectionHeader(stringResource(R.string.general))
		SettingsCard {
			TvSwitchRow(
				stringResource(R.string.console_discovery),
				stringResource(R.string.console_discovery_summary),
				discovery,
				{ discovery = it; preferences.discoveryEnabled = it },
				focusRequester = fr(15), onFocus = fb(15)
			)
			TvSwitchRow(
				stringResource(R.string.auto_connect_startup),
				stringResource(R.string.auto_connect_startup_summary),
				autoConnect,
				{ autoConnect = it; preferences.autoConnectEnabled = it },
				focusRequester = fr(16), onFocus = fb(16)
			)
			TvCycleRow(
				title = stringResource(R.string.sleep_console_on_exit),
				value = sleepConsoleOnExit.title,
				onNext = {
					val values = Preferences.SleepConsoleOnExit.values()
					sleepConsoleOnExit = values[(sleepConsoleOnExit.ordinal + 1) % values.size]
					preferences.sleepConsoleOnExit = sleepConsoleOnExit
				},
				focusRequester = fr(17), onFocus = fb(17)
			)
			TvCycleRow(
				title = stringResource(R.string.on_close),
				value = disconnectAction.title,
				onNext = {
					val values = Preferences.DisconnectAction.values()
					disconnectAction = values[(disconnectAction.ordinal + 1) % values.size]
					preferences.disconnectAction = disconnectAction
				},
				focusRequester = fr(18), onFocus = fb(18)
			)
			TvCycleRow(
				title = stringResource(R.string.auto_connect_console),
				value = autoConnectOptions.getOrNull(autoConnectIndex)?.let {
					it.serverNickname?.takeIf { n -> n.isNotBlank() }
						?: stringResource(if(it.target.isPS5) R.string.playstation5 else R.string.playstation4)
				} ?: stringResource(R.string.none),
				onNext = {
					autoConnectIndex = (autoConnectIndex + 1) % (autoConnectOptions.size.coerceAtLeast(1))
					preferences.autoConnectMac = autoConnectOptions.getOrNull(autoConnectIndex)?.serverMac?.toString() ?: ""
				},
				focusRequester = fr(19), onFocus = fb(19)
			)
			TvSwitchRow(
				stringResource(R.string.verbose_logs),
				stringResource(R.string.verbose_logs_summary),
				logVerbose,
				{ logVerbose = it; preferences.logVerbose = it },
				focusRequester = fr(20), onFocus = fb(20)
			)
			TvActionRow(
				stringResource(R.string.session_logs),
				stringResource(R.string.session_logs_summary),
				onShowLogs,
				focusRequester = fr(21), onFocus = fb(21)
			)
			TvActionRow(
				stringResource(R.string.registered_consoles),
				if(registeredHostCount == 0) stringResource(R.string.registered_consoles_none)
					else stringResource(R.string.registered_consoles_count, registeredHostCount),
				onManageHosts,
				focusRequester = fr(22), onFocus = fb(22)
			)
		}

		SectionHeader(stringResource(R.string.playstation_network))
		SettingsCard {
			if(psnLoggedIn)
			{
				TvInfoRow(stringResource(R.string.account), stringResource(R.string.signed_in))
				TvActionRow(
					stringResource(R.string.sign_out),
					stringResource(R.string.sign_out_summary),
					{
						com.chiaking.tv.common.psn.PsnTokenStore.get(ctx).logout()
						psnLoggedIn = false
					},
					focusRequester = fr(23), onFocus = fb(23)
				)
			}
			else
				TvActionRow(
					stringResource(R.string.sign_in_psn),
					stringResource(R.string.sign_in_psn_summary),
					{ showPsnLogin = true },
					focusRequester = fr(23), onFocus = fb(23)
				)
		}

		SectionHeader(stringResource(R.string.backup))
		SettingsCard {
			TvActionRow(
				stringResource(R.string.export_settings),
				stringResource(R.string.export_settings_summary),
				onExportSettings,
				focusRequester = fr(24), onFocus = fb(24)
			)
			TvActionRow(
				stringResource(R.string.import_settings),
				stringResource(R.string.import_settings_summary),
				onImportSettings,
				focusRequester = fr(25), onFocus = fb(25)
			)
		}

		SectionHeader(stringResource(R.string.about))
		SettingsCard {
			TvInfoRow(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
			TvInfoRow(stringResource(R.string.chiaki_ng_core), UPSTREAM_CHIAKI_VERSION)
		}

		Spacer(Modifier.height(32.dp))
	}

	if(showPsnLogin)
		PsnLoginDialog(
			onDismiss = { showPsnLogin = false },
			onLoggedIn = { psnLoggedIn = true; showPsnLogin = false }
		)
}
