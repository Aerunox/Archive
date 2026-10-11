# Remove the LiquidGlass theme entirely; app becomes single-theme (Miuix).
import os, shutil

ROOT = 'app/src/main/java/com/archive/app/'

def patch(rel, subs):
    path = ROOT + rel
    s = open(path, encoding='utf-8').read()
    for old, new in subs:
        assert old in s, "NOT FOUND in %s: %r" % (rel, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8', newline='').write(s)
    print('patched', rel)

def write(rel, content):
    path = ROOT + rel
    open(path, 'w', encoding='utf-8', newline='').write(content)
    print('rewrote', rel)

# 0) delete the glass package
shutil.rmtree(ROOT + 'ui/design/glass', ignore_errors=True)
print('deleted ui/design/glass')

# 1) version catalog + build.gradle
s = open('gradle/libs.versions.toml', encoding='utf-8').read()
s = s.replace('liquidglass = "1.1.0"\n', '')
s = s.replace('\nliquidglass-core = { group = "com.qmdeve.liquidglass", name = "core", version.ref = "liquidglass" }', '')
open('gradle/libs.versions.toml', 'w', encoding='utf-8', newline='').write(s)
print('cleaned libs.versions.toml')
s = open('app/build.gradle.kts', encoding='utf-8').read()
s = s.replace('\n    implementation(libs.liquidglass.core)', '')
open('app/build.gradle.kts', 'w', encoding='utf-8', newline='').write(s)
print('cleaned app/build.gradle.kts')

# 2) AppThemeSpec.kt: color/monet enums only
write('ui/theme/AppThemeSpec.kt', '''package com.archive.app.ui.theme

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
''')

# 3) PurrColors.kt: drop glass locals
patch('ui/theme/PurrColors.kt', [
 ("""val LocalPurrColors = staticCompositionLocalOf { purrLightColors() }
val LocalGlassParams = staticCompositionLocalOf { GlassParams() }
val LocalGlassSupported = staticCompositionLocalOf { false }

/** When true, list thumbnails render blurred until the user taps them. */
val LocalBlurThumbnails = staticCompositionLocalOf { true }""",
  """val LocalPurrColors = staticCompositionLocalOf { purrLightColors() }

/** When true, list thumbnails render blurred until the user taps them. */
val LocalBlurThumbnails = staticCompositionLocalOf { true }"""),
])

# 4) ArchiveTheme.kt: single Miuix theme
write('ui/theme/ArchiveTheme.kt', '''package com.archive.app.ui.theme

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
''')

# 5) ArchiveRoot: drop themeMode/glass from the call + VM setters
patch('ui/ArchiveRoot.kt', [
 ("import com.archive.app.ui.theme.AppColorMode\nimport com.archive.app.ui.theme.AppMonetStyle\nimport com.archive.app.ui.theme.AppThemeMode\nimport com.archive.app.ui.theme.GlassParams\nimport com.archive.app.ui.theme.ArchiveTheme",
  "import com.archive.app.ui.theme.AppColorMode\nimport com.archive.app.ui.theme.AppMonetStyle\nimport com.archive.app.ui.theme.ArchiveTheme"),
 ("""    fun setThemeMode(value: AppThemeMode) = launch { settings.setThemeMode(value) }
    fun setColorMode(value: AppColorMode) = launch { settings.setColorMode(value) }""",
  "    fun setColorMode(value: AppColorMode) = launch { settings.setColorMode(value) }"),
 ("""    fun setGlassEffects(value: Boolean) = launch { settings.setGlassEffects(value) }
    fun setGlassBlur(value: Float) = launch { settings.setGlassBlur(value) }
    fun setGlassRefractionHeight(value: Float) = launch { settings.setGlassRefractionHeight(value) }
    fun setGlassRefractionOffset(value: Float) = launch { settings.setGlassRefractionOffset(value) }
    fun setGlassDispersion(value: Float) = launch { settings.setGlassDispersion(value) }
    fun setGlassTintAlpha(value: Float) = launch { settings.setGlassTintAlpha(value) }
    fun setGlassCornerRadius(value: Float) = launch { settings.setGlassCornerRadius(value) }

    /** Resets all LiquidGlass tunables to the values documented upstream. */
    fun resetGlassDefaults() = launch {
        settings.setGlassEffects(true)
        settings.setGlassBlur(GlassParams().blurRadius)
        settings.setGlassRefractionHeight(GlassParams().refractionHeight)
        settings.setGlassRefractionOffset(GlassParams().refractionOffset)
        settings.setGlassDispersion(GlassParams().dispersion)
        settings.setGlassTintAlpha(GlassParams().tintAlpha)
        settings.setGlassCornerRadius(GlassParams().cornerRadius)
        settings.setGlassElastic(false)
        settings.setGlassDraggable(false)
        settings.setGlassTouchEffect(false)
    }
""",
  ""),
 ("""    ArchiveTheme(
        themeMode = settings.themeMode,
        colorMode = settings.colorMode,
        monetStyle = settings.monetStyle,
        seedColor = settings.seedColor.takeIf { it != 0L },
        glass = settings.glass,
        blurThumbnails = settings.blurThumbnails,
    ) {""",
  """    ArchiveTheme(
        colorMode = settings.colorMode,
        monetStyle = settings.monetStyle,
        seedColor = settings.seedColor.takeIf { it != 0L },
        blurThumbnails = settings.blurThumbnails,
    ) {"""),
])

# 6) SettingsRepository: drop themeMode + glass keys
patch('data/SettingsRepository.kt', [
 ("import com.archive.app.ui.theme.AppColorMode\nimport com.archive.app.ui.theme.AppMonetStyle\nimport com.archive.app.ui.theme.AppThemeMode\nimport com.archive.app.ui.theme.GlassParams",
  "import com.archive.app.ui.theme.AppColorMode\nimport com.archive.app.ui.theme.AppMonetStyle"),
 ("""data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.MIUIX,
    val colorMode: AppColorMode = AppColorMode.FOLLOW_SYSTEM,
    val monetStyle: AppMonetStyle = AppMonetStyle.TONAL_SPOT,
    val seedColor: Long = 0L,
    val glass: GlassParams = GlassParams(),
    val baseUrl: String = UrlProvider.DEFAULT_BASE_URL,""",
  """data class AppSettings(
    val colorMode: AppColorMode = AppColorMode.FOLLOW_SYSTEM,
    val monetStyle: AppMonetStyle = AppMonetStyle.TONAL_SPOT,
    val seedColor: Long = 0L,
    val baseUrl: String = UrlProvider.DEFAULT_BASE_URL,"""),
 ("""    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val MONET_STYLE = stringPreferencesKey("monet_style")
        val SEED_COLOR = longPreferencesKey("seed_color")
        val GLASS_EFFECTS = booleanPreferencesKey("glass_effects")
        val GLASS_BLUR = floatPreferencesKey("glass_blur")
        val GLASS_REFRACTION_HEIGHT = floatPreferencesKey("glass_refraction_height")
        val GLASS_REFRACTION_OFFSET = floatPreferencesKey("glass_refraction_offset")
        val GLASS_DISPERSION = floatPreferencesKey("glass_dispersion")
        val GLASS_TINT_ALPHA = floatPreferencesKey("glass_tint_alpha")
        val GLASS_CORNER_RADIUS = floatPreferencesKey("glass_corner_radius")
        val GLASS_ELASTIC = booleanPreferencesKey("glass_elastic")
        val GLASS_DRAGGABLE = booleanPreferencesKey("glass_draggable")
        val GLASS_TOUCH_EFFECT = booleanPreferencesKey("glass_touch_effect")
        val BASE_URL = stringPreferencesKey("base_url")""",
  """    private object Keys {
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val MONET_STYLE = stringPreferencesKey("monet_style")
        val SEED_COLOR = longPreferencesKey("seed_color")
        val BASE_URL = stringPreferencesKey("base_url")"""),
 ("""        AppSettings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            colorMode = p[Keys.COLOR_MODE]?.let { runCatching { AppColorMode.valueOf(it) }.getOrNull() }
                ?: defaults.colorMode,
            monetStyle = p[Keys.MONET_STYLE]?.let { runCatching { AppMonetStyle.valueOf(it) }.getOrNull() }
                ?: defaults.monetStyle,
            seedColor = p[Keys.SEED_COLOR] ?: defaults.seedColor,
            glass = GlassParams(
                effectsEnabled = p[Keys.GLASS_EFFECTS] ?: defaults.glass.effectsEnabled,
                blurRadius = p[Keys.GLASS_BLUR] ?: defaults.glass.blurRadius,
                refractionHeight = p[Keys.GLASS_REFRACTION_HEIGHT] ?: defaults.glass.refractionHeight,
                refractionOffset = p[Keys.GLASS_REFRACTION_OFFSET] ?: defaults.glass.refractionOffset,
                dispersion = p[Keys.GLASS_DISPERSION] ?: defaults.glass.dispersion,
                tintAlpha = p[Keys.GLASS_TINT_ALPHA] ?: defaults.glass.tintAlpha,
                cornerRadius = p[Keys.GLASS_CORNER_RADIUS] ?: defaults.glass.cornerRadius,
                elasticEnabled = p[Keys.GLASS_ELASTIC] ?: defaults.glass.elasticEnabled,
                draggableEnabled = p[Keys.GLASS_DRAGGABLE] ?: defaults.glass.draggableEnabled,
                touchEffectEnabled = p[Keys.GLASS_TOUCH_EFFECT] ?: defaults.glass.touchEffectEnabled,
            ),
            baseUrl = (p[Keys.BASE_URL] ?: defaults.baseUrl).ifBlank { defaults.baseUrl },""",
  """        AppSettings(
            colorMode = p[Keys.COLOR_MODE]?.let { runCatching { AppColorMode.valueOf(it) }.getOrNull() }
                ?: defaults.colorMode,
            monetStyle = p[Keys.MONET_STYLE]?.let { runCatching { AppMonetStyle.valueOf(it) }.getOrNull() }
                ?: defaults.monetStyle,
            seedColor = p[Keys.SEED_COLOR] ?: defaults.seedColor,
            baseUrl = (p[Keys.BASE_URL] ?: defaults.baseUrl).ifBlank { defaults.baseUrl },"""),
 ("""    suspend fun setThemeMode(value: AppThemeMode) = edit { it[Keys.THEME_MODE] = value }
    suspend fun setColorMode(value: AppColorMode) = edit { it[Keys.COLOR_MODE] = value }""",
  "    suspend fun setColorMode(value: AppColorMode) = edit { it[Keys.COLOR_MODE] = value }"),
 ("""    suspend fun setGlassEffects(value: Boolean) = edit { it[Keys.GLASS_EFFECTS] = value }
    suspend fun setGlassBlur(value: Float) = edit { it[Keys.GLASS_BLUR] = value }
    suspend fun setGlassRefractionHeight(value: Float) = edit { it[Keys.GLASS_REFRACTION_HEIGHT] = value }
    suspend fun setGlassRefractionOffset(value: Float) = edit { it[Keys.GLASS_REFRACTION_OFFSET] = value }
    suspend fun setGlassDispersion(value: Float) = edit { it[Keys.GLASS_DISPERSION] = value }
    suspend fun setGlassTintAlpha(value: Float) = edit { it[Keys.GLASS_TINT_ALPHA] = value }
    suspend fun setGlassCornerRadius(value: Float) = edit { it[Keys.GLASS_CORNER_RADIUS] = value }
    suspend fun setGlassElastic(value: Boolean) = edit { it[Keys.GLASS_ELASTIC] = value }
    suspend fun setGlassDraggable(value: Boolean) = edit { it[Keys.GLASS_DRAGGABLE] = value }
    suspend fun setGlassTouchEffect(value: Boolean) = edit { it[Keys.GLASS_TOUCH_EFFECT] = value }
""",
  ""),
])
print('PHASE 1 DONE')
