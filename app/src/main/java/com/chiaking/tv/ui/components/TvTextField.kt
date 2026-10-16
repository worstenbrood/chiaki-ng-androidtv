// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.PsBlueLight

@Composable
fun TvTextField(
	label: String,
	value: String,
	enabled: Boolean = true,
	imeAction: ImeAction = ImeAction.Default,
	keyboardActions: KeyboardActions = KeyboardActions.Default,
	textStyle: TextStyle = TextStyle.Default,
	onValueChange: (String) -> Unit
)
{
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		enabled = enabled,
		label = { Text(label) },
		textStyle = textStyle,
		keyboardOptions = KeyboardOptions(imeAction = imeAction),
		keyboardActions = keyboardActions,
		singleLine = true,
		colors = OutlinedTextFieldDefaults.colors(
			focusedBorderColor = PsBlueLight,
			focusedLabelColor = PsBlueLight
		),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
	)
}
