// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.SelectionFocused
import com.chiaking.tv.ui.theme.SelectionIdle
import com.chiaking.tv.ui.theme.SelectionShape

/** Left-pane category entry of the settings screen. */
@Composable
internal fun SettingsNavItem(
	label: String,
	icon: ImageVector,
	selected: Boolean,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit,
	onClick: () -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.clip(SelectionShape)
			.background(
				when {
					focused -> SelectionFocused
					selected -> SelectionIdle
					else -> Color.Transparent
				}
			)
			.onFocusChanged {
				focused = it.isFocused
				onFocus(it.isFocused)
			}
			.clickable { onClick() }
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			icon,
			null,
			tint = when {
				focused -> PsCyan
				selected -> PsCyan
				else -> OnSurfaceMuted
			},
			modifier = Modifier.size(20.dp)
		)
		Spacer(Modifier.width(14.dp))
		Text(
			label,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Medium,
			color = when {
				focused -> OnSurfaceLight
				selected -> OnSurfaceLight
				else -> OnSurfaceMuted
			}
		)
	}
}
