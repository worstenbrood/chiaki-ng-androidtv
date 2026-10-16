// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.settings

import com.chiaking.tv.ui.screens.PsnLoginDialog

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.BuildConfig
import com.chiaking.tv.R
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.common.db.RegisteredHost
import com.chiaking.tv.common.psn.PsnTokenStore
import com.chiaking.tv.session.MicCapture
import com.chiaking.tv.ui.focus.FocusMemory
import com.chiaking.tv.ui.components.PinTextField
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.components.ScreenHeader
import com.chiaking.tv.ui.components.settings.SettingsCard
import com.chiaking.tv.ui.components.settings.TvActionRow
import com.chiaking.tv.ui.components.settings.TvCycleRow
import com.chiaking.tv.ui.components.settings.TvInfoRow
import com.chiaking.tv.ui.components.settings.TvOrderEntry
import com.chiaking.tv.ui.components.settings.TvOrderToggleList
import com.chiaking.tv.ui.components.settings.TvStepperRow
import com.chiaking.tv.ui.components.settings.TvSwitchRow
import com.chiaking.tv.ui.focus.rememberFocusRestore
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.SurfaceFrosted
import com.chiaking.tv.ui.focus.tryRequestFocus

// Keep in sync with CHIAKI_VERSION in external/chiaki-ng/CMakeLists.txt (see docs/UPSTREAM_SYNC.md)
const val UPSTREAM_CHIAKI_VERSION = "1.10.0"

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
	var hudItems by remember { mutableStateOf(preferences.hudItems) }
	var displayMode by remember { mutableStateOf(preferences.displayMode) }
	var disconnectAction by remember { mutableStateOf(preferences.disconnectAction) }
	var idrOnFec by remember { mutableStateOf(preferences.idrOnFecFailure) }
	var autoConnectIndex by remember { mutableStateOf(
		(registeredHosts.indexOfFirst { it.serverMac.toString() == preferences.autoConnectMac } + 1).coerceAtLeast(0)) }

	val autoConnectOptions = remember(registeredHosts) { listOf<RegisteredHost?>(null) + registeredHosts }

	val ctx = LocalContext.current
	var psnLoggedIn by remember { mutableStateOf(PsnTokenStore.get(ctx).isLoggedIn) }
	var showPsnLogin by remember { mutableStateOf(false) }
	var showPinEdit by remember { mutableStateOf(false) }
	var pinDraft by remember { mutableStateOf(preferences.loginPin) }
	val audioDevices = remember { AudioDevices.outputDevices(ctx) }
	var deviceIndex by remember { mutableStateOf(
		AudioDevices.indexOfSavedOutput(audioDevices, preferences.audioDeviceName, preferences.audioDeviceId)) }

	var micEnabled by remember { mutableStateOf(preferences.micEnabled) }
	var micStartUnmuted by remember { mutableStateOf(preferences.micStartUnmuted) }
	var micGain by remember { mutableStateOf(preferences.micGain) }
	var micAec by remember { mutableStateOf(preferences.micAec) }
	var micNs by remember { mutableStateOf(preferences.micNs) }
	var micAgc by remember { mutableStateOf(preferences.micAgc) }
	var micComplexity by remember { mutableStateOf(preferences.micComplexity) }
	var micBandwidth by remember { mutableStateOf(preferences.micBandwidth) }
	val micDevices = remember { AudioDevices.inputDevices(ctx) }
	var micDeviceIndex by remember { mutableStateOf(
		micDevices.indexOfFirst { it.first == preferences.micDeviceName }.coerceAtLeast(0)) }
	val micPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()) { granted ->
		micEnabled = granted
		preferences.micEnabled = granted
	}

	var category by remember { mutableStateOf(
		SettingsCategory.entries.getOrElse(FocusMemory.get("settings_cat")) { SettingsCategory.STREAM }) }
	val restoreRow = FocusMemory.get("settings_row_${category.ordinal}")

	val navFocus = remember { FocusRequester() }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	var rightFocused by remember { mutableStateOf(false) }
	BackHandler(enabled = rightFocused)
	{
		navFocus.tryRequestFocus()
	}
	rememberFocusRestore(restoreRow, navFocus, rowFocus)
	fun fr(i: Int) = if(i == restoreRow.coerceAtLeast(1)) rowFocus else null
	fun fb(i: Int): (Boolean) -> Unit = { if(it) FocusMemory.set("settings_row_${category.ordinal}", i) }

	Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
		ScreenHeader(
			stringResource(R.string.settings), onBack,
			modifier = Modifier.padding(horizontal = 40.dp),
			backFocusRequester = if(restoreRow == 0) backFocus else null,
			onBackFocus = { if(it) FocusMemory.set("settings_row_${category.ordinal}", 0) }
		)

		Spacer(Modifier.height(20.dp))

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.padding(horizontal = 40.dp)
		) {
			Column(
				modifier = Modifier
					.width(280.dp)
					.onKeyEvent { ev ->
						if(ev.key == Key.DirectionRight && ev.type == KeyEventType.KeyDown)
							true
						else
							false
					}
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				SettingsCategory.entries.forEachIndexed { index, cat ->
					SettingsNavItem(
						label = stringResource(cat.titleRes),
						icon = cat.icon,
						selected = cat == category,
						focusRequester = if(index == category.ordinal) navFocus else null,
						onFocus = {
							if(it)
							{
								FocusMemory.set("settings_cat", index)
								category = cat
							}
						},
						onClick = {
							category = cat
							rowFocus.tryRequestFocus()
						}
					)
				}
			}

			Spacer(Modifier.width(28.dp))

			Column(
				modifier = Modifier
					.weight(1f)
					.onFocusEvent { rightFocused = it.hasFocus }
					.onKeyEvent { ev ->
						if(ev.key == Key.DirectionLeft && ev.type == KeyEventType.KeyDown)
							true
						else
							false
					}
					.verticalScroll(rememberScrollState())
			) {
				when(category)
				{
					SettingsCategory.STREAM ->
					{
						SettingsCard(SurfaceFrosted) {
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
								value = stringResource(displayMode.titleRes),
								onNext = {
									val values = Preferences.DisplayMode.values()
									displayMode = values[(displayMode.ordinal + 1) % values.size]
									preferences.displayMode = displayMode
								},
								focusRequester = fr(5), onFocus = fb(5)
							)
							TvSwitchRow(
								stringResource(R.string.idr_on_fec_failure),
								stringResource(R.string.idr_on_fec_failure_summary),
								idrOnFec,
								{ idrOnFec = it; preferences.idrOnFecFailure = it },
								focusRequester = fr(6), onFocus = fb(6)
							)
						}
					}

					SettingsCategory.AUDIO ->
					{
						SettingsCard(SurfaceFrosted) {
							TvCycleRow(
								title = stringResource(R.string.volume),
								value = stringResource(R.string.volume_percent, volume),
								onNext = {
									volume = if(volume >= 100) 0 else volume + 25
									preferences.audioVolume = volume
								},
								focusRequester = fr(1), onFocus = fb(1)
							)
							TvCycleRow(
								title = stringResource(R.string.output_device),
								value = audioDevices[deviceIndex].first,
								onNext = {
									deviceIndex = (deviceIndex + 1) % audioDevices.size
									preferences.audioDeviceId = audioDevices[deviceIndex].second
									preferences.audioDeviceName = audioDevices[deviceIndex].first
								},
								focusRequester = fr(2), onFocus = fb(2)
							)
							TvCycleRow(
								title = stringResource(R.string.audio_buffer),
								value = if(bufferMs == 0) stringResource(R.string.auto) else stringResource(R.string.buffer_ms, bufferMs),
								onNext = {
									bufferMs = if(bufferMs >= 100) 0 else bufferMs + 10
									preferences.audioBufferMs = bufferMs
								},
								focusRequester = fr(3), onFocus = fb(3)
							)
						}
					}

					SettingsCategory.MIC ->
					{
						SettingsCard(SurfaceFrosted) {
							TvSwitchRow(
								stringResource(R.string.mic_enabled),
								stringResource(R.string.mic_enabled_summary),
								micEnabled,
								{ on ->
									if(on)
									{
										if(MicCapture.hasPermission(ctx))
										{
											micEnabled = true
											preferences.micEnabled = true
										}
										else
											micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
									}
									else
									{
										micEnabled = false
										preferences.micEnabled = false
									}
								},
								focusRequester = fr(1), onFocus = fb(1)
							)
							TvSwitchRow(
								stringResource(R.string.mic_start_unmuted),
								stringResource(R.string.mic_start_unmuted_summary),
								micStartUnmuted,
								{ micStartUnmuted = it; preferences.micStartUnmuted = it },
								focusRequester = fr(2), onFocus = fb(2)
							)
							TvCycleRow(
								title = stringResource(R.string.mic_device),
								value = micDevices[micDeviceIndex].first,
								onNext = {
									micDeviceIndex = (micDeviceIndex + 1) % micDevices.size
									preferences.micDeviceName = micDevices[micDeviceIndex].first
								},
								focusRequester = fr(3), onFocus = fb(3)
							)
							TvCycleRow(
								title = stringResource(R.string.mic_gain),
								value = stringResource(R.string.mic_gain_percent, micGain),
								onNext = {
									micGain = if(micGain >= 200) 50 else micGain + 25
									preferences.micGain = micGain
								},
								focusRequester = fr(4), onFocus = fb(4)
							)
							TvSwitchRow(
								stringResource(R.string.mic_aec),
								stringResource(R.string.mic_aec_summary),
								micAec,
								{ micAec = it; preferences.micAec = it },
								focusRequester = fr(5), onFocus = fb(5)
							)
							TvSwitchRow(
								stringResource(R.string.mic_ns),
								stringResource(R.string.mic_ns_summary),
								micNs,
								{ micNs = it; preferences.micNs = it },
								focusRequester = fr(6), onFocus = fb(6)
							)
							TvSwitchRow(
								stringResource(R.string.mic_agc),
								stringResource(R.string.mic_agc_summary),
								micAgc,
								{ micAgc = it; preferences.micAgc = it },
								focusRequester = fr(7), onFocus = fb(7)
							)
							TvCycleRow(
								title = stringResource(R.string.mic_complexity),
								value = micComplexity.title,
								onNext = {
									val values = Preferences.MicComplexity.values()
									micComplexity = values[(micComplexity.ordinal + 1) % values.size]
									preferences.micComplexity = micComplexity
								},
								focusRequester = fr(8), onFocus = fb(8)
							)
							TvCycleRow(
								title = stringResource(R.string.mic_bandwidth),
								value = micBandwidth.title,
								onNext = {
									val values = Preferences.MicBandwidth.values()
									micBandwidth = values[(micBandwidth.ordinal + 1) % values.size]
									preferences.micBandwidth = micBandwidth
								},
								focusRequester = fr(9), onFocus = fb(9)
							)
						}
					}

					SettingsCategory.CONTROLLER ->
					{
						SettingsCard(SurfaceFrosted) {
							TvSwitchRow(
								stringResource(R.string.swap_cross_moon),
								stringResource(R.string.swap_cross_moon_summary),
								swapCrossMoon,
								{ swapCrossMoon = it; preferences.swapCrossMoon = it },
								focusRequester = fr(1), onFocus = fb(1)
							)
							TvSwitchRow(
								stringResource(R.string.dpad_as_controller),
								stringResource(R.string.dpad_as_controller_summary),
								dpadAsController,
								{ dpadAsController = it; preferences.dpadAsController = it },
								focusRequester = fr(2), onFocus = fb(2)
							)
							TvSwitchRow(
								stringResource(R.string.rumble),
								stringResource(R.string.rumble_summary),
								rumble,
								{ rumble = it; preferences.rumbleEnabled = it },
								focusRequester = fr(3), onFocus = fb(3)
							)
							TvSwitchRow(
								stringResource(R.string.motion_controls),
								stringResource(R.string.motion_controls_summary),
								motion,
								{ motion = it; preferences.motionEnabled = it },
								focusRequester = fr(4), onFocus = fb(4)
							)
							TvSwitchRow(
								stringResource(R.string.button_haptics),
								stringResource(R.string.button_haptics_summary),
								haptics,
								{ haptics = it; preferences.buttonHapticEnabled = it },
								focusRequester = fr(5), onFocus = fb(5)
							)
						}
					}

					SettingsCategory.HUD ->
					{
						SettingsCard(SurfaceFrosted) {
							TvCycleRow(
								title = stringResource(R.string.hud_shortcut),
								value = menuShortcut.title,
								onNext = {
									val values = Preferences.MenuShortcut.values()
									menuShortcut = values[(menuShortcut.ordinal + 1) % values.size]
									preferences.menuShortcut = menuShortcut
								},
								focusRequester = fr(1), onFocus = fb(1)
							)
							TvOrderToggleList(
								entries = hudItems.map { TvOrderEntry(it.kind, stringResource(it.kind.titleRes), it.enabled, it.kind != Preferences.HudItemKind.CLOSE) },
								summary = stringResource(R.string.hud_items_summary),
								onToggle = { i ->
									if(hudItems[i].kind != Preferences.HudItemKind.CLOSE)
									{
										hudItems = hudItems.mapIndexed { j, e -> if(j == i) e.copy(enabled = !e.enabled) else e }
										preferences.hudItems = hudItems
									}
								},
								onMove = { i, d ->
									val j = i + d
									if(j in hudItems.indices)
									{
										val list = hudItems.toMutableList()
										list.add(j, list.removeAt(i))
										hudItems = list
										preferences.hudItems = list
									}
								},
								focusRequester = fr(2), onFocus = fb(2)
							)
						}
					}

					SettingsCategory.GENERAL ->
					{
						SettingsCard(SurfaceFrosted) {
							TvSwitchRow(
								stringResource(R.string.console_discovery),
								stringResource(R.string.console_discovery_summary),
								discovery,
								{ discovery = it; preferences.discoveryEnabled = it },
								focusRequester = fr(1), onFocus = fb(1)
							)
							TvSwitchRow(
								stringResource(R.string.auto_connect_startup),
								stringResource(R.string.auto_connect_startup_summary),
								autoConnect,
								{ autoConnect = it; preferences.autoConnectEnabled = it },
								focusRequester = fr(2), onFocus = fb(2)
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
								focusRequester = fr(3), onFocus = fb(3)
							)
							TvCycleRow(
								title = stringResource(R.string.on_close),
								value = stringResource(disconnectAction.titleRes),
								onNext = {
									val values = Preferences.DisconnectAction.values()
									disconnectAction = values[(disconnectAction.ordinal + 1) % values.size]
									preferences.disconnectAction = disconnectAction
								},
								focusRequester = fr(4), onFocus = fb(4)
							)
							TvActionRow(
								stringResource(R.string.registered_consoles),
								if(registeredHostCount == 0) stringResource(R.string.registered_consoles_none)
									else stringResource(R.string.registered_consoles_count, registeredHostCount),
								onManageHosts,
								focusRequester = fr(5), onFocus = fb(5)
							)
							TvActionRow(
								stringResource(R.string.login_pin),
								if(preferences.loginPin.isEmpty()) stringResource(R.string.login_pin_not_set)
									else stringResource(R.string.login_pin_summary_default),
								{ pinDraft = preferences.loginPin; showPinEdit = true },
								focusRequester = fr(6), onFocus = fb(6)
							)
							TvSwitchRow(
								stringResource(R.string.verbose_logs),
								stringResource(R.string.verbose_logs_summary),
								logVerbose,
								{ logVerbose = it; preferences.logVerbose = it },
								focusRequester = fr(7), onFocus = fb(7)
							)
							TvActionRow(
								stringResource(R.string.session_logs),
								stringResource(R.string.session_logs_summary),
								onShowLogs,
								focusRequester = fr(8), onFocus = fb(8)
							)
						}
					}

					SettingsCategory.PSN ->
					{
						SettingsCard(SurfaceFrosted) {
							if(psnLoggedIn)
							{
								TvInfoRow(stringResource(R.string.account), stringResource(R.string.signed_in))
								TvActionRow(
									stringResource(R.string.sign_out),
									stringResource(R.string.sign_out_summary),
									{
										PsnTokenStore.get(ctx).logout()
										psnLoggedIn = false
									},
									focusRequester = fr(1), onFocus = fb(1)
								)
							}
							else
								TvActionRow(
									stringResource(R.string.sign_in_psn),
									stringResource(R.string.sign_in_psn_summary),
									{ showPsnLogin = true },
									focusRequester = fr(1), onFocus = fb(1)
								)
						}
					}

					SettingsCategory.BACKUP ->
					{
						SettingsCard(SurfaceFrosted) {
							TvActionRow(
								stringResource(R.string.export_settings),
								stringResource(R.string.export_settings_summary),
								onExportSettings,
								focusRequester = fr(1), onFocus = fb(1)
							)
							TvActionRow(
								stringResource(R.string.import_settings),
								stringResource(R.string.import_settings_summary),
								onImportSettings,
								focusRequester = fr(2), onFocus = fb(2)
							)
						}
					}

					SettingsCategory.ABOUT ->
					{
						SettingsCard(SurfaceFrosted) {
							TvInfoRow(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
							TvInfoRow(stringResource(R.string.chiaki_ng_core), UPSTREAM_CHIAKI_VERSION)
						}
					}
				}
				Spacer(Modifier.height(32.dp))
			}
		}
	}

	if(showPsnLogin)
		PsnLoginDialog(
			onDismiss = { showPsnLogin = false },
			onLoggedIn = { psnLoggedIn = true; showPsnLogin = false }
		)

	if(showPinEdit)
	{
		val save: () -> Unit = {
			preferences.loginPin = pinDraft
			showPinEdit = false
		}
		PsAlertDialog(
			onDismissRequest = { showPinEdit = false },
			title = { Text(stringResource(R.string.login_pin)) },
			text = {
				PinTextField(stringResource(R.string.login_pin), pinDraft,
					onValueChange = { pinDraft = it }, onDone = { save() })
			},
			confirmButton = {
				TextButton(onClick = { save() }) { Text(stringResource(R.string.save), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = { showPinEdit = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceMuted) }
			}
		)
	}
}
