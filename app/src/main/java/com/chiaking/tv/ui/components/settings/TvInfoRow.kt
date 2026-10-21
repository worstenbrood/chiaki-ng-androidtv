// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.OnSurfaceMuted

/** Non-focusable title/value row (read-only info). */
@Composable
fun TvInfoRow(title: String, value: String)
{
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 22.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(12.dp))
		Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
	}
}
