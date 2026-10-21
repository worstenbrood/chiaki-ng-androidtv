// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chiaking.tv.common.settings.Preferences.AppTheme

/**
 * The five selectable UI palettes (Settings > General > Theme). Each theme-dependent color
 * below is a @Composable getter backed by [LocalPsPalette], so switching the theme in
 * [AppThemeState] recomposes the whole tree without touching call sites.
 */
@Immutable
class PsPalette(
	val accent: Color,
	val accentLight: Color,
	val secondary: Color,
	val background: Color,
	val surface: Color,
	val surfaceCard: Color,
	val frosted: Color,
	val dialog: Color
)

private val PsBluePalette = PsPalette(
	accent = Color(0xFF0070D1), accentLight = Color(0xFF4FA3FF), secondary = Color(0xFF00C2FF),
	background = Color(0xFF060B14), surface = Color(0xFF0E1524), surfaceCard = Color(0xFF161E31),
	frosted = Color(0xE60E182C), dialog = Color(0xF2101A2E)
)
private val MidnightPalette = PsPalette(
	accent = Color(0xFF6C5CE7), accentLight = Color(0xFF9B8CFF), secondary = Color(0xFFE056FD),
	background = Color(0xFF0A0814), surface = Color(0xFF151024), surfaceCard = Color(0xFF1E1832),
	frosted = Color(0xE6161228), dialog = Color(0xF2181230)
)
private val EmeraldPalette = PsPalette(
	accent = Color(0xFF0FA872), accentLight = Color(0xFF4FE3A8), secondary = Color(0xFF00E5C0),
	background = Color(0xFF04120C), surface = Color(0xFF0C1F16), surfaceCard = Color(0xFF143024),
	frosted = Color(0xE60C2018), dialog = Color(0xF20E2419)
)
private val CrimsonPalette = PsPalette(
	accent = Color(0xFFD6304A), accentLight = Color(0xFFFF6B7F), secondary = Color(0xFFFF9F43),
	background = Color(0xFF140609), surface = Color(0xFF240E13), surfaceCard = Color(0xFF32161D),
	frosted = Color(0xE6201015), dialog = Color(0xF2261017)
)
private val AmberPalette = PsPalette(
	accent = Color(0xFFE8901E), accentLight = Color(0xFFFFB85C), secondary = Color(0xFFFF6B6B),
	background = Color(0xFF120C04), surface = Color(0xFF20180C), surfaceCard = Color(0xFF2E2414),
	frosted = Color(0xE61E160C), dialog = Color(0xF2241A0E)
)

fun AppTheme.palette(): PsPalette = when(this)
{
	AppTheme.PS_BLUE -> PsBluePalette
	AppTheme.MIDNIGHT -> MidnightPalette
	AppTheme.EMERALD -> EmeraldPalette
	AppTheme.CRIMSON -> CrimsonPalette
	AppTheme.AMBER -> AmberPalette
}

/** Process-wide theme selection; written by Settings, read by [ChiakiTvTheme]. */
object AppThemeState
{
	var theme: AppTheme by mutableStateOf(AppTheme.PS_BLUE)
}

val LocalPsPalette = staticCompositionLocalOf { PsBluePalette }

val PsBlue: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.accent
val PsBlueLight: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.accentLight
val PsCyan: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.secondary
val TvBackground: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.background
val SurfaceDark: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.surface
val SurfaceCard: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.surfaceCard
val SurfaceFrosted: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.frosted
val DialogSurface: Color
	@Composable @ReadOnlyComposable get() = LocalPsPalette.current.dialog

val OnSurfaceLight = Color(0xFFF0F2F8)
val OnSurfaceMuted = Color(0xFF8A93AB)
val SuccessGreen = Color(0xFF2ECC71)
val WarningAmber = Color(0xFFF5A623)
val ErrorRed = Color(0xFFE74C3C)
val Ps5Blue = Color(0xFF2E6FF2)
val Ps4Blue = Color(0xFF0070D1)
val PsFocusWhite = Color(0xEEEEEEEE)
val PsFocusText = Color(0xFF060B14)
val OnPrimary = Color.White
val OnVideo = Color.White
val VideoBlack = Color.Black

// Translucent white overlays for focus/selection/borders on dark surfaces
val CardIdleBorder = Color.White.copy(alpha = 0.10f)
val SelectionFocused = Color.White.copy(alpha = 0.14f)
val SelectionIdle = Color.White.copy(alpha = 0.08f)
val ButtonIdle = Color.White.copy(alpha = 0.12f)
val OutlineSubtle = Color.White.copy(alpha = 0.15f)
val OutlineMedium = Color.White.copy(alpha = 0.25f)

// HUD quick-menu
val HudChipFocus = Color(0xFF4A5568).copy(alpha = 0.6f)
val PsBlueSoft: Color
	@Composable @ReadOnlyComposable get() = PsBlue.copy(alpha = 0.30f)
val HomeGlowStrong: Color
	@Composable @ReadOnlyComposable get() = PsBlue.copy(alpha = 0.28f)
val HomeGlowWeak: Color
	@Composable @ReadOnlyComposable get() = PsBlue.copy(alpha = 0.08f)

private fun colorSchemeFor(palette: PsPalette) = darkColorScheme(
	primary = palette.accent,
	onPrimary = OnPrimary,
	secondary = palette.secondary,
	onSecondary = Color.Black,
	background = palette.background,
	onBackground = OnSurfaceLight,
	surface = palette.surface,
	onSurface = OnSurfaceLight,
	surfaceVariant = palette.surfaceCard,
	onSurfaceVariant = OnSurfaceMuted,
	error = ErrorRed,
	onError = OnPrimary
)

// Focus motion: scale-up + glow per interactive widget class
val FocusScaleCardX = 1.04f
val FocusScaleCardY = 1.10f
val FocusScaleButton = 1.06f
val FocusScaleIconButton = 1.12f
val FocusGlowCard = 12.dp
val FocusGlowPsButton = 18.dp
val FocusGlowButton = 16.dp
val FocusGlowIconButton = 10.dp

val CardShape = RoundedCornerShape(16.dp)
val SelectionShape = RoundedCornerShape(10.dp)
val ButtonShape = CircleShape
val DialogShape = RoundedCornerShape(20.dp)
val HudChipShape = RoundedCornerShape(12.dp)
val HudPanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

@Composable
fun ChiakiTvTheme(content: @Composable () -> Unit)
{
	val palette = AppThemeState.theme.palette()
	val scheme = remember(palette) { colorSchemeFor(palette) }
	CompositionLocalProvider(LocalPsPalette provides palette) {
		MaterialTheme(
			colorScheme = scheme,
			typography = ChiakiTvTypography,
			content = content
		)
	}
}
