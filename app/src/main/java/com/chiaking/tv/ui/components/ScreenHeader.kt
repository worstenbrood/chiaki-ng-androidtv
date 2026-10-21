// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components

import com.chiaking.tv.ui.components.buttons.TvButton

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R

@Composable
fun ScreenHeader(
	title: String,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
	style: TextStyle = MaterialTheme.typography.displayMedium,
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
		Text(title, style = style)
	}
}
