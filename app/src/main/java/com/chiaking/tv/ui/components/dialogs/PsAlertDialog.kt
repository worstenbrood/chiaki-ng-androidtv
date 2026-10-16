// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.DialogShape
import com.chiaking.tv.ui.theme.DialogSurface
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted

/**
 * PS5-styled alert dialog: frosted dark panel, rounded corners.
 */
@Composable
fun PsAlertDialog(
	onDismissRequest: () -> Unit,
	title: @Composable () -> Unit,
	text: @Composable () -> Unit,
	confirmButton: @Composable () -> Unit,
	dismissButton: @Composable (() -> Unit)? = null
)
{
	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = title,
		text = text,
		confirmButton = confirmButton,
		dismissButton = dismissButton,
		containerColor = DialogSurface,
		tonalElevation = 0.dp,
		shape = DialogShape,
		titleContentColor = OnSurfaceLight,
		textContentColor = OnSurfaceMuted
	)
}
