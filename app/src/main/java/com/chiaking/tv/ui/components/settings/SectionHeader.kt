// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.PsSectionHeaderStyle

@Composable
fun SectionHeader(text: String, centered: Boolean = false)
{
	Text(
		text.uppercase(),
		style = PsSectionHeaderStyle(),
		color = OnSurfaceLight,
		textAlign = if(centered) TextAlign.Center else TextAlign.Start,
		modifier = if(centered) Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)
			else Modifier.padding(start = 22.dp, top = 24.dp, bottom = 8.dp)
	)
}
