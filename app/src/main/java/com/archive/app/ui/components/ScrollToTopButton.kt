package com.archive.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 「回到顶部」按钮：列表向下滚过若干条目后，在右下角出现；点击快速滚回顶部。
 *
 * 用法：
 * ```
 * val listState = rememberLazyListState()
 * val scrollTop = rememberListScrollToTopState(listState)
 * LazyColumn(state = listState) { … }
 * ScrollToTopButton(scrollTop, Modifier.align(Alignment.BottomEnd))
 * ```
 */
class ScrollToTopState internal constructor(
    private val show: State<Boolean>,
    private val action: suspend () -> Unit,
) {
    val visible: Boolean get() = show.value

    suspend fun scrollToTop() = action()
}

@Composable
fun rememberListScrollToTopState(
    state: LazyListState,
    thresholdItems: Int = 3,
    onReachTop: () -> Unit = {},
): ScrollToTopState {
    val show = remember(state, thresholdItems) {
        derivedStateOf {
            state.firstVisibleItemIndex > thresholdItems ||
                (state.firstVisibleItemIndex == thresholdItems && state.firstVisibleItemScrollOffset > 0)
        }
    }
    // 回到顶部（按钮滚动或手动滑回）时通知外部——用于让底栏重新出现
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collect { atTop -> if (atTop) onReachTop() }
    }
    return remember(state, show) {
        ScrollToTopState(show) { state.animateScrollToItem(0) }
    }
}

@Composable
fun rememberGridScrollToTopState(
    state: LazyGridState,
    thresholdItems: Int = 6,
    onReachTop: () -> Unit = {},
): ScrollToTopState {
    val show = remember(state, thresholdItems) {
        derivedStateOf {
            state.firstVisibleItemIndex > thresholdItems ||
                (state.firstVisibleItemIndex == thresholdItems && state.firstVisibleItemScrollOffset > 0)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collect { atTop -> if (atTop) onReachTop() }
    }
    return remember(state, show) {
        ScrollToTopState(show) { state.animateScrollToItem(0) }
    }
}

/** 右下角悬浮按钮（圆角方形、主题容器的浅色底 + 主色箭头），放在内容层底部即可。 */
@Composable
fun ScrollToTopButton(
    state: ScrollToTopState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPurrColors.current
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Surface(
            onClick = { scope.launch { state.scrollToTop() } },
            shape = RoundedCornerShape(18.dp),
            color = colors.secondaryContainer,
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LucideIcons.arrowUp,
                    contentDescription = "回到顶部",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
