// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.common.*
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.DiscoveryHost
import com.chiaking.tv.ui.*
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay

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

private val CARD_WIDTH = 260.dp
private val CARD_HEIGHT = 150.dp

@Composable
fun HomeScreen(
	hosts: List<DisplayHost>,
	discoveryActive: Boolean,
	wakingHost: DisplayHost?,
	wakeBannerText: String?,
	onCancelWakeup: () -> Unit,
	psnLoggedIn: Boolean = false,
	psnDevices: List<com.metallic.chiaki.lib.HolepunchDevice> = emptyList(),
	psnLoading: Boolean = false,
	psnDeviceCount: Int? = null,
	remoteConnectingText: String? = null,
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
			delay(100)
			try { localFocus.requestFocus() } catch(e: Exception) { }
			focusRequested = true
		}
	}

	Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(Icons.Default.Gamepad, null, tint = PsBlueLight, modifier = Modifier.size(36.dp))
			Spacer(Modifier.width(12.dp))
			Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Bold)
			Spacer(Modifier.weight(1f))
			TvButton(
				text = stringResource(if(discoveryActive) R.string.discovery_on else R.string.discovery_off),
				onClick = { onAction(ToggleDiscovery) },
				colors = ButtonDefaults.buttonColors(
					containerColor = if(discoveryActive) PsBlue else SurfaceCard,
					contentColor = Color.White
				)
			)
			Spacer(Modifier.width(12.dp))
			TvButton(text = stringResource(R.string.settings), onClick = { onAction(OpenSettings) })
		}

		Spacer(Modifier.height(12.dp))

		if(wakingHost != null)
		{
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
			) {
				CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(24.dp))
				Text(
					wakeBannerText ?: stringResource(
						R.string.waiting_for_console,
						wakingHost.name ?: stringResource(R.string.playstation5)
					),
					color = OnSurfaceMuted,
					fontSize = 16.sp
				)
				Spacer(Modifier.weight(1f))
				TvButton(text = stringResource(R.string.cancel), onClick = onCancelWakeup)
			}
		}

		if(remoteConnectingText != null)
		{
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
			) {
				CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(24.dp))
				Text(remoteConnectingText, color = OnSurfaceMuted, fontSize = 16.sp)
			}
		}

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			contentAlignment = Alignment.Center
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier.verticalScroll(rememberScrollState())
			) {
				SectionHeader(stringResource(R.string.local))
				if(hosts.isEmpty())
				{
					Icon(
						Icons.Default.Search,
						null,
						tint = PsBlueLight,
						modifier = Modifier.size(72.dp)
					)
					Spacer(Modifier.height(16.dp))
					Text(
						stringResource(if(discoveryActive) R.string.no_consoles_discovered else R.string.discovery_off_hint),
						textAlign = TextAlign.Center,
						color = OnSurfaceMuted,
						fontSize = 16.sp
					)
					Spacer(Modifier.height(32.dp))
				}
				Row(
					horizontalArrangement = Arrangement.spacedBy(20.dp),
					modifier = Modifier
						.horizontalScroll(rememberScrollState())
						.padding(vertical = 4.dp)
				) {
					hosts.forEachIndexed { index, host ->
						ConsoleCard(
							host = host,
							waking = wakingHost?.host == host.host,
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
					Spacer(Modifier.height(16.dp))
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(
							stringResource(R.string.remote).uppercase(),
							fontSize = 13.sp,
							fontWeight = FontWeight.Bold,
							color = PsBlueLight,
							letterSpacing = 2.sp
						)
						if(psnLoading)
						{
							Spacer(Modifier.width(12.dp))
							CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(18.dp))
						}
					}
					Spacer(Modifier.height(8.dp))
					Row(
						horizontalArrangement = Arrangement.spacedBy(20.dp),
						modifier = Modifier
							.horizontalScroll(rememberScrollState())
							.padding(vertical = 4.dp)
					) {
						psnDevices.forEachIndexed { index, device ->
							RemoteConsoleCard(
								device,
								remoteConnectingText != null,
								focusRequester = if(index == remoteTarget) remoteFocus else null,
								onFocus = { if(it) FocusMemory.set("home_remote", index) },
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
			}
		}
	}
}

@Composable
private fun RemoteConsoleCard(
	device: com.metallic.chiaki.lib.HolepunchDevice,
	connecting: Boolean,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit,
	onAction: (HomeAction) -> Unit
)
{
	TvFocusCard(
		focusRequester = focusRequester,
		onFocusChange = onFocus,
		onClick = { if(!connecting) onAction(ConnectRemoteConsole(device)) }
	) {
		Column(
			modifier = Modifier
				.width(CARD_WIDTH)
				.height(CARD_HEIGHT)
				.padding(20.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(
					Icons.Default.Cloud,
					null,
					tint = if(device.isPS5) Ps5Blue else Ps4Blue,
					modifier = Modifier.size(40.dp)
				)
				Spacer(Modifier.width(12.dp))
				Column {
					Text(
						device.name.ifBlank { stringResource(if(device.isPS5) R.string.playstation5 else R.string.playstation4) },
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(stringResource(R.string.remote_play), fontSize = 13.sp, color = OnSurfaceMuted)
				}
			}
			Spacer(Modifier.height(16.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(10.dp).background(PsBlueLight, androidx.compose.foundation.shape.CircleShape))
				Spacer(Modifier.width(8.dp))
				Text(stringResource(if(connecting) R.string.linking else R.string.ready), fontSize = 14.sp, color = PsBlueLight)
				Spacer(Modifier.weight(1f))
				Text(stringResource(if(device.isPS5) R.string.ps5 else R.string.ps4), fontSize = 13.sp, color = OnSurfaceMuted)
			}
		}
	}
}

@Composable
private fun ConsoleCard(
	host: DisplayHost,
	waking: Boolean = false,
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
		waking -> PsBlueLight
		ready -> SuccessGreen
		standby -> WarningAmber
		host.isRegistered -> PsBlueLight
		else -> OnSurfaceMuted
	}
	val stateText = stringResource(
		when {
			waking -> R.string.waking
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
				.padding(20.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(
					Icons.Default.SportsEsports,
					null,
					tint = if(host.isPS5) Ps5Blue else Ps4Blue,
					modifier = Modifier.size(40.dp)
				)
				Spacer(Modifier.width(12.dp))
				Column {
					Text(
						host.name ?: stringResource(if(host.isPS5) R.string.playstation5 else R.string.playstation4),
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold
					)
					Text(host.host, fontSize = 13.sp, color = OnSurfaceMuted)
				}
			}
			Spacer(Modifier.height(16.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(10.dp).background(stateColor, androidx.compose.foundation.shape.CircleShape))
				Spacer(Modifier.width(8.dp))
				Text(stateText, fontSize = 14.sp, color = stateColor)
				Spacer(Modifier.weight(1f))
				Text(stringResource(if(host.isPS5) R.string.ps5 else R.string.ps4), fontSize = 13.sp, color = OnSurfaceMuted)
			}
			if(runningApp != null)
			{
				Spacer(Modifier.height(12.dp))
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(Icons.Default.PlayArrow, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
					Spacer(Modifier.width(6.dp))
					Text(
						runningApp,
						fontSize = 13.sp,
						color = OnSurfaceMuted,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			if(host is ManualDisplayHost)
			{
				Spacer(Modifier.height(12.dp))
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
			Icon(icon, null, tint = PsBlueLight, modifier = Modifier.size(44.dp))
			Spacer(Modifier.height(8.dp))
			Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
			if(sub != null)
				Text(sub, fontSize = 13.sp, color = if(sub.contains("-1")) WarningAmber else OnSurfaceMuted)
		}
	}
}
