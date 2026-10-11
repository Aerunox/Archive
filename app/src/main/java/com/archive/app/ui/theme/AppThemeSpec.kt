package com.archive.app.ui.theme

/**
 * Pure theme definitions. The app renders with the Miuix component library
 * (HyperOS style); these enums drive Miuix's ThemeController.
 *
 * SIX modes so the Miuix theme can expose its full ColorSchemeMode set
 * (System/Light/Dark + the three Monet variants).
 */
enum class AppColorMode {
    FOLLOW_SYSTEM, LIGHT, DARK,
    MONET_SYSTEM, MONET_LIGHT, MONET_DARK,
}

enum class AppMonetStyle { TONAL_SPOT, NEUTRAL, VIBRANT, EXPRESSIVE }
