// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val PsBlue = Color(0xFF0070D1)
val PsBlueLight = Color(0xFF4FA3FF)
val PsCyan = Color(0xFF00C2FF)
val TvBackground = Color(0xFF060B14)
val SurfaceDark = Color(0xFF0E1524)
val SurfaceCard = Color(0xFF161E31)
val SurfaceFrosted = Color(0xE60E182C)
val DialogSurface = Color(0xF2101A2E)
val OnSurfaceLight = Color(0xFFF0F2F8)
val OnSurfaceMuted = Color(0xFF8A93AB)
val SuccessGreen = Color(0xFF2ECC71)
val WarningAmber = Color(0xFFF5A623)
val ErrorRed = Color(0xFFE74C3C)
val Ps5Blue = Color(0xFF2E6FF2)
val Ps4Blue = Color(0xFF0070D1)
val PsFocusWhite = Color(0xEEEEEEEE)
val PsFocusText = Color(0xFF060B14)

private val ChiakiTvColorScheme = darkColorScheme(
	primary = PsBlue,
	onPrimary = Color.White,
	secondary = PsCyan,
	onSecondary = Color.Black,
	background = TvBackground,
	onBackground = OnSurfaceLight,
	surface = SurfaceDark,
	onSurface = OnSurfaceLight,
	surfaceVariant = SurfaceCard,
	onSurfaceVariant = OnSurfaceMuted,
	error = ErrorRed,
	onError = Color.White
)

val CardShape = RoundedCornerShape(16.dp)
val SelectionShape = RoundedCornerShape(10.dp)
val ButtonShape = CircleShape
val DialogShape = RoundedCornerShape(20.dp)

@Composable
fun ChiakiTvTheme(content: @Composable () -> Unit)
{
	MaterialTheme(
		colorScheme = ChiakiTvColorScheme,
		typography = ChiakiTvTypography,
		content = content
	)
}
