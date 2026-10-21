// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R

// Inter is an open (OFL) geometric sans, close in spirit to Sony's SST.
val PsFontFamily = FontFamily(
	Font(R.font.inter_light, FontWeight.Light),
	Font(R.font.inter_regular, FontWeight.Normal),
	Font(R.font.inter_medium, FontWeight.Medium),
	Font(R.font.inter_semi_bold, FontWeight.SemiBold),
	Font(R.font.inter_bold, FontWeight.Bold)
)

// All text styles live here; components reference MaterialTheme.typography.* instead of
// hardcoding sizes/weights. A theme manager only has to swap this Typography instance.
// Roles: display* = screen headers, headline* = big feature text (PIN, spinners),
// title* = row/nav titles + HUD values, body* = buttons + sublines, label* = small meta text.
val ChiakiTvTypography = Typography(
	displayLarge = TextStyle(fontFamily = PsFontFamily, fontSize = 30.sp, fontWeight = FontWeight.Bold),
	displayMedium = TextStyle(fontFamily = PsFontFamily, fontSize = 30.sp, fontWeight = FontWeight.Light),
	displaySmall = TextStyle(fontFamily = PsFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Light),
	headlineLarge = TextStyle(fontFamily = PsFontFamily, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
	headlineMedium = TextStyle(fontFamily = PsFontFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp),
	headlineSmall = TextStyle(fontFamily = PsFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold),
	titleLarge = TextStyle(fontFamily = PsFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Medium),
	titleMedium = TextStyle(fontFamily = PsFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
	titleSmall = TextStyle(fontFamily = PsFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold),
	bodyLarge = TextStyle(fontFamily = PsFontFamily, fontSize = 16.sp),
	bodyMedium = TextStyle(fontFamily = PsFontFamily, fontSize = 15.sp),
	bodySmall = TextStyle(fontFamily = PsFontFamily, fontSize = 13.sp),
	labelLarge = TextStyle(fontFamily = PsFontFamily, fontSize = 14.sp),
	labelMedium = TextStyle(fontFamily = PsFontFamily, fontSize = 12.sp),
	labelSmall = TextStyle(fontFamily = PsFontFamily, fontSize = 11.sp)
)

// Derived styles for the two roles that need tracking beyond the 15 Material slots;
// they copy from the swapped typography so a theme change propagates.
@Composable
fun PsSectionHeaderStyle(): TextStyle =
	MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp)

@Composable
fun PsHudLabelStyle(): TextStyle =
	MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
