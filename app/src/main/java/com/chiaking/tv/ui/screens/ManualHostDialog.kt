// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.components.TvTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R

@Composable
fun ManualHostDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit)
{
	var value by remember { mutableStateOf(initial) }
	PsAlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(if(initial.isEmpty()) R.string.manual_host_add else R.string.manual_host_edit)) },
		text = {
			TvTextField(stringResource(R.string.host_address_ip), value) { value = it }
		},
		confirmButton = {
			TextButton(
				onClick = { if(value.isNotBlank()) onSave(value.trim()) }
			) { Text(stringResource(R.string.save)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
		}
	)
}
