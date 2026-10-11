package com.archive.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.archive.app.ui.design.LocalDesign
import com.archive.app.ui.design.miuix.MiuixDesign
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

fun AppColorMode.toMiuixMode(): ColorSchemeMode = when (this) {
    AppColorMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
    AppColorMode.LIGHT -> ColorSchemeMode.Light
    AppColorMode.DARK -> ColorSchemeMode.Dark
    AppColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
    AppColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
    AppColorMode.MONET_DARK -> ColorSchemeMode.MonetDark
}

fun AppMonetStyle.toPaletteStyle(): ThemePaletteStyle = when (this) {
    AppMonetStyle.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
    AppMonetStyle.NEUTRAL -> ThemePaletteStyle.Neutral
    AppMonetStyle.VIBRANT -> ThemePaletteStyle.Vibrant
    AppMonetStyle.EXPRESSIVE -> ThemePaletteStyle.Expressive
}

/** True when the chosen mode derives colors from a seed (Monet). */
fun AppColorMode.isMonet(): Boolean = this == AppColorMode.MONET_SYSTEM ||
    this == AppColorMode.MONET_LIGHT || this == AppColorMode.MONET_DARK

private fun Colors.toPurrColors(): PurrColors = PurrColors(
    background = background,
    surface = surface,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    primary = primary,
    onPrimary = onPrimary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    textPrimary = onBackground,
    textSecondary = onBackgroundVariant,
    textTertiary = onSurfaceVariantSummary,
    divider = dividerLine,
    error = error,
    scrim = windowDimming,
)

/**
 * Theme engine: wraps the app in MiuixTheme (single visual theme) and
 * publishes the neutral tokens through CompositionLocals. Changing colors at
 * runtime only recomposes this wrapper — no Activity recreation.
 */
@Composable
fun ArchiveTheme(
    colorMode: AppColorMode,
    monetStyle: AppMonetStyle,
    seedColor: Long?,
    blurThumbnails: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode, monetStyle, seedColor) {
        ThemeController(
            colorSchemeMode = colorMode.toMiuixMode(),
            keyColor = seedColor?.takeIf { it != 0L }?.let { Color(it) },
            paletteStyle = monetStyle.toPaletteStyle(),
        )
    }
    MiuixTheme(controller = controller) {
        val purr = MiuixTheme.colorScheme.toPurrColors()
        CompositionLocalProvider(
            LocalPurrColors provides purr,
            LocalDesign provides MiuixDesign,
            LocalBlurThumbnails provides blurThumbnails,
        ) {
            content()
        }
    }
}
