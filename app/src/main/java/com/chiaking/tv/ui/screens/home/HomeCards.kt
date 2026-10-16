// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.chiaking.tv.common.DiscoveredDisplayHost
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.common.ManualDisplayHost
import com.chiaking.tv.ui.components.buttons.TvButton
import com.chiaking.tv.ui.components.TvFocusCard
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.Ps4Blue
import com.chiaking.tv.ui.theme.Ps5Blue
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.SurfaceCard
import com.chiaking.tv.ui.theme.SurfaceDark
import com.chiaking.tv.ui.theme.SuccessGreen
import com.chiaking.tv.ui.theme.WarningAmber
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.HolepunchDevice

internal val CARD_WIDTH = 220.dp
internal val CARD_HEIGHT = 164.dp

/** Shared frame for the home-screen console/add tiles. */
@Composable
private fun ConsoleCardShell(
	focusRequester: FocusRequester?,
	containerColor: Color = SurfaceCard,
	onFocus: (Boolean) -> Unit,
	onClick: () -> Unit,
	content: @Composable ColumnScope.() -> Unit
)
{
	TvFocusCard(
		focusRequester = focusRequester,
		containerColor = containerColor,
		onFocusChange = onFocus,
		onClick = onClick
	) {
		Column(
			modifier = Modifier
				.width(CARD_WIDTH)
				.height(CARD_HEIGHT)
				.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			content()
		}
	}
}

/** Colored status dot + label + PS4/PS5 tag. */
@Composable
private fun StatusLine(stateColor: Color, stateText: String, isPS5: Boolean)
{
	Row(verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(8.dp).background(stateColor, CircleShape))
		Spacer(Modifier.width(6.dp))
		Text(stateText, fontSize = 12.sp, color = stateColor)
		Spacer(Modifier.width(8.dp))
		Text(stringResource(if(isPS5) R.string.ps5 else R.string.ps4), fontSize = 12.sp, color = OnSurfaceMuted)
	}
}

@Composable
internal fun ConsoleCard(
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

	ConsoleCardShell(
		focusRequester = focusRequester,
		onFocus = onFocus,
		onClick = {
			if(host.isRegistered)
			{
				if(standby) onAction(WakeupHost(host)) else onAction(ConnectHost(host))
			}
			else
				onAction(RegisterHost(host.host, host.isPS5))
		}
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
		StatusLine(stateColor, stateText, host.isPS5)
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

@Composable
internal fun RemoteConsoleCard(
	device: HolepunchDevice,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit,
	onAction: (HomeAction) -> Unit
)
{
	ConsoleCardShell(
		focusRequester = focusRequester,
		onFocus = onFocus,
		onClick = { onAction(ConnectRemoteConsole(device)) }
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
		StatusLine(SuccessGreen, stringResource(R.string.ready), device.isPS5)
	}
}

@Composable
internal fun AddCard(
	text: String,
	icon: ImageVector,
	sub: String? = null,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {},
	onClick: () -> Unit
)
{
	ConsoleCardShell(
		focusRequester = focusRequester,
		containerColor = SurfaceDark,
		onFocus = onFocus,
		onClick = onClick
	) {
		Icon(icon, null, tint = PsCyan, modifier = Modifier.size(40.dp))
		Spacer(Modifier.height(8.dp))
		Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
		if(sub != null)
			Text(sub, fontSize = 13.sp, color = if(sub.contains("-1")) WarningAmber else OnSurfaceMuted)
	}
}
