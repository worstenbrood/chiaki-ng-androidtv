// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.dialogs

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.common.settings.SettingsTransfer

/**
 * Replace/Merge prompt shown while a settings import is pending on [SettingsTransfer].
 */
@Composable
fun ImportSettingsDialog(settingsTransfer: SettingsTransfer)
{
	settingsTransfer.pendingImport?.let {
		PsAlertDialog(
			onDismissRequest = { settingsTransfer.dismissImport() },
			title = { Text(stringResource(R.string.import_settings)) },
			text = { Text(stringResource(R.string.import_replace_merge_question)) },
			confirmButton = {
				TextButton(onClick = { settingsTransfer.confirmImport(true) })
				{ Text(stringResource(R.string.replace)) }
			},
			dismissButton = {
				TextButton(onClick = { settingsTransfer.confirmImport(false) })
				{ Text(stringResource(R.string.merge)) }
			}
		)
	}
}
