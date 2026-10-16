// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.SelectionFocused
import com.chiaking.tv.ui.theme.SelectionShape

/** Shared focus/selection styling for a settings row. */
@Composable
internal fun Modifier.tvRow(
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
		.background(if(focused.value) SelectionFocused else Color.Transparent)
		.onFocusChanged {
			focused.value = it.isFocused
			onFocus(it.isFocused)
		}
		.clickable(onClick = onClick)
		.padding(horizontal = 22.dp, vertical = 12.dp)

@Composable
internal fun RowTitle(title: String)
{
	Text(
		title,
		fontSize = 18.sp,
		fontWeight = FontWeight.Medium,
		color = OnSurfaceLight
	)
}

@Composable
internal fun RowSummary(summary: String)
{
	Text(
		summary,
		fontSize = 13.sp,
		color = OnSurfaceMuted
	)
}
