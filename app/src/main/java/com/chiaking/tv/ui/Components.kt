// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.chiaking.tv.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.ui.theme.*

@Composable
fun TvFocusCard(
	focusRequester: FocusRequester? = null,
	containerColor: Color = SurfaceCard,
	enabled: Boolean = true,
	onClick: (() -> Unit)? = null,
	onFocusChange: (Boolean) -> Unit = {},
	content: @Composable BoxScope.() -> Unit
)
{
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if(focused) 1.05f else 1.0f, label = "focusScale")

	Box(
		modifier = Modifier
			.scale(scale)
			.then(if(!enabled) Modifier.alpha(0.4f) else Modifier)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.clip(CardShape)
			.then(
				if(onClick != null && enabled) Modifier.clickable { onClick() } else Modifier.focusable()
			)
			.onFocusChanged {
				focused = it.isFocused
				onFocusChange(it.isFocused)
			}
			.background(containerColor)
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
	onFocusChange: (Boolean) -> Unit = {},
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
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.onFocusChanged {
				focused = it.isFocused
				onFocusChange(it.isFocused)
			},
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
fun ScreenHeader(
	title: String,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
	fontSize: androidx.compose.ui.unit.TextUnit = 30.sp,
	backFocusRequester: FocusRequester? = null,
	onBackFocus: (Boolean) -> Unit = {}
)
{
	Row(modifier = modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
		TvButton(
			stringResource(R.string.back),
			onClick = onBack,
			focusRequester = backFocusRequester,
			onFocusChange = onBackFocus
		)
		Spacer(Modifier.width(20.dp))
		Text(title, fontSize = fontSize, fontWeight = FontWeight.Bold)
	}
}

@Composable
private fun Modifier.tvRow(
	focused: MutableState<Boolean>,
	onClick: () -> Unit,
	focusRequester: FocusRequester?,
	onFocus: (Boolean) -> Unit
): Modifier =
	this
		.padding(vertical = 2.dp)
		.then(
			if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
		)
		.clip(CardShape)
		.background(if(focused.value) PsBlue.copy(alpha = 0.40f) else Color.Transparent)
		.onFocusChanged {
			focused.value = it.isFocused
			onFocus(it.isFocused)
		}
		.clickable(onClick = onClick)
		.padding(horizontal = 20.dp, vertical = 12.dp)

@Composable
fun TvSwitchRow(
	title: String,
	summary: String?,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {}
)
{
	val focused = remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.tvRow(focused, { onCheckedChange(!checked) }, focusRequester, onFocus),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
			if(summary != null)
				Text(summary, fontSize = 13.sp, color = OnSurfaceMuted)
		}
		Spacer(Modifier.width(12.dp))
		Box(
			modifier = Modifier
				.clip(RoundedCornerShape(10.dp))
				.background(if(checked) PsBlue else Color.White.copy(alpha = 0.12f))
				.padding(horizontal = 14.dp, vertical = 5.dp)
		) {
			Text(
				stringResource(if(checked) R.string.on else R.string.off),
				fontSize = 14.sp,
				fontWeight = FontWeight.Bold,
				color = if(checked) Color.White else OnSurfaceMuted
			)
		}
	}
}

@Composable
fun TvCycleRow(
	title: String,
	value: String,
	onNext: () -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {}
)
{
	val focused = remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.tvRow(focused, onNext, focusRequester, onFocus),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(12.dp))
		Box(
			modifier = Modifier
				.clip(RoundedCornerShape(10.dp))
				.background(if(focused.value) PsBlueLight else PsBlue.copy(alpha = 0.30f))
				.padding(horizontal = 14.dp, vertical = 5.dp)
		) {
			Text(
				value,
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				color = if(focused.value) Color(0xFF0B0E17) else PsBlueLight
			)
		}
	}
}

@Composable
fun TvStepperRow(
	title: String,
	value: String,
	summary: String?,
	onStep: (Int) -> Unit,
	onReset: () -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {}
)
{
	val focused = remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.onKeyEvent { ev ->
				if(!focused.value)
					return@onKeyEvent false
				val mult = if(ev.nativeKeyEvent.repeatCount >= 8) 5 else 1
				when {
					ev.key == Key.DirectionLeft && ev.type == KeyEventType.KeyDown -> { onStep(-mult); true }
					ev.key == Key.DirectionRight && ev.type == KeyEventType.KeyDown -> { onStep(mult); true }
					else -> false
				}
			}
			.tvRow(focused, onReset, focusRequester, onFocus),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
			if(summary != null)
				Text(summary, fontSize = 13.sp, color = OnSurfaceMuted)
		}
		Spacer(Modifier.width(12.dp))
		Box(
			modifier = Modifier
				.clip(RoundedCornerShape(10.dp))
				.background(if(focused.value) PsBlueLight else PsBlue.copy(alpha = 0.30f))
				.padding(horizontal = 14.dp, vertical = 5.dp)
		) {
			Text(
				value,
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				color = if(focused.value) Color(0xFF0B0E17) else PsBlueLight
			)
		}
	}
}

@Composable
fun TvActionRow(
	title: String,
	summary: String?,
	onClick: () -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {}
)
{
	val focused = remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.tvRow(focused, onClick, focusRequester, onFocus),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
			if(summary != null)
				Text(summary, fontSize = 13.sp, color = OnSurfaceMuted)
		}
		Text(">", color = if(focused.value) Color.White else OnSurfaceMuted, fontSize = 20.sp)
	}
}

@Composable
fun TvInfoRow(title: String, value: String)
{
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(12.dp))
		Text(value, fontSize = 15.sp, color = OnSurfaceMuted)
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
