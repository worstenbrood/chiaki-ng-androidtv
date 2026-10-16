// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction

/**
 * Numeric PIN input (max 8 digits) with a Done action; shared by the login-PIN dialogs
 * (stream, per-console and global settings).
 */
@Composable
fun PinTextField(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	onDone: () -> Unit
)
{
	TvTextField(
		label,
		value,
		imeAction = ImeAction.Done,
		keyboardActions = KeyboardActions(onDone = { onDone() })
	) {
		if(it.all { c -> c.isDigit() } && it.length <= 8) onValueChange(it)
	}
}
