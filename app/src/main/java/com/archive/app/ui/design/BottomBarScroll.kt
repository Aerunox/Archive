package com.archive.app.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 滚动驱动的底部导航栏显隐：**列表向下滚动时隐藏，向上滚动时显示**。
 *
 * 观感与实现要点：
 * - 只在「内容确实向下滚过一小段」（滑动阈值）后隐藏，避免手指轻微抖动导致闪烁；
 *   反向一旦出现立即显示；回顶（无法再向下滚）时也会显示。
 * - 惯性滑动在 [NestedScrollConnection.onPreFling] 里按速度方向一次性判定，不逐帧切换。
 * - 隐藏时**保留布局占位**，仅把栏平移出屏（graphicsLayer），避免滚动过程中列表因
 *   内边距变化而重排（那会导致滚动卡顿与位置跳动）。
 */
class AppBottomBarScrollState internal constructor(
    val visible: MutableState<Boolean>,
    val nestedScrollConnection: NestedScrollConnection,
) {
    /** 挂到滚动列表上：`LazyColumn(Modifier.bottomBarHideOnScroll(state))`。 */
    fun attach(modifier: Modifier): Modifier =
        modifier.nestedScroll(nestedScrollConnection)

    /** 立即显示底栏（例如程序化滚动回到顶部、或页面需要重新露出导航）。 */
    fun show() {
        visible.value = true
    }

    fun hide() {
        visible.value = false
    }
}

@Composable
fun rememberAppBottomBarScrollState(
    hideThreshold: androidx.compose.ui.unit.Dp = 16.dp,
    showFlingVelocity: Float = 120f,
): AppBottomBarScrollState {
    val visible = remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { hideThreshold.toPx() }
    val accumulatedDown = remember { mutableFloatStateOf(0f) }

    val connection = remember(thresholdPx, showFlingVelocity) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Fling) return Offset.Zero
                val dy = available.y
                if (dy < 0f) {
                    // 内容向上滑动 = 用户在往列表深处滚 → 累计后隐藏
                    accumulatedDown.floatValue += -dy
                    if (visible.value && accumulatedDown.floatValue > thresholdPx) {
                        visible.value = false
                    }
                } else if (dy > 0f) {
                    // 回滚 → 立即显示
                    accumulatedDown.floatValue = 0f
                    if (!visible.value) visible.value = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 列表已到顶（滚动被消费光）时仍有向下的余量 → 保证显示
                if (available.y > 0f) {
                    accumulatedDown.floatValue = 0f
                    if (!visible.value) visible.value = true
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                when {
                    available.y < -showFlingVelocity -> visible.value = false
                    available.y > showFlingVelocity -> visible.value = true
                }
                return Velocity.Zero
            }
        }
    }
    return remember(connection) { AppBottomBarScrollState(visible, connection) }
}

/**
 * 承载底栏：隐藏时**整栏下滑出屏并同时收起自身高度**。
 *
 * 只做平移会让隐藏后留下一条和底栏等高的空白条（Scaffold 的内容内边距仍按底栏高度计算），
 * 所以这里用 AnimatedVisibility 的 expand/shrink + slide 组合：内容保持原始高度被裁切，
 * 容器高度动画到 0，Scaffold 的底部内边距随之收缩，列表顺势铺满，滚动中也不会挤压缩放底栏内容。
 */
@Composable
fun AppBottomBarHost(
    state: AppBottomBarScrollState,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = state.visible.value,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { it },
        ) + expandVertically(
            animationSpec = tween(durationMillis = 220),
            expandFrom = Alignment.Bottom,
        ) + fadeIn(tween(durationMillis = 160)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 220),
            targetOffsetY = { it },
        ) + shrinkVertically(
            animationSpec = tween(durationMillis = 220),
            shrinkTowards = Alignment.Bottom,
        ) + fadeOut(tween(durationMillis = 160)),
    ) {
        content()
    }
}

/** 供嵌套较深的列表（如首页网格）取用当前页面的底栏滚动状态。 */
val LocalBottomBarScrollState = staticCompositionLocalOf<AppBottomBarScrollState?> { null }

@Composable
fun ProvideBottomBarScrollState(
    state: AppBottomBarScrollState,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(LocalBottomBarScrollState provides state, content = content)

/** 便捷：把当前页面的底栏滚动状态接到这个 modifier 上（无状态时原样返回）。 */
@Composable
fun Modifier.hideBottomBarOnScroll(): Modifier {
    val state = LocalBottomBarScrollState.current ?: return this
    return state.attach(this)
}
