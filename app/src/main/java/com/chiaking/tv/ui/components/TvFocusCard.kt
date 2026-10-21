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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chiaking.tv.ui.theme.CardIdleBorder
import com.chiaking.tv.ui.theme.CardShape
import com.chiaking.tv.ui.theme.FocusGlowCard
import com.chiaking.tv.ui.theme.FocusScaleCardX
import com.chiaking.tv.ui.theme.FocusScaleCardY
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.PsFocusWhite
import com.chiaking.tv.ui.theme.SurfaceCard

@Composable
fun TvFocusCard(
	focusRequester: FocusRequester? = null,
	containerColor: Color = SurfaceCard,
	focusScaleX: Float = FocusScaleCardX,
	focusScaleY: Float = FocusScaleCardY,
	onClick: (() -> Unit)? = null,
	onFocusChange: (Boolean) -> Unit = {},
	content: @Composable BoxScope.() -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	val scaleX by animateFloatAsState(if(focused) focusScaleX else 1.0f, label = "focusScaleX")
	val scaleY by animateFloatAsState(if(focused) focusScaleY else 1.0f, label = "focusScaleY")
	val glow by animateDpAsState(if(focused) FocusGlowCard else 0.dp, label = "focusGlow")

	Box(
		modifier = Modifier
			// focus observer must come first in the chain; placed after clip/clickable it
			// never observes focus and the scale silently stays at 1.0
			.onFocusEvent {
				focused = it.hasFocus
				onFocusChange(it.hasFocus)
			}
			.scale(scaleX, scaleY)
			.zIndex(if(focused) 1f else 0f)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.shadow(glow, CardShape, clip = false, ambientColor = PsCyan, spotColor = PsCyan)
			.clip(CardShape)
			.then(
				if(onClick != null) Modifier.clickable { onClick() } else Modifier.focusable()
			)
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
