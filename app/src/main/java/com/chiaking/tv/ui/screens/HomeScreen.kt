// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun HomeScreen(
	hosts: List<DisplayHost>,
	discoveryActive: Boolean,
	onAction: (HomeAction) -> Unit
)
{
	val cardFocus = remember { FocusRequester() }
	var lastFocused by rememberSaveable { mutableStateOf(0) }
	val focusTarget = remember { lastFocused.coerceAtLeast(0) }
	var focusRequested by remember { mutableStateOf(false) }

	LaunchedEffect(hosts.size)
	{
		if(!focusRequested && hosts.isNotEmpty())
		{
			delay(100)
			try { cardFocus.requestFocus() } catch(e: Exception) { }
			focusRequested = true
		}
	}

	Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(Icons.Default.Gamepad, null, tint = PsBlueLight, modifier = Modifier.size(36.dp))
			Spacer(Modifier.width(12.dp))
			Text("Chiaki TV", fontSize = 32.sp, fontWeight = FontWeight.Bold)
			Spacer(Modifier.weight(1f))
			TvButton(
				text = if(discoveryActive) "Discovery: On" else "Discovery: Off",
				onClick = { onAction(ToggleDiscovery) },
				colors = ButtonDefaults.buttonColors(
					containerColor = if(discoveryActive) PsBlue else SurfaceCard,
					contentColor = Color.White
				)
			)
			Spacer(Modifier.width(12.dp))
			TvButton(text = "Settings", onClick = { onAction(OpenSettings) })
		}

		Spacer(Modifier.height(24.dp))

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			contentAlignment = Alignment.Center
		) {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
						if(discoveryActive)
							"No consoles discovered yet.\nMake sure your console is in rest mode or on, and on the same network."
						else
							"Discovery is off.\nRegister a console or add one manually.",
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
						.padding(vertical = 8.dp)
				) {
					hosts.forEachIndexed { index, host ->
						ConsoleCard(
							host = host,
							focusRequester = if(index == focusTarget.coerceAtMost(hosts.lastIndex)) cardFocus else null,
							onFocus = { focused -> if(focused) lastFocused = index },
							onAction = onAction
						)
					}
					AddCard("Register Console", Icons.Default.AddCircleOutline) { onAction(RegisterNew) }
					AddCard("Add Manual Host", Icons.Default.Language) { onAction(AddManualHost) }
				}
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
	val stateText = when {
		ready -> "Ready"
		standby -> "Rest Mode"
		host.isRegistered -> "Registered"
		else -> "Not registered"
	}

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
				.width(260.dp)
				.height(170.dp)
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
						host.name ?: (if(host.isPS5) "PlayStation 5" else "PlayStation 4"),
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
				Text(if(host.isPS5) "PS5" else "PS4", fontSize = 13.sp, color = OnSurfaceMuted)
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
						overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
					)
				}
			}
			if(host is ManualDisplayHost)
			{
				Spacer(Modifier.height(12.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TvButton("Edit", onClick = { onAction(EditManualHost(host.manualHost)) })
					TvButton("Delete", onClick = { onAction(DeleteManualHost(host.manualHost)) })
				}
			}
		}
	}
}

@Composable
private fun AddCard(text: String, icon: ImageVector, onClick: () -> Unit)
{
	TvFocusCard(onClick = onClick, containerColor = SurfaceDark) {
		Column(
			modifier = Modifier
				.width(260.dp)
				.height(170.dp)
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Icon(icon, null, tint = PsBlueLight, modifier = Modifier.size(44.dp))
			Spacer(Modifier.height(8.dp))
			Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
		}
	}
}
