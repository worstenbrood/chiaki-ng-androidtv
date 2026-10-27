// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import com.chiaking.tv.ui.components.RowSummary
import com.chiaking.tv.ui.components.RowTitle
import com.chiaking.tv.ui.components.tvRow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted

/**
 * Focusable settings row that navigates to another screen/dialog on click.
 */
@Composable
fun TvActionRow(
	title: String,
	summary: String?,
	onClick: () -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (Boolean) -> Unit = {},
	leadingContent: (@Composable () -> Unit)? = null
)
{
	val focused = remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.tvRow(focused, onClick, focusRequester, onFocus),
		verticalAlignment = Alignment.CenterVertically
	) {
		leadingContent?.invoke()
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
