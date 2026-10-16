// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.buttons

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.ui.theme.ButtonShape
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OutlineSubtle
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.PsFocusText
import com.chiaking.tv.ui.theme.PsFocusWhite
import com.chiaking.tv.ui.theme.SurfaceCard

@Composable
fun TvButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester? = null,
	onFocusChange: (Boolean) -> Unit = {},
	colors: ButtonColors = ButtonDefaults.buttonColors(
		containerColor = SurfaceCard,
		contentColor = OnSurfaceLight
	)
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.06f else 1.0f, label = "btnScale")
	val glow by animateDpAsState(if(focused) 16.dp else 0.dp, label = "btnGlow")

	Button(
		onClick = onClick,
		modifier = modifier
			.scale(scale)
			.shadow(glow, ButtonShape, clip = false, ambientColor = PsCyan, spotColor = PsCyan)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.onFocusChanged {
				focused = it.isFocused
				onFocusChange(it.isFocused)
			},
		colors = if(focused)
			ButtonDefaults.buttonColors(containerColor = PsFocusWhite, contentColor = PsFocusText)
		else
			colors,
		shape = ButtonShape,
		border = if(focused) null else BorderStroke(1.dp, OutlineSubtle)
	) {
		Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
	}
}
