// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.chiaking.tv.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.ui.theme.*

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
					if(focused) PsFocusWhite else Color.White.copy(alpha = 0.10f)
				),
				CardShape
			)
	) {
		content()
	}
}

/**
 * Modal progress dialog with a spinner, status text and a Cancel button.
 * Back dismisses via [onCancel] (the dialog's own BackHandler).
 */
@Composable
fun TvProgressDialog(text: String, onCancel: () -> Unit)
{
	AlertDialog(
		onDismissRequest = onCancel,
		containerColor = DialogSurface,
		tonalElevation = 0.dp,
		shape = DialogShape,
		textContentColor = OnSurfaceMuted,
		text = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(16.dp)
			) {
				CircularProgressIndicator(color = PsCyan, modifier = Modifier.size(28.dp))
				Text(text, color = OnSurfaceMuted)
			}
		},
		confirmButton = {
			TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = PsBlueLight) }
		}
	)
}

/**
 * PS5-styled alert dialog: frosted dark panel, rounded corners.
 */
@Composable
fun PsAlertDialog(
	onDismissRequest: () -> Unit,
	title: @Composable () -> Unit,
	text: @Composable () -> Unit,
	confirmButton: @Composable () -> Unit,
	dismissButton: @Composable (() -> Unit)? = null
)
{
	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = title,
		text = text,
		confirmButton = confirmButton,
		dismissButton = dismissButton,
		containerColor = DialogSurface,
		tonalElevation = 0.dp,
		shape = DialogShape,
		titleContentColor = OnSurfaceLight,
		textContentColor = OnSurfaceMuted
	)
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
		border = if(focused) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
	) {
		Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
	}
}

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
		else -> Color.White.copy(alpha = 0.12f)
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
		border = if(primary || focused) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
	) {
		Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
	}
}

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
	val glow by animateDpAsState(if(focused) 14.dp else 0.dp, label = "iconBtnGlow")

	Box(
		modifier = modifier
			.scale(scale)
			.shadow(glow, CircleShape, clip = false, ambientColor = PsCyan, spotColor = PsCyan)
			.then(
				if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
			)
			.clip(CircleShape)
			.clickable { onClick() }
			.onFocusChanged {
				focused = it.isFocused
				onFocusChange(it.isFocused)
			}
			.background(if(focused) PsFocusWhite else Color.White.copy(alpha = 0.08f))
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
	Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
		TvButton(
			stringResource(R.string.back),
			onClick = onBack,
			focusRequester = backFocusRequester,
			onFocusChange = onBackFocus
		)
		Spacer(Modifier.width(20.dp))
		Text(title, fontSize = fontSize, fontWeight = FontWeight.Light)
	}
}

/**
 * Focusable settings row: focused row becomes a white pill with dark text (PS5 style).
 */
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
		.clip(SelectionShape)
		.background(if(focused.value) Color.White.copy(alpha = 0.14f) else Color.Transparent)
		.onFocusChanged {
			focused.value = it.isFocused
			onFocus(it.isFocused)
		}
		.clickable(onClick = onClick)
		.padding(horizontal = 22.dp, vertical = 12.dp)

@Composable
private fun RowTitle(title: String)
{
	Text(
		title,
		fontSize = 18.sp,
		fontWeight = FontWeight.Medium,
		color = OnSurfaceLight
	)
}

@Composable
private fun RowSummary(summary: String)
{
	Text(
		summary,
		fontSize = 13.sp,
		color = OnSurfaceMuted
	)
}

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
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			RowTitle(title)
			if(summary != null)
				RowSummary(summary)
		}
		Spacer(Modifier.width(12.dp))
		Box(
			modifier = Modifier
				.clip(ButtonShape)
				.background(
					when {
						checked -> PsBlue
						focused.value -> Color.White.copy(alpha = 0.25f)
						else -> Color.White.copy(alpha = 0.12f)
					}
				)
				.padding(horizontal = 14.dp, vertical = 5.dp)
		) {
			Text(
				stringResource(if(checked) R.string.on else R.string.off),
				fontSize = 14.sp,
				fontWeight = FontWeight.Bold,
				color = when {
					checked -> Color.White
					focused.value -> OnSurfaceLight
					else -> OnSurfaceMuted
				}
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
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			title,
			fontSize = 18.sp,
			fontWeight = FontWeight.Medium,
			color = OnSurfaceLight,
			modifier = Modifier.weight(1f)
		)
		Spacer(Modifier.width(12.dp))
		Box(
			modifier = Modifier
				.clip(ButtonShape)
				.background(if(focused.value) PsBlueLight else PsBlue.copy(alpha = 0.30f))
				.padding(horizontal = 14.dp, vertical = 5.dp)
		) {
			Text(
				value,
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				color = if(focused.value) PsFocusText else PsBlueLight
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
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			RowTitle(title)
			if(summary != null)
				RowSummary(summary)
		}
		Spacer(Modifier.width(12.dp))
		Box(
			modifier = Modifier
				.clip(ButtonShape)
				.background(if(focused.value) PsBlueLight else PsBlue.copy(alpha = 0.30f))
				.padding(horizontal = 14.dp, vertical = 5.dp)
		) {
			Text(
				value,
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				color = if(focused.value) PsFocusText else PsBlueLight
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
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			RowTitle(title)
			if(summary != null)
				RowSummary(summary)
		}
		Icon(
			Icons.Default.KeyboardArrowRight,
			null,
			tint = if(focused.value) OnSurfaceLight else OnSurfaceMuted,
			modifier = Modifier.size(22.dp)
		)
	}
}

@Composable
fun TvInfoRow(title: String, value: String)
{
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 22.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(12.dp))
		Text(value, fontSize = 15.sp, color = OnSurfaceMuted)
	}
}

@Composable
fun SectionHeader(text: String, centered: Boolean = false)
{
	Text(
		text.uppercase(),
		fontSize = 13.sp,
		fontWeight = FontWeight.SemiBold,
		color = OnSurfaceLight,
		letterSpacing = 2.5.sp,
		textAlign = if(centered) TextAlign.Center else TextAlign.Start,
		modifier = if(centered) Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)
			else Modifier.padding(start = 22.dp, top = 24.dp, bottom = 8.dp)
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
