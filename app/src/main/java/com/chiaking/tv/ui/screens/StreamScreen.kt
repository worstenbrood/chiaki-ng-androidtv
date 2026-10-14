// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.ui.FocusMemory
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectInfo
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.session.*
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay

private data class HudStats(val fps: Double, val kbps: Double, val framesLost: Long, val packetLossPct: Double, val rttMs: Long)

@Composable
fun StreamScreen(
	session: StreamSession,
	preferences: Preferences,
	onQuit: () -> Unit,
	onSleep: () -> Unit
)
{
	val state by session.state.collectAsState()
	val hudVisible by session.hudVisible.collectAsState()
	val disconnectDialogVisible by session.disconnectDialogVisible.collectAsState()
	var hudStats by remember { mutableStateOf<HudStats?>(null) }
	var pinDialog by remember { mutableStateOf<StreamStateLoginPinRequest?>(null) }
	var quitDialog by remember { mutableStateOf<StreamStateQuit?>(null) }
	var createError by remember { mutableStateOf<StreamStateCreateError?>(null) }
	val hudScroll = rememberScrollState()
	val hudFocus = remember { FocusRequester() }
	var hudFocusIdx by remember { mutableStateOf(0) }

	val closeAction: () -> Unit = {
		when(preferences.disconnectAction)
		{
			Preferences.DisconnectAction.SLEEP -> { session.gotoBed(); onSleep() }
			Preferences.DisconnectAction.NOTHING -> onQuit()
			Preferences.DisconnectAction.ASK -> session.requestDisconnectDialog()
		}
	}

	var volume by remember { mutableStateOf(preferences.audioVolume) }
	val ctx = LocalContext.current
	val audioDevices = remember { AudioDevices.outputDevices(ctx) }
	var deviceIndex by remember { mutableStateOf(
		(audioDevices.indexOfFirst { it.first == preferences.audioDeviceName }
			.takeIf { it >= 0 }
			?: audioDevices.indexOfFirst { it.second == preferences.audioDeviceId })
			.coerceAtLeast(0)) }
	val deviceName = audioDevices[deviceIndex].first

	LaunchedEffect(hudVisible)
	{
		if(!hudVisible)
		{
			hudStats = null
			return@LaunchedEffect
		}
		var lastStats: LongArray? = null
		var lastTime = 0L
		while(hudVisible)
		{
			val now = System.nanoTime()
			val stats = session.getStats()
			if(stats != null && lastStats != null && lastTime != 0L)
			{
				val dt = (now - lastTime) / 1_000_000_000.0
				if(dt > 0.0)
					hudStats = HudStats(
						(stats[0] - lastStats[0]) / dt,
						(stats[1] - lastStats[1]) * 8.0 / dt / 1000.0,
						stats[2],
						stats[3] / 10000.0,
						stats[4] / 1000
					)
			}
			lastStats = stats
			lastTime = now
			delay(1000)
		}
	}

	LaunchedEffect(hudVisible)
	{
		if(hudVisible)
		{
			delay(400)
			hudFocusIdx = FocusMemory.get("hud").coerceIn(0, 6)
			delay(50)
			try { hudFocus.requestFocus() } catch(e: Exception)
			{ android.util.Log.e("StreamScreen", "HUD focus request failed", e) }
		}
	}

	BackHandler(onBack = onQuit)

	LaunchedEffect(state) {
		when(state)
		{
			is StreamStateLoginPinRequest -> pinDialog = state as StreamStateLoginPinRequest
			is StreamStateQuit -> {
				val quit = state as StreamStateQuit
				if(session.disconnecting)
					{}
				else if(quit.reason.isError)
					quitDialog = quit
				else
					onQuit()
			}
			is StreamStateCreateError -> createError = state as StreamStateCreateError
			else -> {}
		}
	}

	var displayMode by remember { mutableStateOf(preferences.displayMode) }

	Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
		BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
			val vprofile = session.connectInfo.videoProfile
			val vAspect = if(vprofile.height > 0) vprofile.width.toFloat() / vprofile.height else 16f / 9f
			val sAspect = maxWidth / maxHeight
			// The video buffer always stretches to fill the SurfaceView bounds, so the
			// display mode is implemented purely by sizing the view (like VideoView does).
			val (vw, vh) = when(displayMode)
			{
				Preferences.DisplayMode.FIT ->
					if(vAspect > sAspect) maxWidth to (maxWidth / vAspect)
					else (maxHeight * vAspect) to maxHeight
				Preferences.DisplayMode.ZOOM ->
					if(vAspect > sAspect) (maxHeight * vAspect) to maxHeight
					else maxWidth to (maxWidth / vAspect)
				Preferences.DisplayMode.STRETCH -> maxWidth to maxHeight
			}
			AndroidView(
				factory = { context ->
					SurfaceView(context).also {
						session.attachToSurfaceView(it)
					}
				},
				modifier = Modifier
					.size(vw, vh)
					.align(Alignment.Center)
			)
		}

		when(state)
		{
			StreamStateConnecting, StreamStateIdle -> HudSpinner(stringResource(R.string.connecting))
			is StreamStateRemoteProgress -> HudSpinner(stringResource(
				if((state as StreamStateRemoteProgress).finished) R.string.remote_data_connected
				else R.string.remote_data_connecting
			))
			StreamStateDisconnecting -> HudSpinner(stringResource(R.string.disconnecting))
			else -> {}
		}

		AnimatedVisibility(
			visible = hudVisible,
			enter = slideInVertically(initialOffsetY = { it }),
			exit = slideOutVertically(targetOffsetY = { it }),
			modifier = Modifier.align(Alignment.BottomCenter)
		) {
			val vp = session.connectInfo.videoProfile
			val codecLabel = when(vp.codec)
			{
				Codec.CODEC_H264 -> "H.264"
				Codec.CODEC_H265 -> "H.265"
				Codec.CODEC_H265_HDR -> "H.265 HDR"
			}
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.background(Color(0xE60B0E17))
					.padding(horizontal = 16.dp, vertical = 8.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Box(Modifier.fillMaxWidth()) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.horizontalScroll(hudScroll)
				) {
					HudItem(
						label = stringResource(if(session.connectInfo.ps5) R.string.ps5 else R.string.ps4),
						value = session.connectInfo.host.ifEmpty { stringResource(R.string.remote_host_label) },
						sub = stringResource(
							when(state)
							{
								is StreamStateConnected -> R.string.connected
								is StreamStateDisconnecting -> R.string.disconnecting
								else -> R.string.connecting
							}
						),
						focusReq = if(hudFocusIdx == 0) hudFocus else null,
						selected = hudFocusIdx == 0,
						onSelect = { if(it) { hudFocusIdx = 0; FocusMemory.set("hud", 0) } }
					)
					HudDivider()
					HudItem(
						label = stringResource(R.string.video),
						value = stringResource(R.string.video_value, vp.width, vp.height, vp.maxFPS),
						sub = stringResource(R.string.video_sub, codecLabel, vp.bitrate / 1000),
						focusReq = if(hudFocusIdx == 1) hudFocus else null,
						selected = hudFocusIdx == 1,
						onSelect = { if(it) { hudFocusIdx = 1; FocusMemory.set("hud", 1) } }
					)
					HudDivider()
					HudItem(
						label = stringResource(R.string.display),
						value = displayMode.title,
						sub = stringResource(R.string.ok_to_cycle),
						onClick = {
							val values = Preferences.DisplayMode.values()
							displayMode = values[(displayMode.ordinal + 1) % values.size]
							preferences.displayMode = displayMode
						},
						focusReq = if(hudFocusIdx == 2) hudFocus else null,
						selected = hudFocusIdx == 2,
						onSelect = { if(it) { hudFocusIdx = 2; FocusMemory.set("hud", 2) } }
					)
					HudDivider()
					HudItem(
						label = stringResource(R.string.stream),
						value = hudStats?.let { stringResource(R.string.fps_value, it.fps) } ?: "—",
						sub = hudStats?.let { stringResource(R.string.mbps_value, it.kbps / 1000.0) } ?: "",
						focusReq = if(hudFocusIdx == 3) hudFocus else null,
						selected = hudFocusIdx == 3,
						onSelect = { if(it) { hudFocusIdx = 3; FocusMemory.set("hud", 3) } }
					)
					HudDivider()
					HudItem(
						label = stringResource(R.string.network),
						value = hudStats?.let { stringResource(R.string.loss_value, it.packetLossPct) } ?: "—",
						sub = hudStats?.let { stringResource(R.string.ping_sub, it.rttMs, it.framesLost) } ?: "",
						focusReq = if(hudFocusIdx == 4) hudFocus else null,
						selected = hudFocusIdx == 4,
						onSelect = { if(it) { hudFocusIdx = 4; FocusMemory.set("hud", 4) } }
					)
					HudDivider()
					HudItem(
						label = stringResource(R.string.audio),
						value = stringResource(R.string.volume_percent, volume),
						sub = deviceName,
						onClick = {
							deviceIndex = (deviceIndex + 1) % audioDevices.size
							val (name, id) = audioDevices[deviceIndex]
							preferences.audioDeviceName = name
							preferences.audioDeviceId = id
							session.setAudioDevice(id)
						},
						onVertical = { dir ->
							volume = (volume + dir * 5).coerceIn(0, 100)
							preferences.audioVolume = volume
							session.setVolumePercent(volume)
						},
						focusReq = if(hudFocusIdx == 5) hudFocus else null,
						selected = hudFocusIdx == 5,
						onSelect = { if(it) { hudFocusIdx = 5; FocusMemory.set("hud", 5) } }
					)
					HudDivider()
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.widthIn(min = 140.dp)
							.clip(RoundedCornerShape(10.dp))
							.background(if(hudFocusIdx == 6) Color.White.copy(alpha = 0.16f) else Color.Transparent)
							.then(if(hudFocusIdx == 6) Modifier.focusRequester(hudFocus) else Modifier)
							.onFocusEvent { if(it.isFocused) { hudFocusIdx = 6; FocusMemory.set("hud", 6) } }
							.onKeyEvent { ev ->
								if(ev.key == Key.ButtonA && ev.type == KeyEventType.KeyUp)
								{
									closeAction()
									true
								}
								else
									false
							}
							.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { closeAction() }
							.padding(horizontal = 10.dp, vertical = 4.dp)
					) {
						Text(
							stringResource(R.string.hud_close),
							fontSize = 11.sp,
							color = PsBlueLight,
							letterSpacing = 2.sp,
							fontWeight = FontWeight.SemiBold
						)
						Spacer(Modifier.height(2.dp))
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier.height(40.dp)
						) {
							Icon(
								Icons.Default.Close,
								contentDescription = "Disconnect",
								tint = if(hudFocusIdx == 6) Color.White else OnSurfaceMuted,
								modifier = Modifier.size(22.dp)
							)
						}
					}
				}
				if(hudScroll.value > 0)
					Icon(
						Icons.Default.KeyboardArrowLeft,
						contentDescription = null,
						tint = Color.White,
						modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
					)
				if(hudScroll.value < hudScroll.maxValue)
					Icon(
						Icons.Default.KeyboardArrowRight,
						contentDescription = null,
						tint = Color.White,
						modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)
					)
				}
			}
		}
	}

	if(pinDialog != null)
	{
		var pin by remember(pinDialog!!.requestId) { mutableStateOf(preferences.loginPin) }
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text(stringResource(if(pinDialog!!.pinIncorrect) R.string.pin_incorrect else R.string.enter_pin)) },
			text = {
				Column {
					Text(stringResource(R.string.pin_instructions))
					Spacer(Modifier.height(12.dp))
					TvTextField(stringResource(R.string.pin), pin) {
						if(it.all { c -> c.isDigit() } && it.length <= 8) pin = it
					}
				}
			},
			confirmButton = {
				TextButton(onClick = {
					preferences.loginPin = pin
					session.setLoginPin(pin)
					pinDialog = null
				}) { Text(stringResource(R.string.connect), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text(stringResource(R.string.quit), color = OnSurfaceMuted) }
			}
		)
	}

	if(quitDialog != null)
	{
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text(stringResource(R.string.session_ended)) },
			text = {
				Text(
					quitDialog!!.reason.toString() +
						(quitDialog!!.reasonString?.let { "\n$it" } ?: ""),
					color = OnSurfaceMuted
				)
			},
			confirmButton = {
				TextButton(onClick = {
					quitDialog = null
					session.shutdown(StreamStateIdle)
					session.resumeDelayed(1500)
				}) { Text(stringResource(R.string.reconnect), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text(stringResource(R.string.quit), color = OnSurfaceMuted) }
			}
		)
	}

	if(disconnectDialogVisible)
	{
		AlertDialog(
			onDismissRequest = { session.dismissDisconnectDialog() },
			title = { Text(stringResource(R.string.disconnect_title)) },
			text = { Text(stringResource(R.string.disconnect_sleep_question)) },
			confirmButton = {
				TextButton(onClick = {
					session.dismissDisconnectDialog()
					session.gotoBed()
					onSleep()
				}) { Text(stringResource(R.string.sleep), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = {
					session.dismissDisconnectDialog()
					onQuit()
				}) { Text(stringResource(R.string.disconnect), color = OnSurfaceMuted) }
			}
		)
	}

	if(createError != null)
	{
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text(stringResource(R.string.failed_to_start_session)) },
			text = { Text(createError!!.error.errorCode.toString(), color = OnSurfaceMuted) },
			confirmButton = { TextButton(onClick = onQuit) { Text(stringResource(R.string.quit), color = OnSurfaceMuted) } }
		)
	}
}

