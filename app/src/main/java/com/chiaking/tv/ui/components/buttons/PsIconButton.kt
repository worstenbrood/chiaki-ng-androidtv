// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.buttons

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.PsFocusText
import com.chiaking.tv.ui.theme.PsFocusWhite
import com.chiaking.tv.ui.theme.SelectionIdle

@Composable
fun PsIconButton(
	icon: ImageVector,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester? = null,
	onFocusChange: (Boolean) -> Unit = {}
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.12f else 1.0f, label = "iconBtnScale")
	val glow by animateDpAsState(if(focused) 10.dp else 0.dp, label = "iconBtnGlow")

	Box(
		modifier = modifier
			// focus observer must come first; after clip/clickable it never observes focus
			.onFocusEvent {
				focused = it.hasFocus
				onFocusChange(it.hasFocus)
			}
			.scale(scale)
			.shadow(glow, CircleShape, clip = false, ambientColor = PsCyan, spotColor = PsCyan)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.clip(CircleShape)
			.clickable { onClick() }
			.background(if(focused) PsFocusWhite else SelectionIdle)
			.padding(10.dp),
		contentAlignment = Alignment.Center
	) {
		Icon(
			icon,
			contentDescription,
			tint = if(focused) PsFocusText else OnSurfaceLight,
			modifier = Modifier.size(22.dp)
		)
	}
}
