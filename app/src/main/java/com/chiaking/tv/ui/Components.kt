// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.ui.theme.*

@Composable
fun TvFocusCard(
	focusRequester: FocusRequester? = null,
	onClick: (() -> Unit)? = null,
	content: @Composable BoxScope.() -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.05f else 1.0f, label = "focusScale")

	Box(
		modifier = Modifier
			.scale(scale)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.then(
				if(onClick != null) Modifier.clickable { onClick() } else Modifier.focusable()
			)
			.onFocusChanged { focused = it.isFocused }
			.border(
				BorderStroke(
					if(focused) 3.dp else 1.dp,
					if(focused) PsBlueLight else Color.White.copy(alpha = 0.12f)
				),
				CardShape
			)
	) {
		content()
	}
}

@Composable
fun TvButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester? = null,
	colors: ButtonColors = ButtonDefaults.buttonColors(
		containerColor = SurfaceCard,
		contentColor = OnSurfaceLight
	)
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.06f else 1.0f, label = "btnScale")

	Button(
		onClick = onClick,
		modifier = modifier
			.scale(scale)
			.onFocusChanged { focused = it.isFocused },
		colors = colors,
		shape = ButtonShape,
		border = BorderStroke(
			if(focused) 2.dp else 1.dp,
			if(focused) PsBlueLight else Color.White.copy(alpha = 0.15f)
		)
	) {
		Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
	}
}

@Composable
fun TvSwitchRow(
	title: String,
	summary: String?,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.onFocusChanged { focused = it.isFocused }
			.focusable()
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
			if(summary != null)
				Text(summary, fontSize = 13.sp, color = OnSurfaceMuted)
		}
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
			colors = SwitchDefaults.colors(
				checkedTrackColor = PsBlue,
				uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
			)
		)
	}
}

@Composable
fun TvActionRow(
	title: String,
	summary: String?,
	onClick: () -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.onFocusChanged { focused = it.isFocused }
			.focusable()
			.padding(horizontal = 20.dp, vertical = 14.dp),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
			if(summary != null)
				Text(summary, fontSize = 13.sp, color = OnSurfaceMuted)
		}
		Text(">", color = if(focused) PsBlueLight else OnSurfaceMuted, fontSize = 20.sp)
	}
}

@Composable
fun SectionHeader(text: String)
{
	Text(
		text.uppercase(),
		fontSize = 13.sp,
		fontWeight = FontWeight.Bold,
		color = PsBlueLight,
		letterSpacing = 2.sp,
		modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
	)
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit)
{
	Card(
		colors = CardDefaults.cardColors(containerColor = SurfaceCard),
		shape = CardShape,
		modifier = Modifier.padding(horizontal = 16.dp)
	) {
		Column(content = content)
	}
}
