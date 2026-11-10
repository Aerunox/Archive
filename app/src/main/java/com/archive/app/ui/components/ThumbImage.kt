package com.archive.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.archive.app.ui.theme.LocalBlurThumbnails
import com.archive.app.util.BlurTransformation

/**
 * 缩略图解码的显示目标尺寸上限（像素）。预览界面（列表 / 网格 / 详情缩略）展示的
 * 封面是 fillMaxWidth + 4:3 等信息流尺寸，@2x 下长边通常也就 ~800px；给 Coil
 * 设一个上限让它在解码时就向下采样到实际需要的大小，而不是把站点原尺寸的 thumbnail
 * 整张解出来再裁切 —— 解码更快、内存更省、滚动更顺，视觉差别很小。
 * 需要看更清晰的调用方（如详情放大页）可传更大的 targetSize 覆盖。
 */
private const val DEFAULT_THUMB_TARGET_SIZE = 1080

/**
 * Remote thumbnail with optional blur (privacy switch, default ON).
 * Always provide a contentDescription for accessibility.
 *
 * **作者头像请用 [CreatorAvatar]**（固定不模糊）：那个开关只针对内容。
 */
@Composable
fun ThumbImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blurredOverride: Boolean? = null,
    cornerRadius: Int = 12,
    originalUrl: String? = null,
    targetSize: Int? = DEFAULT_THUMB_TARGET_SIZE,
) {
    val blur = blurredOverride ?: LocalBlurThumbnails.current
    val context = LocalContext.current
    val colors = com.archive.app.ui.theme.LocalPurrColors.current
    // 模糊开启时（默认）：原图那点分辨率会被模糊抹掉，直接用缩略图 ——
    // 省掉一次大图下载 + 大图解码 + 大图模糊，这是信息流滚动卡顿的主要来源。
    // 用户点开看清（blur = false）时仍按原来的逻辑取原图。
    val effectiveOriginal = originalUrl?.takeIf { !blur }
    // Original files are login-gated server-side; when the original request
    // fails (e.g. guest session), fall back to the thumbnail variant.
    var originalFailed by remember(url, originalUrl) { mutableStateOf(false) }
    val data = if (effectiveOriginal != null && originalFailed) url else (effectiveOriginal ?: url)
    // 「模糊缩略图」开关对所有媒体一视同仁：**不做任何格式豁免** ——
    // GIF / WebP / HEIF / AVIF 这些动图同样模糊，webm 视频首帧同理（见 VideoPosterCell）。
    // 代价是动图会退化成一帧模糊静图（Transformation 作用于解码后的位图），
    // 这是刻意的取舍：隐私优先于动图效果，不要再按扩展名把动图放出去。
    val model = remember(url, originalUrl, blur, data, targetSize) {
        ImageRequest.Builder(context)
            .data(data)
            // 解码时按显示目标尺寸向下采样：预览界面勿整张解码，速度与内存双收益。
            .apply { if (targetSize != null) size(targetSize, targetSize) }
            .apply { if (blur) transformations(BlurTransformation()) }
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius.dp)),
        contentScale = ContentScale.Crop,
        // 加载占位：给个中性容器色，避免列表快速滚动时封面区域一片空白——
        // 占位色让"正在加载"可预期，感知上比空白位快
        placeholder = androidx.compose.runtime.remember(colors) {
            androidx.compose.ui.graphics.painter.ColorPainter(colors.surfaceContainer)
        },
        onError = {
            if (originalUrl != null && !originalFailed) originalFailed = true
        },
    )
}

/**
 * 作者头像 —— **任何界面都保持清晰，不受「模糊缩略图」开关影响**。
 *
 * 那个开关是给**内容**用的：列表里的帖子封面 / 缩略图，以及 WebM 这类动图内容本身。
 * 头像属于界面元素 —— 糊掉它遮不住内容，只会让人认不出「这是谁」。
 *
 * 以前这件事靠每个调用点自己传 `blurredOverride = false`，漏一处就漏一个
 * （创作者列表那处就漏了，于是列表里的头像跟着被模糊），所以把「头像永不模糊」
 * 做进组件本身的语义：调用方不需要、也不应该再关心这个开关。
 */
@Composable
fun CreatorAvatar(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cornerRadius: Int = 12,
) {
    ThumbImage(
        url = url,
        modifier = modifier,
        contentDescription = contentDescription,
        blurredOverride = false,
        cornerRadius = cornerRadius,
    )
}

/**
 * Multi-URL fallback image (Pawchive fancard pattern): tries each URL in
 * order, moving to the next on failure.
 */
@Composable
fun MultiFallbackImage(
    urls: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blurredOverride: Boolean? = null,
) {
    if (urls.isEmpty()) return
    var attempt by remember(urls) { mutableStateOf(0) }
    val data = urls[attempt.coerceIn(0, urls.lastIndex)]
    val context = LocalContext.current
    // 与 ThumbImage 同一规则：没显式指定就跟随「模糊缩略图」开关。
    // 这里以前完全没接线（参数是死的），于是粉丝卡这类多源回退的媒体在开关开启时依然清晰。
    val blur = blurredOverride ?: LocalBlurThumbnails.current
    val model = remember(data, urls, blur) {
        ImageRequest.Builder(context)
            .data(data)
            .apply { if (blur) transformations(BlurTransformation()) }
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        onError = {
            if (attempt < urls.lastIndex) attempt += 1
        },
    )
}
