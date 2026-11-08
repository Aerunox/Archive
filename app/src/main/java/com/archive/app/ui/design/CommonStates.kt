package com.archive.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archive.app.ui.theme.LocalPurrColors
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Theme-neutral states (loading / empty / error) shared by every screen. */

/**
 * 全屏加载动画：Miuix 官方的 [InfiniteProgressIndicator]（轨道 + 环绕圆点），
 * 与 HyperOS 系统加载动画同款。全应用的 loading 统一走这里与 [AppLoadingItem]，
 * 不要在页面里直接用 Material 的 CircularProgressIndicator。
 */
@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}

/** 列表内联加载动画：与 [AppLoading] 同款 Miuix 组件，仅尺寸收窄。 */
@Composable
fun AppLoadingItem(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * 行内小尺寸加载动画（操作反馈：如「翻译中」「识别中」「缓冲中」）。
 *
 * 与 [AppLoading] / [AppLoadingItem] 一样走 Miuix 官方组件——全应用的加载动画
 * 统一在 Miuix 一套里，不要在页面/组件里直接使用 Material 的
 * CircularProgressIndicator（那是另一套观感，与 Miuix 主题不一致）。
 */
@Composable
fun AppSpinner(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        progress = null,
        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
        strokeWidth = strokeWidth,
        // 圆环直径必须走组件自己的 `size` 参数（默认 30dp）：只缩 modifier 的话
        // 布局盒子变小了、圆环仍按 30dp 画，会溢出边界压住旁边的文字
        size = size,
    )
}

@Composable
fun AppEmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(message, style = AppTextStyle.SECONDARY, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(message, style = AppTextStyle.SECONDARY, textAlign = TextAlign.Center)
        if (onRetry != null) {
            AppButton("重试", onRetry, Modifier.padding(top = 16.dp), primary = false)
        }
    }
}

/** Simple segmented tab used by screens that switch between two lists. */
@Composable
fun CommonTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) colors.primary.copy(alpha = 0.14f)
                else colors.surfaceContainer.copy(alpha = 0.7f),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            style = AppTextStyle.SMALL,
            color = if (selected) colors.primary else colors.textSecondary,
        )
    }
}
