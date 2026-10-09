// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.ui.*
import com.chiaking.tv.ui.theme.*

@Composable
fun SettingsScreen(
	preferences: Preferences,
	registeredHostCount: Int,
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

	val ctx = androidx.compose.ui.platform.LocalContext.current
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

	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
		Row(
			modifier = Modifier.padding(horizontal = 32.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			TvButton("Back", onClick = onBack)
			Spacer(Modifier.width(20.dp))
			Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Bold)
		}

		SectionHeader("Stream")
		SettingsCard {
			TvCycleRow(
				title = "Resolution",
				value = resolution.title,
				onNext = {
					resolution = Preferences.Resolution.values()[(resolution.ordinal + 1) % Preferences.Resolution.values().size]
					preferences.resolution = resolution
				}
			)
			TvCycleRow(
				title = "Frames per Second",
				value = fps.title,
				onNext = {
					fps = Preferences.FPS.values()[(fps.ordinal + 1) % Preferences.FPS.values().size]
					preferences.fps = fps
				}
			)
			TvCycleRow(
				title = "Video Codec",
				value = codec.title,
				onNext = {
					codec = Preferences.Codec.values()[(codec.ordinal + 1) % Preferences.Codec.values().size]
					preferences.codec = codec
				}
			)
			TvCycleRow(
				title = "Bitrate",
				value = if(bitrate == null) "Auto (${preferences.bitrateAuto} kbps)" else "$bitrate kbps",
				onNext = {
					bitrate = when(bitrate)
					{
						null -> 5000
						5000 -> 10000
						10000 -> 20000
						20000 -> 50000
						else -> null
					}
					preferences.bitrate = bitrate
				}
			)
		}

		SectionHeader("Audio")
		SettingsCard {
			TvCycleRow(
				title = "Volume",
				value = "$volume%",
				onNext = {
					volume = if(volume >= 100) 0 else volume + 25
					preferences.audioVolume = volume
				}
			)
			TvCycleRow(
				title = "Output Device",
				value = audioDevices[deviceIndex].first,
				onNext = {
					deviceIndex = (deviceIndex + 1) % audioDevices.size
					preferences.audioDeviceId = audioDevices[deviceIndex].second
					preferences.audioDeviceName = audioDevices[deviceIndex].first
				}
			)
		}

		SectionHeader("Controller")
		SettingsCard {
			TvSwitchRow(
				"Swap Cross / Circular",
				"Swap the A and B button meanings (Japanese console layout)",
				swapCrossMoon
			) { swapCrossMoon = it; preferences.swapCrossMoon = it }
			TvSwitchRow(
				"D-pad sends controller input",
				"Use the remote control D-pad as the controller D-pad while streaming",
				dpadAsController
			) { dpadAsController = it; preferences.dpadAsController = it }
			TvSwitchRow("Rumble", "Vibrate the gamepad on console rumble events", rumble)
			{ rumble = it; preferences.rumbleEnabled = it }
			TvSwitchRow("Motion Controls", "Use device gyroscope/accelerometer as controller motion", motion)
			{ motion = it; preferences.motionEnabled = it }
			TvSwitchRow("Button Haptics", "Haptic feedback on button presses", haptics)
			{ haptics = it; preferences.buttonHapticEnabled = it }
		}

		SectionHeader("General")
		SettingsCard {
			TvSwitchRow("Console Discovery", "Automatically find consoles on the local network", discovery)
			{ discovery = it; preferences.discoveryEnabled = it }
			TvSwitchRow("Verbose Logs", "Write detailed session logs for debugging", logVerbose)
			{ logVerbose = it; preferences.logVerbose = it }
			TvActionRow("Session Logs", "View recorded session logs", onShowLogs)
			TvActionRow(
				"Registered Consoles",
				if(registeredHostCount == 0) "None registered" else "$registeredHostCount registered",
				onManageHosts
			)
		}

		SectionHeader("Backup")
		SettingsCard {
			TvActionRow("Export Settings", "Save registered and manual hosts to a JSON file", onExportSettings)
			TvActionRow("Import Settings", "Restore hosts from a chiaki-settings.json file", onImportSettings)
		}

		Spacer(Modifier.height(32.dp))
	}
}
