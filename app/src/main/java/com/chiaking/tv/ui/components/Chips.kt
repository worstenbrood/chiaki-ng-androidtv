// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.ui.theme.ButtonIdle
import com.chiaking.tv.ui.theme.ButtonShape
import com.chiaking.tv.ui.theme.OnPrimary
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.OutlineMedium
import com.chiaking.tv.ui.theme.PsBlue
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.PsFocusText

/** On/Off pill shown at the end of a toggle row. */
@Composable
internal fun OnOffChip(enabled: Boolean, focused: Boolean)
{
	Box(
		modifier = Modifier
			.clip(ButtonShape)
			.background(
				when {
					enabled -> PsBlue
					focused -> OutlineMedium
					else -> ButtonIdle
				}
			)
			.padding(horizontal = 14.dp, vertical = 5.dp)
	) {
		Text(
			stringResource(if(enabled) R.string.on else R.string.off),
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold,
			color = when {
				enabled -> OnPrimary
				focused -> OnSurfaceLight
				else -> OnSurfaceMuted
			}
		)
	}
}

/** Blue value pill shown at the end of a cycle/stepper row. */
@Composable
internal fun ValueChip(text: String, focused: Boolean)
{
	Box(
		modifier = Modifier
			.clip(ButtonShape)
			.background(if(focused) PsBlueLight else PsBlue.copy(alpha = 0.30f))
			.padding(horizontal = 14.dp, vertical = 5.dp)
	) {
		Text(
			text,
			fontSize = 15.sp,
			fontWeight = FontWeight.Bold,
			color = if(focused) PsFocusText else PsBlueLight
		)
	}
}
