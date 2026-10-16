// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.ui.theme.DialogShape
import com.chiaking.tv.ui.theme.DialogSurface
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.PsCyan

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
