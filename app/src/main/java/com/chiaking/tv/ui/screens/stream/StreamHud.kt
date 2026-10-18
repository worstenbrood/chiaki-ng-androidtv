// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.CpuUsageMonitor
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.session.StreamState
import com.chiaking.tv.session.StreamStateConnected
import com.chiaking.tv.session.StreamStateDisconnecting
import com.chiaking.tv.ui.focus.FocusMemory
import com.chiaking.tv.ui.theme.HudPanelShape
import com.chiaking.tv.ui.theme.OnVideo
import com.chiaking.tv.ui.theme.SurfaceFrosted
import com.metallic.chiaki.lib.Codec
import kotlinx.coroutines.delay

private const val HUD_STATS_POLL_MS = 1000L
private const val NS_PER_S = 1_000_000_000.0
private const val BITS_PER_BYTE = 8.0
private const val BITS_PER_KBIT = 1000.0
private const val PACKET_LOSS_PCT_SCALE = 10000.0
private const val MICROS_PER_MS = 1000

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
	val hudItems = preferences.hudItems.filter { it.enabled }
	val showCpu = hudItems.any { it.kind == Preferences.HudItemKind.CPU }
	val lastIdx = (hudItems.size - 1).coerceAtLeast(0)
	var hudFocusIdx by remember { mutableStateOf(0) }
	var hudInitialFocus by remember { mutableStateOf(false) }
	var hudStats by remember { mutableStateOf<HudStats?>(null) }
	var volume by remember { mutableStateOf(preferences.audioVolume) }
	val micMuted by session.micMuted.collectAsState()
	val micActive by session.micActive.collectAsState()
	val pitchEnabled by session.micPitch.enabled.collectAsState()
	val pitchSemis by session.micPitch.semitones.collectAsState()
	var micGain by remember { mutableStateOf(preferences.micGain) }
	val cpuMonitor = remember { CpuUsageMonitor() }
	val ctx = LocalContext.current
	val audioDevices = remember { AudioDevices.outputDevices(ctx) }
	var deviceIndex by remember { mutableStateOf(
		AudioDevices.indexOfSavedOutput(audioDevices, preferences.audioDeviceName, preferences.audioDeviceId)) }
	val deviceName = audioDevices[deviceIndex].first

	LaunchedEffect(hudVisible, showCpu)
	{
		if(!hudVisible)
		{
			hudStats = null
			return@LaunchedEffect
		}
		cpuMonitor.reset()
		var lastStats: LongArray? = null
		var lastTime = 0L
		while(hudVisible)
		{
			val now = System.nanoTime()
			val stats = session.getStats()
			// monitor reports % of total capacity (0-100); chip shows that % + cores used / total
			val cpuAppPct = if(showCpu) cpuMonitor.getCpuUsage() else 0.0
			if(stats != null && lastStats != null && lastTime != 0L)
			{
				val dt = (now - lastTime) / NS_PER_S
				if(dt > 0.0)
				{
					hudStats = HudStats(
						(stats[0] - lastStats[0]) / dt,
						(stats[1] - lastStats[1]) * BITS_PER_BYTE / dt / BITS_PER_KBIT,
						stats[2],
						stats[3] / PACKET_LOSS_PCT_SCALE,
						stats[4] / MICROS_PER_MS,
						cpuAppPct,
						cpuAppPct / 100.0 * cpuMonitor.coreCount,
						cpuMonitor.coreCount
					)
				}
			}
			lastStats = stats
			lastTime = now
			delay(HUD_STATS_POLL_MS)
		}
	}

	LaunchedEffect(hudVisible)
	{
		// arm the one-shot focus grab; the selected chip requests focus itself from
		// onGloballyPositioned (see HudItem), which fires exactly when its FocusRequester
		// is attached — no delays or retries needed
		hudInitialFocus = hudVisible
		if(hudVisible)
			hudFocusIdx = FocusMemory.get("hud").coerceIn(0, lastIdx)
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
		@Composable
		fun chip(i: Int, label: String, value: String, sub: String, onClick: (() -> Unit)? = null, onVertical: ((Int) -> Unit)? = null, icon: ImageVector? = null)
		{
			HudItem(
				label = label,
				value = value,
				sub = sub,
				onClick = onClick,
				onVertical = onVertical,
				icon = icon,
				focusReq = if(hudFocusIdx == i) hudFocus else null,
				selected = hudFocusIdx == i,
				grabFocus = hudInitialFocus && hudFocusIdx == i,
				onFocusGrabbed = { hudInitialFocus = false },
				onSelect = { if(it) { hudFocusIdx = i; FocusMemory.set("hud", i) } }
			)
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
				hudItems.forEachIndexed { i, item ->
					if(i > 0)
						HudDivider()
					when(item.kind)
					{
						Preferences.HudItemKind.CONSOLE -> chip(
							i,
							stringResource(if(session.connectInfo.ps5) R.string.ps5 else R.string.ps4),
							session.connectInfo.host.ifEmpty { stringResource(R.string.remote_host_label) },
							stringResource(
								when(state)
								{
									is StreamStateConnected -> R.string.connected
									is StreamStateDisconnecting -> R.string.disconnecting
									else -> R.string.connecting
								}
							)
						)
						Preferences.HudItemKind.VIDEO -> chip(
							i,
							stringResource(R.string.video),
							stringResource(R.string.video_value, vp.width, vp.height, vp.maxFPS),
							stringResource(R.string.video_sub, codecLabel, vp.bitrate / 1000)
						)
						Preferences.HudItemKind.DISPLAY -> chip(
							i,
							stringResource(R.string.display),
							stringResource(displayMode.titleRes),
							stringResource(R.string.ok_to_cycle),
							onClick = onCycleDisplayMode
						)
						Preferences.HudItemKind.STREAM -> chip(
							i,
							stringResource(R.string.stream),
							hudStats?.let { stringResource(R.string.fps_value, it.fps) } ?: "—",
							hudStats?.let { stringResource(R.string.mbps_value, it.kbps / 1000.0) } ?: ""
						)
						Preferences.HudItemKind.NETWORK -> chip(
							i,
							stringResource(R.string.network),
							hudStats?.let { stringResource(R.string.loss_value, it.packetLossPct) } ?: "—",
							hudStats?.let { stringResource(R.string.ping_sub, it.rttMs, it.framesLost) } ?: ""
						)
						Preferences.HudItemKind.AUDIO -> chip(
							i,
							stringResource(R.string.audio),
							stringResource(R.string.volume_percent, volume),
							deviceName,
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
						Preferences.HudItemKind.MIC -> chip(
							i,
							stringResource(R.string.microphone),
							when {
								!preferences.micEnabled -> stringResource(R.string.mic_off)
								!micActive && state is StreamStateConnected -> stringResource(R.string.mic_permission_needed)
								micMuted -> stringResource(R.string.mic_muted)
								else -> stringResource(R.string.mic_live)
							},
							stringResource(R.string.mic_gain_percent, micGain),
							onClick = { if(preferences.micEnabled) session.toggleMicMute() },
							onVertical = { dir ->
								if(preferences.micEnabled)
								{
									micGain = (micGain + dir * 5).coerceIn(25, 300)
									preferences.micGain = micGain
								}
							}
						)
						Preferences.HudItemKind.PITCH -> chip(
							i,
							stringResource(R.string.mic_pitch),
							stringResource(if(pitchEnabled) R.string.on else R.string.off),
							stringResource(R.string.mic_pitch_semitones, pitchSemis),
							onClick = { if(preferences.micEnabled) session.micPitch.toggle() },
							onVertical = { dir -> if(preferences.micEnabled) session.micPitch.step(dir) }
						)
						Preferences.HudItemKind.CPU -> chip(
							i,
							stringResource(R.string.cpu),
							hudStats?.let { stringResource(R.string.cpu_value, it.cpuAppPct) } ?: "—",
							hudStats?.let { stringResource(R.string.cpu_sub, it.cpuCores, it.cpuCoreCount) } ?: ""
						)
						Preferences.HudItemKind.CLOSE -> chip(
							i,
							stringResource(R.string.hud_close),
							"",
							"",
							onClick = onClose,
							icon = Icons.Default.Close
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
