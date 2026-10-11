// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
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
	val disconnectFocus = remember { FocusRequester() }

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
			try { disconnectFocus.requestFocus() } catch(e: Exception) {}
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

	Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
		AndroidView(
			factory = { context ->
				SurfaceView(context).also {
					session.attachToSurfaceView(it)
				}
			},
			modifier = Modifier.fillMaxSize()
		)

		when(state)
		{
			StreamStateConnecting, StreamStateIdle -> HudSpinner("Connecting...")
			StreamStateDisconnecting -> HudSpinner("Disconnecting...")
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
					.padding(horizontal = 40.dp, vertical = 14.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally)
				) {
					HudItem(
						label = if(session.connectInfo.ps5) "PS5" else "PS4",
						value = session.connectInfo.host,
						sub = when(state)
						{
							is StreamStateConnected -> "Connected"
							is StreamStateDisconnecting -> "Disconnecting"
							else -> "Connecting"
						}
					)
					HudDivider()
					HudItem(
						label = "Video",
						value = "${vp.width}x${vp.height} @ ${vp.maxFPS} fps",
						sub = "$codecLabel • ${vp.bitrate / 1000} Mbps target"
					)
					HudDivider()
					HudItem(
						label = "Stream",
						value = hudStats?.let { String.format("%.0f fps", it.fps) } ?: "—",
						sub = hudStats?.let { String.format("%.1f Mbps", it.kbps / 1000.0) } ?: ""
					)
					HudDivider()
					HudItem(
						label = "Network",
						value = hudStats?.let { String.format("%.1f%% loss", it.packetLossPct) } ?: "—",
						sub = hudStats?.let { String.format("ping %d ms • %d lost", it.rttMs, it.framesLost) } ?: ""
					)
					HudDivider()
					HudItem(
						label = "Audio",
						value = "$volume%",
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
						}
					)
					HudDivider()
					var disconnectFocused by remember { mutableStateOf(false) }
					Box(
						contentAlignment = Alignment.Center,
						modifier = Modifier
							.focusRequester(disconnectFocus)
							.focusable()
							.onFocusEvent { disconnectFocused = it.isFocused }
							.onKeyEvent { ev ->
								if(ev.key == Key.ButtonA && ev.type == KeyEventType.KeyUp)
								{
									session.requestDisconnectDialog()
									true
								}
								else
									false
							}
							.clickable { session.requestDisconnectDialog() }
							.padding(8.dp)
						.background(
							if(disconnectFocused) Color.White.copy(alpha = 0.16f) else Color.Transparent,
							RoundedCornerShape(10.dp)
						)
					) {
						Icon(
							Icons.Default.Close,
							contentDescription = "Disconnect",
							tint = if(disconnectFocused) Color.White else OnSurfaceMuted,
							modifier = Modifier.size(22.dp)
						)
					}
				}
			}
		}
	}

	if(pinDialog != null)
	{
		var pin by remember { mutableStateOf("") }
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text(if(pinDialog!!.pinIncorrect) "PIN Incorrect" else "Enter PIN") },
			text = {
				Column {
					Text("Enter the PIN shown on your TV/console login screen.")
					Spacer(Modifier.height(12.dp))
					TvTextField("PIN", pin) {
						if(it.all { c -> c.isDigit() } && it.length <= 8) pin = it
					}
				}
			},
			confirmButton = {
				TextButton(onClick = {
					session.setLoginPin(pin)
					pinDialog = null
				}) { Text("Connect", color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) }
			}
		)
	}

	if(quitDialog != null)
	{
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text("Session Ended") },
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
				}) { Text("Reconnect", color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) }
			}
		)
	}

	if(disconnectDialogVisible)
	{
		AlertDialog(
			onDismissRequest = { session.dismissDisconnectDialog() },
			title = { Text("Disconnect") },
			text = { Text("Do you want the Console to go into sleep mode?") },
			confirmButton = {
				TextButton(onClick = {
					session.dismissDisconnectDialog()
					session.gotoBed()
					onSleep()
				}) { Text("Sleep", color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = {
					session.dismissDisconnectDialog()
					onQuit()
				}) { Text("Disconnect", color = OnSurfaceMuted) }
			}
		)
	}

	if(createError != null)
	{
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text("Failed to Start Session") },
			text = { Text(createError!!.error.errorCode.toString(), color = OnSurfaceMuted) },
			confirmButton = { TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) } }
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
	onVertical: ((Int) -> Unit)? = null
)
{
	var focused by remember { mutableStateOf(false) }
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.padding(horizontal = 4.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(if(focused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
			.then(
				if(onClick != null)
					Modifier
						.onFocusEvent { focused = it.isFocused }
						.onKeyEvent { ev ->
							if(ev.type != KeyEventType.KeyDown || onVertical == null)
								false
							else
								when(ev.key)
								{
									Key.DirectionUp -> { onVertical(1); true }
									Key.DirectionDown -> { onVertical(-1); true }
									else -> false
								}
						}
						.clickable { onClick() }
				else
					Modifier
			)
			.padding(horizontal = 12.dp, vertical = 6.dp)
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
			Text(sub, fontSize = 12.sp, color = OnSurfaceMuted)
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
