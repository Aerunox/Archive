package com.archive.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppSpinner
import com.archive.app.ui.theme.LocalPurrColors
import kotlinx.coroutines.delay

/** In-place submit feedback states (requirement: 提交按钮、同位置切换、加载、完成、结果). */
enum class MorphState { IDLE, LOADING, SUCCESS, ERROR }

private suspend fun autoResetDelay(onAutoReset: () -> Unit) {
    delay(1800)
    onAutoReset()
}

/**
 * Icon button that morphs through idle → loading spinner → animated check
 * (success) / error mark, all in the same position.
 */
@Composable
fun MorphIconButton(
    state: MorphState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
    onAutoReset: () -> Unit = {},
) {
    LaunchedEffect(state) {
        if (state == MorphState.SUCCESS || state == MorphState.ERROR) autoResetDelay(onAutoReset)
    }
    val colors = LocalPurrColors.current
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.6f))
                .togetherWith(fadeOut() + scaleOut(targetScale = 0.6f))
        },
        label = "morphIcon",
    ) { st ->
        when (st) {
            MorphState.IDLE -> AppIconButton(onClick = onAction, icon = icon, contentDescription = contentDescription, modifier = modifier)
            MorphState.LOADING -> Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AppSpinner(size = 18.dp, color = colors.primary)
            }
            MorphState.SUCCESS -> Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CheckMorphIcon(visible = true, tint = colors.primary)
            }
            MorphState.ERROR -> Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
                MenuCloseIcon(checked = true, tint = colors.error)
            }
        }
    }
}

/**
 * Button whose label morphs in place through the submit states
 * (idle → loading → success/error), keeping the same position and size.
 */
@Composable
fun MorphStateButton(
    state: MorphState,
    label: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
    onAutoReset: () -> Unit = {},
) {
    LaunchedEffect(state) {
        if (state == MorphState.SUCCESS || state == MorphState.ERROR) autoResetDelay(onAutoReset)
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = { (fadeIn()).togetherWith(fadeOut()) },
        label = "morphButton",
    ) { st ->
        when (st) {
            MorphState.IDLE -> AppButton(label, onAction, modifier)
            MorphState.LOADING -> AppButton("同步中…", {}, modifier, enabled = false)
            MorphState.SUCCESS -> AppButton("完成 ✓", {}, modifier, enabled = false)
            MorphState.ERROR -> AppButton("失败 · 点击重试", onAction, modifier, primary = false)
        }
    }
}
