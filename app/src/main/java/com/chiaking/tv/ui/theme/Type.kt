// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chiaking.tv.R

// Inter is an open (OFL) geometric sans, close in spirit to Sony's SST.
val PsFontFamily = FontFamily(
	Font(R.font.inter_light, FontWeight.Light),
	Font(R.font.inter_regular, FontWeight.Normal),
	Font(R.font.inter_medium, FontWeight.Medium),
	Font(R.font.inter_semi_bold, FontWeight.SemiBold),
	Font(R.font.inter_bold, FontWeight.Bold)
)

private fun styled(styles: List<TextStyle>): List<TextStyle> =
	styles.map { it.copy(fontFamily = PsFontFamily) }

val ChiakiTvTypography: Typography = run {
	val base = Typography()
	val all = styled(
		listOf(
			base.displayLarge, base.displayMedium, base.displaySmall,
			base.headlineLarge, base.headlineMedium, base.headlineSmall,
			base.titleLarge, base.titleMedium, base.titleSmall,
			base.bodyLarge, base.bodyMedium, base.bodySmall,
			base.labelLarge, base.labelMedium, base.labelSmall
		)
	)
	Typography(
		displayLarge = all[0], displayMedium = all[1], displaySmall = all[2],
		headlineLarge = all[3], headlineMedium = all[4], headlineSmall = all[5],
		titleLarge = all[6], titleMedium = all[7], titleSmall = all[8],
		bodyLarge = all[9], bodyMedium = all[10], bodySmall = all[11],
		labelLarge = all[12], labelMedium = all[13], labelSmall = all[14]
	)
}
