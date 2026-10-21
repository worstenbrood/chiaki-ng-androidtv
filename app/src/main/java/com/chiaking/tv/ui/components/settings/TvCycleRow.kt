// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import com.chiaking.tv.ui.components.ValueChip
import com.chiaking.tv.ui.components.tvRow

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

import androidx.compose.ui.unit.dp

import com.chiaking.tv.ui.theme.OnSurfaceLight

/**
 * Focusable settings row that cycles through values on click.
 */
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
			style = MaterialTheme.typography.titleLarge,
			color = OnSurfaceLight,
			modifier = Modifier.weight(1f)
		)
		Spacer(Modifier.width(12.dp))
		ValueChip(value, focused.value)
	}
}
