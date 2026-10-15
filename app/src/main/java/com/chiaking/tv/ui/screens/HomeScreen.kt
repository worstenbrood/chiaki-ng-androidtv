// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.common.*
import com.metallic.chiaki.lib.DiscoveryHost
import com.chiaking.tv.ui.*
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HomeAction
data class ConnectHost(val host: DisplayHost): HomeAction()
data class WakeupHost(val host: DisplayHost): HomeAction()
data class EditManualHost(val manualHost: ManualHost): HomeAction()
data class DeleteManualHost(val manualHost: ManualHost): HomeAction()
object RegisterNew: HomeAction()
data class RegisterHost(val host: String, val ps5: Boolean): HomeAction()
object AddManualHost: HomeAction()
object OpenSettings: HomeAction()
object ToggleDiscovery: HomeAction()
data class ConnectRemoteConsole(val device: com.metallic.chiaki.lib.HolepunchDevice): HomeAction()
object RefreshPsnDevices: HomeAction()

private val CARD_WIDTH = 220.dp
private val CARD_HEIGHT = 164.dp
private const val FOCUS_REQUEST_DELAY_MS = 100L
private const val CLOCK_POLL_MS = 10_000L

@Composable
fun HomeScreen(
	hosts: List<DisplayHost>,
	discoveryActive: Boolean,
	psnLoggedIn: Boolean = false,
	psnDevices: List<com.metallic.chiaki.lib.HolepunchDevice> = emptyList(),
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

	val clock = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
	LaunchedEffect(Unit)
	{
		while(true)
		{
			delay(CLOCK_POLL_MS)
			clock.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
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
				fontSize = 26.sp,
				fontWeight = FontWeight.Light,
				letterSpacing = 1.sp
			)
			Spacer(Modifier.weight(1f))
			Text(clock.value, fontSize = 17.sp, color = OnSurfaceMuted, fontWeight = FontWeight.Medium)
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
					colors = listOf(PsBlue.copy(alpha = 0.28f), PsBlue.copy(alpha = 0.08f), Color.Transparent),
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
						fontSize = 15.sp
					)
				}

				SectionHeader(stringResource(R.string.local))
				Row(
					horizontalArrangement = Arrangement.spacedBy(20.dp),
					modifier = Modifier
						.horizontalScroll(rememberScrollState())
						.padding(vertical = 4.dp)
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
							fontSize = 13.sp,
							fontWeight = FontWeight.SemiBold,
							color = OnSurfaceLight,
							letterSpacing = 2.5.sp
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
							.padding(vertical = 4.dp, horizontal = 2.dp)
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

@Composable
private fun RemoteConsoleCard(
	device: com.metallic.chiaki.lib.HolepunchDevice,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit,
	onAction: (HomeAction) -> Unit
)
{
	TvFocusCard(
		focusRequester = focusRequester,
		onFocusChange = onFocus,
		onClick = { onAction(ConnectRemoteConsole(device)) }
	) {
		Column(
			modifier = Modifier
				.width(CARD_WIDTH)
				.height(CARD_HEIGHT)
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(
				Icons.Default.Cloud,
				null,
				tint = if(device.isPS5) Ps5Blue else Ps4Blue,
				modifier = Modifier.size(52.dp)
			)
			Spacer(Modifier.height(10.dp))
			Text(
				device.name.ifBlank { stringResource(if(device.isPS5) R.string.playstation5 else R.string.playstation4) },
				fontSize = 16.sp,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Spacer(Modifier.height(4.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(8.dp).background(SuccessGreen, CircleShape))
				Spacer(Modifier.width(6.dp))
				Text(stringResource(R.string.ready), fontSize = 12.sp, color = SuccessGreen)
				Spacer(Modifier.width(8.dp))
				Text(stringResource(if(device.isPS5) R.string.ps5 else R.string.ps4), fontSize = 12.sp, color = OnSurfaceMuted)
			}
		}
	}
}

@Composable
private fun ConsoleCard(
	host: DisplayHost,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit,
	onAction: (HomeAction) -> Unit
)
{
	val standby = host is DiscoveredDisplayHost && host.discoveredHost.state == DiscoveryHost.State.STANDBY
	val ready = host is DiscoveredDisplayHost && host.discoveredHost.state == DiscoveryHost.State.READY
	val runningApp = (host as? DiscoveredDisplayHost)?.takeIf {
		!it.cached && it.discoveredHost.state == DiscoveryHost.State.READY
	}?.discoveredHost?.let {
		it.runningAppName?.takeIf { n -> n.isNotBlank() }
			?: it.runningAppTitleid?.takeIf { t -> t.isNotBlank() }
	}
	val stateColor = when {
		ready -> SuccessGreen
		standby -> WarningAmber
		host.isRegistered -> PsBlueLight
		else -> OnSurfaceMuted
	}
	val stateText = stringResource(
		when {
			ready -> R.string.ready
			standby -> R.string.rest_mode
			host.isRegistered -> R.string.registered
			else -> R.string.not_registered
		}
	)

	TvFocusCard(
		focusRequester = focusRequester,
		onFocusChange = onFocus,
		onClick = {
			if(host.isRegistered)
			{
				if(standby) onAction(WakeupHost(host)) else onAction(ConnectHost(host))
			}
			else
				onAction(RegisterHost(host.host, host.isPS5))
		}
	) {
		Column(
			modifier = Modifier
				.width(CARD_WIDTH)
				.height(CARD_HEIGHT)
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(
				Icons.Default.SportsEsports,
				null,
				tint = if(host.isPS5) Ps5Blue else Ps4Blue,
				modifier = Modifier.size(52.dp)
			)
			Spacer(Modifier.height(10.dp))
			Text(
				host.name ?: stringResource(if(host.isPS5) R.string.playstation5 else R.string.playstation4),
				fontSize = 16.sp,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Spacer(Modifier.height(4.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(8.dp).background(stateColor, CircleShape))
				Spacer(Modifier.width(6.dp))
				Text(stateText, fontSize = 12.sp, color = stateColor)
				Spacer(Modifier.width(8.dp))
				Text(stringResource(if(host.isPS5) R.string.ps5 else R.string.ps4), fontSize = 12.sp, color = OnSurfaceMuted)
			}
			if(runningApp != null)
				Text(
					runningApp,
					fontSize = 11.sp,
					color = OnSurfaceMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 4.dp)
				)
			if(host is ManualDisplayHost)
			{
				Spacer(Modifier.height(10.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TvButton(stringResource(R.string.edit), onClick = { onAction(EditManualHost(host.manualHost)) })
					TvButton(stringResource(R.string.delete), onClick = { onAction(DeleteManualHost(host.manualHost)) })
				}
			}
		}
	}
}

@Composable
private fun AddCard(
	text: String,
	icon: ImageVector,
	sub: String? = null,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {},
	onClick: () -> Unit
)
{
	TvFocusCard(
		focusRequester = focusRequester,
		onFocusChange = onFocus,
		onClick = onClick,
		containerColor = SurfaceDark
	) {
		Column(
			modifier = Modifier
				.width(CARD_WIDTH)
				.height(CARD_HEIGHT)
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(icon, null, tint = PsCyan, modifier = Modifier.size(40.dp))
			Spacer(Modifier.height(8.dp))
			Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
			if(sub != null)
				Text(sub, fontSize = 13.sp, color = if(sub.contains("-1")) WarningAmber else OnSurfaceMuted)
		}
	}
}
