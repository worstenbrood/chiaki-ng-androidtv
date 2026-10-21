// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.ui.focus.FocusMemory
import com.chiaking.tv.ui.components.buttons.PsIconButton
import com.chiaking.tv.ui.components.settings.SectionHeader
import com.chiaking.tv.ui.theme.HomeGlowStrong
import com.chiaking.tv.ui.theme.HomeGlowWeak
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.PsSectionHeaderStyle
import com.metallic.chiaki.lib.HolepunchDevice
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FOCUS_REQUEST_DELAY_MS = 100L
private const val CLOCK_POLL_MS = 10_000L

/** Own composable so the 10 s clock tick only recomposes this Text, not the whole home screen. */
@Composable
private fun ClockText()
{
	val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
	var now by remember { mutableStateOf(format.format(Date())) }
	LaunchedEffect(Unit)
	{
		while(true)
		{
			delay(CLOCK_POLL_MS)
			now = format.format(Date())
		}
	}
	Text(now, style = MaterialTheme.typography.titleMedium, color = OnSurfaceMuted)
}

@Composable
fun HomeScreen(
	hosts: List<DisplayHost>,
	discoveryActive: Boolean,
	psnLoggedIn: Boolean = false,
	psnDevices: List<HolepunchDevice> = emptyList(),
	psnLoading: Boolean = false,
	psnDeviceCount: Int? = null,
	onAction: (HomeAction) -> Unit
)
{
	val localFocus = remember { FocusRequester() }
	val remoteFocus = remember { FocusRequester() }
	val localTarget = remember { FocusMemory.get("home_local").coerceIn(0, hosts.size + 1) }
	val remoteTarget = remember { FocusMemory.get("home_remote").coerceIn(0, psnDevices.size) }
	var focusRequested by remember { mutableStateOf(false) }

	LaunchedEffect(Unit)
	{
		if(!focusRequested)
		{
			delay(FOCUS_REQUEST_DELAY_MS)
			try { localFocus.requestFocus() } catch(e: Exception) { }
			focusRequested = true
		}
	}

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 40.dp)
		) {
			Icon(Icons.Default.Gamepad, null, tint = PsCyan, modifier = Modifier.size(28.dp))
			Spacer(Modifier.width(12.dp))
			Text(
				stringResource(R.string.app_name),
				style = MaterialTheme.typography.headlineLarge
			)
			Spacer(Modifier.weight(1f))
			ClockText()
			Spacer(Modifier.width(24.dp))
			PsIconButton(
				icon = if(discoveryActive) Icons.Default.SettingsInputAntenna else Icons.Default.SettingsEthernet,
				contentDescription = stringResource(if(discoveryActive) R.string.discovery_on else R.string.discovery_off),
				onClick = { onAction(ToggleDiscovery) }
			)
			Spacer(Modifier.width(12.dp))
			PsIconButton(
				icon = Icons.Default.Settings,
				contentDescription = stringResource(R.string.settings),
				onClick = { onAction(OpenSettings) }
			)
		}

		BoxWithConstraints(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
		) {
			val density = LocalDensity.current
			val glowBrush = with(density) {
				Brush.radialGradient(
					colors = listOf(HomeGlowStrong, HomeGlowWeak, Color.Transparent),
					center = Offset(maxWidth.toPx() * 0.78f, maxHeight.toPx() * 0.18f),
					radius = maxWidth.toPx() * 0.85f
				)
			}
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(glowBrush)
			)
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 40.dp)
			) {
				if(hosts.isEmpty())
				{
					Spacer(Modifier.height(24.dp))
					Text(
						stringResource(if(discoveryActive) R.string.no_consoles_discovered else R.string.discovery_off_hint),
						color = OnSurfaceMuted,
						style = MaterialTheme.typography.bodyMedium
					)
				}

				SectionHeader(stringResource(R.string.local))
				Row(
					horizontalArrangement = Arrangement.spacedBy(20.dp),
					modifier = Modifier
						.horizontalScroll(rememberScrollState())
						.padding(vertical = 4.dp, horizontal = 12.dp)
				) {
					hosts.forEachIndexed { index, host ->
						ConsoleCard(
							host = host,
							focusRequester = if(index == localTarget) localFocus else null,
							onFocus = { focused -> if(focused) FocusMemory.set("home_local", index) },
							onAction = onAction
						)
					}
					AddCard(
						stringResource(R.string.register_console),
						Icons.Default.AddCircleOutline,
						focusRequester = if(hosts.size == localTarget) localFocus else null,
						onFocus = { if(it) FocusMemory.set("home_local", hosts.size) }
					) { onAction(RegisterNew) }
					AddCard(
						stringResource(R.string.add_manual_host),
						Icons.Default.Language,
						focusRequester = if(hosts.size + 1 == localTarget) localFocus else null,
						onFocus = { if(it) FocusMemory.set("home_local", hosts.size + 1) }
					) { onAction(AddManualHost) }
				}

				if(psnLoggedIn)
				{
					Spacer(Modifier.height(24.dp))
					Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp)) {
						Text(
							stringResource(R.string.remote).uppercase(),
							style = PsSectionHeaderStyle(),
							color = OnSurfaceLight
						)
						if(psnLoading)
						{
							Spacer(Modifier.width(12.dp))
							CircularProgressIndicator(color = PsCyan, modifier = Modifier.size(18.dp))
						}
					}
					Spacer(Modifier.height(8.dp))
					Row(
						horizontalArrangement = Arrangement.spacedBy(20.dp),
						modifier = Modifier
							.horizontalScroll(rememberScrollState())
							.padding(vertical = 4.dp, horizontal = 12.dp)
					) {
						psnDevices.forEachIndexed { index, device ->
							RemoteConsoleCard(
								device,
								focusRequester = if(index == remoteTarget) remoteFocus else null,
								onFocus = { focused -> if(focused) FocusMemory.set("home_remote", index) },
								onAction = onAction
							)
						}
						AddCard(
							stringResource(R.string.refresh_psn_consoles),
							Icons.Default.Refresh,
							sub = when(psnDeviceCount)
							{
								null -> null
								-1 -> stringResource(R.string.found_error)
								else -> stringResource(R.string.found_count, psnDeviceCount)
							},
							focusRequester = if(psnDevices.size == remoteTarget) remoteFocus else null,
							onFocus = { if(it) FocusMemory.set("home_remote", psnDevices.size) }
						) { onAction(RefreshPsnDevices) }
					}
				}
				Spacer(Modifier.height(24.dp))
			}
		}
	}
}
