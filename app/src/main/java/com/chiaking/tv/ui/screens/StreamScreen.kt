// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectInfo
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.session.*
import com.chiaking.tv.ui.TvButton
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay

private data class HudStats(val fps: Double, val kbps: Double, val framesLost: Long)

@Composable
fun StreamScreen(
	session: StreamSession,
	preferences: Preferences,
	onQuit: () -> Unit
)
{
	val state by session.state.collectAsState()
	val hudVisible by session.hudVisible.collectAsState()
	var hudStats by remember { mutableStateOf<HudStats?>(null) }
	var pinDialog by remember { mutableStateOf<StreamStateLoginPinRequest?>(null) }
	var quitDialog by remember { mutableStateOf<StreamStateQuit?>(null) }
	var createError by remember { mutableStateOf<StreamStateCreateError?>(null) }

	var volume by remember { mutableStateOf(preferences.audioVolume) }
	val ctx = LocalContext.current
	val audioDevices = remember {
		val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
		val types = setOf(
			android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
			android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
			android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
			android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
			android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
			android.media.AudioDeviceInfo.TYPE_HDMI,
			android.media.AudioDeviceInfo.TYPE_HDMI_ARC,
			android.media.AudioDeviceInfo.TYPE_HDMI_EARC,
			android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
			android.media.AudioDeviceInfo.TYPE_USB_HEADSET
		)
		listOf("Default" to 0) + am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
			.filter { it.isSink && types.contains(it.type) }
			.map { (it.productName?.toString() ?: "Device ${it.type}") to it.id }
			.distinctBy { it.second }
	}
	var deviceIndex by remember { mutableStateOf(audioDevices.indexOfFirst { it.second == preferences.audioDeviceId }.coerceAtLeast(0)) }
	val qualityPresets = remember {
		listOf(
			Preferences.Resolution.RES_720P to Preferences.FPS.FPS_30,
			Preferences.Resolution.RES_720P to Preferences.FPS.FPS_60,
			Preferences.Resolution.RES_1080P to Preferences.FPS.FPS_30,
			Preferences.Resolution.RES_1080P to Preferences.FPS.FPS_60
		)
	}
	var qualityIndex by remember { mutableStateOf(qualityPresets.indexOfFirst { it.first == preferences.resolution && it.second == preferences.fps }.coerceAtLeast(0)) }
	val hudFocus = remember { FocusRequester() }

	LaunchedEffect(hudVisible)
	{
		if(hudVisible)
		{
			try { hudFocus.requestFocus() } catch(e: Exception) { }
		}
	}

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
						stats[2]
					)
			}
			lastStats = stats
			lastTime = now
			delay(1000)
		}
	}

	BackHandler(onBack = onQuit)

	LaunchedEffect(state) {
		when(state)
		{
			is StreamStateLoginPinRequest -> pinDialog = state as StreamStateLoginPinRequest
			is StreamStateQuit -> {
				val quit = state as StreamStateQuit
				if(quit.reason.isError)
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
			StreamStateConnecting, StreamStateIdle -> Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center
			) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					CircularProgressIndicator(color = PsBlueLight)
					Spacer(Modifier.height(16.dp))
					Text("Connecting...", color = Color.White, fontSize = 20.sp)
				}
			}
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
						sub = if(state is StreamStateConnected) "Connected" else "Connecting"
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
						sub = hudStats?.let { String.format("%.1f Mbps • %d lost", it.kbps / 1000.0, it.framesLost) } ?: ""
					)
					HudDivider()
					HudItem(
						label = "Audio",
						value = "$volume%",
						sub = audioDevices[deviceIndex].first
					)
				}
				Spacer(Modifier.height(12.dp))
				Row(
					horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
					verticalAlignment = Alignment.CenterVertically
				) {
					TvButton(
						text = "Volume: $volume%",
						onClick = {
							volume = if(volume >= 100) 0 else volume + 25
							preferences.audioVolume = volume
							session.setVolumePercent(volume)
						},
						focusRequester = hudFocus
					)
					TvButton(
						text = "Output: ${audioDevices[deviceIndex].first}",
						onClick = {
							deviceIndex = (deviceIndex + 1) % audioDevices.size
							preferences.audioDeviceId = audioDevices[deviceIndex].second
						}
					)
					TvButton(
						text = "Quality: ${qualityPresets[qualityIndex].first.title} ${qualityPresets[qualityIndex].second.title}",
						onClick = {
							qualityIndex = (qualityIndex + 1) % qualityPresets.size
							preferences.resolution = qualityPresets[qualityIndex].first
							preferences.fps = qualityPresets[qualityIndex].second
						}
					)
					Text(
						"Volume applies now • Output & Quality apply next session • Back closes HUD",
						fontSize = 12.sp,
						color = OnSurfaceMuted
					)
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
					session.resume()
				}) { Text("Reconnect", color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) }
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
private fun HudItem(label: String, value: String, sub: String)
{
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
