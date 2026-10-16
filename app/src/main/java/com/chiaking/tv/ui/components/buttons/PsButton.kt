// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.buttons

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
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
import com.chiaking.tv.ui.theme.ButtonIdle
import com.chiaking.tv.ui.theme.ButtonShape
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OutlineMedium
import com.chiaking.tv.ui.theme.PsCyan
import com.chiaking.tv.ui.theme.PsFocusText
import com.chiaking.tv.ui.theme.PsFocusWhite

/**
 * PS5 pill button: primary = solid white pill (Play-style), secondary = translucent.
 * Both flip to white pill + dark text when focused.
 */
@Composable
fun PsButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	primary: Boolean = true,
	focusRequester: FocusRequester? = null,
	onFocusChange: (Boolean) -> Unit = {}
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.06f else 1.0f, label = "psBtnScale")
	val glow by animateDpAsState(if(focused) 18.dp else 0.dp, label = "psBtnGlow")
	val container = when {
		focused -> PsFocusWhite
		primary -> PsFocusWhite
		else -> ButtonIdle
	}
	val content = when {
		focused -> PsFocusText
		primary -> PsFocusText
		else -> OnSurfaceLight
	}

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
		colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
		shape = ButtonShape,
		border = if(primary || focused) null else BorderStroke(1.dp, OutlineMedium)
	) {
		Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
	}
}
