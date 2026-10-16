// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.CardShape
import com.chiaking.tv.ui.theme.SurfaceCard

@Composable
fun SettingsCard(containerColor: Color = SurfaceCard, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)
{
	Card(
		colors = CardDefaults.cardColors(containerColor = containerColor),
		shape = CardShape,
		modifier = Modifier.padding(horizontal = 16.dp)
	) {
		Column(content = content)
	}
}
