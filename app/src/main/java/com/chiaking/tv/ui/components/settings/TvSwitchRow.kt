// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import com.chiaking.tv.ui.components.OnOffChip
import com.chiaking.tv.ui.components.RowSummary
import com.chiaking.tv.ui.components.RowTitle
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
import androidx.compose.ui.unit.dp

/**
 * Focusable settings row with an on/off toggle: tapping the row flips the value.
 */
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
		OnOffChip(checked, focused.value)
	}
}
