// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.session.StreamState
import com.chiaking.tv.session.StreamStateConnected
import com.chiaking.tv.session.StreamStateDisconnecting
import com.chiaking.tv.ui.FocusMemory
import com.chiaking.tv.ui.theme.*
import com.metallic.chiaki.lib.Codec
import kotlinx.coroutines.delay

private data class HudStats(val fps: Double, val kbps: Double, val framesLost: Long, val packetLossPct: Double, val rttMs: Long, val cpuAppPct: Double, val cpuCores: Double)

private const val HUD_STATS_POLL_MS = 1000L
private const val HUD_FOCUS_SETTLE_MS = 400L
private const val HUD_FOCUS_REQUEST_MS = 50L
private const val NS_PER_S = 1_000_000_000.0
private const val BITS_PER_BYTE = 8.0
private const val BITS_PER_KBIT = 1000.0
private const val PACKET_LOSS_PCT_SCALE = 10000.0
private const val MICROS_PER_MS = 1000
private const val KERNEL_HZ = 100.0

private fun readSelfCpuTicks(): Long?
{
	return try
	{
		val line = java.io.File("/proc/self/stat").readText()
		val fields = line.substringAfterLast(')').trim().split(Regex("\\s+"))
		// after (comm): state=0 ... utime=11, stime=12
		fields[11].toLong() + fields[12].toLong()
	}
	catch(e: Exception)
	{
		null
	}
}

@Composable
fun StreamHud(
	session: StreamSession,
	preferences: Preferences,
	hudVisible: Boolean,
	state: StreamState,
	displayMode: Preferences.DisplayMode,
	onCycleDisplayMode: () -> Unit,
	onClose: () -> Unit,
	modifier: Modifier = Modifier
)
{
	val hudScroll = rememberScrollState()
	val hudFocus = remember { FocusRequester() }
	val showCpu = preferences.hudShowCpu
	val closeIdx = if(showCpu) 7 else 6
	val lastIdx = closeIdx
	var hudFocusIdx by remember { mutableStateOf(0) }
	var hudStats by remember { mutableStateOf<HudStats?>(null) }
	var volume by remember { mutableStateOf(preferences.audioVolume) }
	val ctx = LocalContext.current
	val audioDevices = remember { AudioDevices.outputDevices(ctx) }
	var deviceIndex by remember { mutableStateOf(
		(audioDevices.indexOfFirst { it.first == preferences.audioDeviceName }
			.takeIf { it >= 0 }
			?: audioDevices.indexOfFirst { it.second == preferences.audioDeviceId })
			.coerceAtLeast(0)) }
	val deviceName = audioDevices[deviceIndex].first

	LaunchedEffect(hudVisible, showCpu)
	{
		if(!hudVisible)
		{
			hudStats = null
			return@LaunchedEffect
		}
		var lastStats: LongArray? = null
		var lastTime = 0L
		var lastCpuTicks: Long? = null
		while(hudVisible)
		{
			val now = System.nanoTime()
			val stats = session.getStats()
			val cpuTicks = if(showCpu) readSelfCpuTicks() else null
			if(stats != null && lastStats != null && lastTime != 0L)
			{
				val dt = (now - lastTime) / NS_PER_S
				if(dt > 0.0)
				{
					val cpuAppPct = if(cpuTicks != null && lastCpuTicks != null)
						(cpuTicks - lastCpuTicks) * 100.0 / (dt * KERNEL_HZ)
						else 0.0
					hudStats = HudStats(
						(stats[0] - lastStats[0]) / dt,
						(stats[1] - lastStats[1]) * BITS_PER_BYTE / dt / BITS_PER_KBIT,
						stats[2],
						stats[3] / PACKET_LOSS_PCT_SCALE,
						stats[4] / MICROS_PER_MS,
						cpuAppPct,
						cpuAppPct / 100.0
					)
				}
			}
			lastStats = stats
			lastTime = now
			lastCpuTicks = cpuTicks
			delay(HUD_STATS_POLL_MS)
		}
	}

	LaunchedEffect(hudVisible)
	{
		if(hudVisible)
		{
			delay(HUD_FOCUS_SETTLE_MS)
			hudFocusIdx = FocusMemory.get("hud").coerceIn(0, lastIdx)
			delay(HUD_FOCUS_REQUEST_MS)
			try { hudFocus.requestFocus() } catch(e: Exception)
			{ android.util.Log.e("StreamHud", "HUD focus request failed", e) }
		}
	}

	AnimatedVisibility(
		visible = hudVisible,
		enter = slideInVertically(initialOffsetY = { it }),
		exit = slideOutVertically(targetOffsetY = { it }),
		modifier = modifier
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
				.clip(HudPanelShape)
				.background(SurfaceFrosted)
				.padding(horizontal = 16.dp, vertical = 10.dp),
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
					value = stringResource(displayMode.titleRes),
					sub = stringResource(R.string.ok_to_cycle),
					onClick = onCycleDisplayMode,
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
				if(showCpu)
				{
					HudDivider()
					HudItem(
						label = stringResource(R.string.cpu),
						value = hudStats?.let { stringResource(R.string.cpu_value, it.cpuAppPct) } ?: "—",
						sub = hudStats?.let { stringResource(R.string.cpu_sub, it.cpuCores) } ?: "",
						focusReq = if(hudFocusIdx == 6) hudFocus else null,
						selected = hudFocusIdx == 6,
						onSelect = { if(it) { hudFocusIdx = 6; FocusMemory.set("hud", 6) } }
					)
				}
				HudDivider()
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					modifier = Modifier
						.widthIn(min = 140.dp)
						.clip(SelectionShape)
						.background(if(hudFocusIdx == closeIdx) HudChipFocus else Color.Transparent)
						.then(if(hudFocusIdx == closeIdx) Modifier.focusRequester(hudFocus) else Modifier)
						.onFocusEvent { if(it.isFocused) { hudFocusIdx = closeIdx; FocusMemory.set("hud", closeIdx) } }
						.onKeyEvent { ev ->
							if(ev.key == Key.ButtonA && ev.type == KeyEventType.KeyUp)
							{
								onClose()
								true
							}
							else
								false
						}
						.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
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
							tint = if(hudFocusIdx == 6) OnVideo else OnSurfaceMuted,
							modifier = Modifier.size(22.dp)
						)
					}
				}
			}
			if(hudScroll.maxValue > 0 && hudFocusIdx > 0)
				Icon(
					Icons.Default.KeyboardArrowLeft,
					contentDescription = null,
					tint = OnVideo,
					modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
				)
			if(hudScroll.maxValue > 0 && hudFocusIdx < lastIdx)
				Icon(
					Icons.Default.KeyboardArrowRight,
					contentDescription = null,
					tint = OnVideo,
					modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)
				)
			}
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
			.clip(HudChipShape)
			.background(if(selected) HudChipFocus else Color.Transparent)
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
		Text(value, fontSize = 17.sp, color = OnVideo, fontWeight = FontWeight.Bold)
		if(sub.isNotEmpty())
			Text(
				sub,
				fontSize = 12.sp,
				color = OnSurfaceMuted,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
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
			.background(OutlineSubtle)
	)
}
