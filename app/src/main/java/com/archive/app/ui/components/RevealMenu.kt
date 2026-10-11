package com.archive.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.theme.LocalPurrColors
import kotlin.math.sqrt

data class MenuItemSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Shared state for the fullscreen reveal menu (requirement 10). */
class AppMenuController {
    var visible by mutableStateOf(false)
        private set
    var center by mutableStateOf(Offset.Zero)
        private set

    fun open(from: Offset) {
        center = from
        visible = true
    }

    fun close() {
        visible = false
    }
}

val LocalAppMenuController = staticCompositionLocalOf { AppMenuController() }

/**
 * Fullscreen menu that expands as a circle from the trigger button position
 * and retracts along the same path (requirement: 按钮触发、扩散展开、
 * 全屏覆盖、原路收回). Menu rows fade in staggered while the circle grows.
 */
@Composable
fun RevealMenuOverlay(
    controller: AppMenuController,
    items: List<MenuItemSpec>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPurrColors.current
    var shown by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(controller.visible) {
        if (controller.visible) {
            shown = true
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
        } else if (shown) {
            progress.animateTo(0f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
            shown = false
        }
    }

    BackHandler(enabled = controller.visible) { controller.close() }

    if (!shown) return

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { controller.close() }
            }
            .drawBehind {
                val center = controller.center
                val dx = maxOf(center.x, size.width - center.x)
                val dy = maxOf(center.y, size.height - center.y)
                val maxRadius = sqrt(dx * dx + dy * dy)
                drawCircle(
                    color = colors.background,
                    radius = maxRadius * progress.value,
                    center = center,
                )
            },
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                // Staggered appearance tied to the reveal progress.
                val itemProgress =
                    ((progress.value - 0.35f - index * 0.06f) / 0.25f).coerceIn(0f, 1f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = itemProgress
                            translationX = 40.dp.toPx() * (1f - itemProgress)
                        }
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .clickable(enabled = itemProgress >= 1f) {
                            controller.close()
                            item.onClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                    AppText(item.label, style = AppTextStyle.MAIN)
                }
            }
        }
        // Close trigger in the top bar position — same spot the menu came from.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
        ) {
            Box(
                Modifier
                    .clickable(enabled = progress.value >= 1f) { controller.close() }
                    .padding(12.dp),
            ) {
                MenuCloseIcon(checked = true, tint = colors.textPrimary)
            }
        }
    }
}

