// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.HudChipFocus
import com.chiaking.tv.ui.theme.HudChipShape
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.OnVideo
import com.chiaking.tv.ui.theme.OutlineSubtle
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.PsHudLabelStyle

@Composable
internal fun HudItem(
	label: String,
	value: String,
	sub: String,
	onClick: (() -> Unit)? = null,
	onVertical: ((Int) -> Unit)? = null,
	icon: ImageVector? = null,
	focusReq: FocusRequester? = null,
	selected: Boolean = false,
	grabFocus: Boolean = false,
	onFocusGrabbed: () -> Unit = {},
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
			.then(if(grabFocus && focusReq != null) Modifier.onGloballyPositioned { onFocusGrabbed(); focusReq.requestFocus() } else Modifier)
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
			style = PsHudLabelStyle(),
			color = PsBlueLight
		)
		Spacer(Modifier.height(2.dp))
		if(icon != null)
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier.height(40.dp)
			) {
				Icon(
					icon,
					contentDescription = label,
					tint = if(selected) OnVideo else OnSurfaceMuted,
					modifier = Modifier.size(22.dp)
				)
			}
		else
		{
			Text(value, style = MaterialTheme.typography.titleSmall, color = OnVideo)
			if(sub.isNotEmpty())
				Text(
					sub,
					style = MaterialTheme.typography.labelMedium,
					color = OnSurfaceMuted,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.widthIn(max = 160.dp)
				)
		}
	}
}

@Composable
internal fun HudDivider()
{
	Box(
		Modifier
			.width(1.dp)
			.height(44.dp)
			.background(OutlineSubtle)
	)
}
