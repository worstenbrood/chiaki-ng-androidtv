// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import com.chiaking.tv.ui.components.RowSummary
import com.chiaking.tv.ui.components.RowTitle
import com.chiaking.tv.ui.components.ValueChip
import com.chiaking.tv.ui.components.tvRow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * Focusable settings row with a numeric value: Left/Right step (accelerated on hold),
 * cross resets to the default.
 */
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
		ValueChip(value, focused.value)
	}
}