@Composable
private fun HudSpinner(text: String)
{
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			CircularProgressIndicator(color = PsBlueLight)
			Spacer(Modifier.height(16.dp))
			Text(text, color = Color.White, fontSize = 20.sp)
		}
	}
}

@Composable
private fun HudItem(
	label: String,
	value: String,
	sub: String,
	onClick: (() -> Unit)? = null,
	onVertical: ((Int) -> Unit)? = null,
	focusReq: FocusRequester? = null,
	selected: Boolean = false,
	onSelect: (Boolean) -> Unit = {}
)
{
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.widthIn(min = 140.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if(selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
			.then(if(focusReq != null) Modifier.focusRequester(focusReq) else Modifier)
			.onFocusEvent { onSelect(it.isFocused) }
			.onKeyEvent { ev ->
				if(ev.key == Key.ButtonA && ev.type == KeyEventType.KeyUp)
				{
					onClick?.invoke()
					true
				}
				else if(ev.type != KeyEventType.KeyDown || onVertical == null)
					false
				else
					when(ev.key)
					{
						Key.DirectionUp -> { onVertical(1); true }
						Key.DirectionDown -> { onVertical(-1); true }
						else -> false
					}
			}
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick?.invoke() }
			.padding(horizontal = 10.dp, vertical = 4.dp)
	) {
		Text(
			label.uppercase(),
			fontSize = 11.sp,
			color = PsBlueLight,
			letterSpacing = 2.sp,
			fontWeight = FontWeight.SemiBold
		)
		Spacer(Modifier.height(2.dp))
		Text(value, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold)
		if(sub.isNotEmpty())
			Text(
				sub,
				fontSize = 12.sp,
				color = OnSurfaceMuted,
				maxLines = 1,
				overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
				modifier = Modifier.widthIn(max = 160.dp)
			)
	}
}

@Composable
private fun HudDivider()
{
	Box(
		Modifier
			.width(1.dp)
			.height(44.dp)
			.background(Color.White.copy(alpha = 0.15f))
	)
}
