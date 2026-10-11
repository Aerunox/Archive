package com.archive.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-neutral color tokens. Both visual themes map their palette into this
 * struct, so screens never touch Miuix or Material3 colors directly.
 */
data class PurrColors(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val error: Color,
    val scrim: Color,
)

/** Base palette (kept as neutral tokens shared by common components). */
fun purrLightColors(primary: Color = DefaultSeed): PurrColors = PurrColors(
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F1F6),
    surfaceContainerHigh = Color(0xFFE8EAF1),
    primary = primary,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFE3ECFF),
    onSecondaryContainer = Color(0xFF14345F),
    textPrimary = Color(0xFF16181D),
    textSecondary = Color(0xFF5C6068),
    textTertiary = Color(0xFF90949C),
    divider = Color(0x1F000000),
    error = Color(0xFFB3261E),
    scrim = Color(0x66000000),
)

fun purrDarkColors(primary: Color = DefaultSeedDark): PurrColors = PurrColors(
    background = Color(0xFF101216),
    surface = Color(0xFF191B20),
    surfaceContainer = Color(0xFF1F2228),
    surfaceContainerHigh = Color(0xFF272A31),
    primary = primary,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFF2C3B55),
    onSecondaryContainer = Color(0xFFD6E4FF),
    textPrimary = Color(0xFFEDEFF3),
    textSecondary = Color(0xFFA6AAB2),
    textTertiary = Color(0xFF6F737B),
    divider = Color(0x24FFFFFF),
    error = Color(0xFFF2B8B5),
    scrim = Color(0x99000000),
)

val DefaultSeed = Color(0xFF3482FF)
val DefaultSeedDark = Color(0xFF5B9BFF)

val LocalPurrColors = staticCompositionLocalOf { purrLightColors() }

/** When true, list thumbnails render blurred until the user taps them. */
val LocalBlurThumbnails = staticCompositionLocalOf { true }

/**
 * 各内容平台的**品牌色**，用于标签这类「一眼看出内容来自哪家」的位置。
 *
 * - Patreon：珊瑚橙 `#F96854`（其标志色，第三方客户端的通用取值）；
 * - Fanbox（pixivFANBOX）：沿用 pixiv 品牌蓝 `#0096FA`。
 *
 * 与站点现版官网的营销配色可能略有出入 —— 要调就改这里，两个平台的标签会一起变。
 * 返回 null 表示该服务没有约定色，调用方应回退到主题的中性样式。
 */
fun serviceBrandColor(service: String?): Color? = when (service?.trim()?.lowercase()) {
    "patreon" -> Color(0xFFF96854)
    "fanbox" -> Color(0xFF0096FA)
    else -> null
}

/**
 * 平台徽标文案：接口给的 `service` 归一成品牌名。
 *
 * 不直接用 `service.uppercase()` 的原因有二：取值大小写/空白不一致时标签会走样；
 * 而该字段是 Gson 反序列化填充的非空 `String`，字段缺失时运行时会是 null，
 * 直接 uppercase 会崩。未知取值仍原样大写 —— 站点新增平台时一眼能看出来。
 */
fun servicePlatformLabel(service: String?): String =
    when (val normalized = service?.trim()?.lowercase()) {
        "patreon" -> "PATREON"
        "fanbox" -> "FANBOX"
        null, "" -> ""
        else -> normalized.uppercase()
    }

/**
 * 品牌色底上可读的文字色：底色偏亮用近黑、偏暗用白。
 * （Patreon 珊瑚与 pixiv 蓝的亮度都低于阈值，实际都会得到白字。）
 */
fun onServiceBrandColor(brand: Color): Color =
    if (brand.luminance() > 0.45f) Color(0xFF16181D) else Color.White
