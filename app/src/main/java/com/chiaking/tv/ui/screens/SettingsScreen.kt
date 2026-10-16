// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.BuildConfig
import com.chiaking.tv.R
import com.chiaking.tv.common.AppDatabase
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.common.RegisteredHost
import com.chiaking.tv.ui.*
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay

// Keep in sync with CHIAKI_VERSION in external/chiaki-ng/CMakeLists.txt (see docs/UPSTREAM_SYNC.md)
const val UPSTREAM_CHIAKI_VERSION = "1.10.0"

private const val FOCUS_RESTORE_DELAY_MS = 100L

private enum class SettingsCategory(val titleRes: Int, val icon: ImageVector)
{
	STREAM(R.string.stream, Icons.Default.Stream),
	AUDIO(R.string.audio, Icons.Default.VolumeUp),
	CONTROLLER(R.string.controller, Icons.Default.SportsEsports),
	HUD(R.string.category_hud, Icons.Default.Dashboard),
	GENERAL(R.string.general, Icons.Default.Settings),
	PSN(R.string.playstation_network, Icons.Default.AccountCircle),
	BACKUP(R.string.backup, Icons.Default.SaveAlt),
	ABOUT(R.string.about, Icons.Default.Info)
}

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

	val ctx = androidx.compose.ui.platform.LocalContext.current
	var psnLoggedIn by remember { mutableStateOf(com.chiaking.tv.common.psn.PsnTokenStore.get(ctx).isLoggedIn) }
	var showPsnLogin by remember { mutableStateOf(false) }
	var showPinEdit by remember { mutableStateOf(false) }
	var pinDraft by remember { mutableStateOf(preferences.loginPin) }
	val audioDevices = remember { AudioDevices.outputDevices(ctx) }
	var deviceIndex by remember { mutableStateOf(
		(audioDevices.indexOfFirst { it.first == preferences.audioDeviceName }
			.takeIf { it >= 0 }
			?: audioDevices.indexOfFirst { it.second == preferences.audioDeviceId })
			.coerceAtLeast(0)) }

	var category by remember { mutableStateOf(
		SettingsCategory.entries.getOrElse(FocusMemory.get("settings_cat")) { SettingsCategory.STREAM }) }
	val restoreRow = FocusMemory.get("settings_row_${category.ordinal}")

	val navFocus = remember { FocusRequester() }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	var rightFocused by remember { mutableStateOf(false) }
	BackHandler(enabled = rightFocused)
	{
		try { navFocus.requestFocus() } catch(e: Exception) { }
	}
	LaunchedEffect(Unit) {
		delay(FOCUS_RESTORE_DELAY_MS)
		try
		{
			if(restoreRow == 0) navFocus.requestFocus() else rowFocus.requestFocus()
		}
		catch(e: Exception)
		{
			try { navFocus.requestFocus() } catch(e2: Exception) { }
		}
	}
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
							try { rowFocus.requestFocus() } catch(e: Exception) { }
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
										com.chiaking.tv.common.psn.PsnTokenStore.get(ctx).logout()
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
				TvTextField(stringResource(R.string.login_pin), pinDraft,
					imeAction = androidx.compose.ui.text.input.ImeAction.Done,
					keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() })) {
					if(it.all { c -> c.isDigit() } && it.length <= 8) pinDraft = it
				}
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

@Composable
private fun SettingsNavItem(
	label: String,
	icon: ImageVector,
	selected: Boolean,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit,
	onClick: () -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.clip(SelectionShape)
			.background(
				when {
					focused -> SelectionFocused
					selected -> SelectionIdle
					else -> Color.Transparent
				}
			)
			.onFocusChanged {
				focused = it.isFocused
				onFocus(it.isFocused)
			}
			.clickable { onClick() }
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Icon(
			icon,
			null,
			tint = when {
				focused -> PsCyan
				selected -> PsCyan
				else -> OnSurfaceMuted
			},
			modifier = Modifier.size(20.dp)
		)
		Spacer(Modifier.width(14.dp))
		Text(
			label,
			fontSize = 17.sp,
			fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Medium,
			color = when {
				focused -> OnSurfaceLight
				selected -> OnSurfaceLight
				else -> OnSurfaceMuted
			}
		)
	}
}

@Composable
fun SettingsRoute(
	preferences: Preferences,
	database: AppDatabase,
	onBack: () -> Unit,
	onShowLogs: () -> Unit,
	onManageHosts: () -> Unit,
	onExportSettings: () -> Unit,
	onImportSettings: () -> Unit
)
{
	val registeredHosts = remember { mutableStateOf<List<RegisteredHost>>(emptyList()) }
	LaunchedEffect(Unit) {
		database.registeredHostDao().getAll().collect { registeredHosts.value = it }
	}
	SettingsScreen(
		preferences = preferences,
		registeredHostCount = registeredHosts.value.size,
		registeredHosts = registeredHosts.value,
		onBack = onBack,
		onShowLogs = onShowLogs,
		onManageHosts = onManageHosts,
		onExportSettings = onExportSettings,
		onImportSettings = onImportSettings
	)
}
