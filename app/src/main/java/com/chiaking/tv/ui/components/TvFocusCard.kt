// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.CardIdleBorder
import com.chiaking.tv.ui.theme.CardShape
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.PsFocusWhite
import com.chiaking.tv.ui.theme.SurfaceCard

@Composable
fun TvFocusCard(
	focusRequester: FocusRequester? = null,
	containerColor: Color = SurfaceCard,
	onClick: (() -> Unit)? = null,
	onFocusChange: (Boolean) -> Unit = {},
	content: @Composable BoxScope.() -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.08f else 1.0f, label = "focusScale")
	val glow by animateDpAsState(if(focused) 24.dp else 0.dp, label = "focusGlow")

	Box(
		modifier = Modifier
			.scale(scale)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.shadow(glow, CardShape, clip = false, ambientColor = PsCyan, spotColor = PsCyan)
			.clip(CardShape)
			.then(
				if(onClick != null) Modifier.clickable { onClick() } else Modifier.focusable()
			)
			.onFocusChanged {
				focused = it.isFocused
				onFocusChange(it.isFocused)
			}
			.background(containerColor)
			.border(
				BorderStroke(
					if(focused) 2.dp else 1.dp,
					if(focused) PsFocusWhite else CardIdleBorder
				),
				CardShape
			)
	) {
		content()
	}
}
