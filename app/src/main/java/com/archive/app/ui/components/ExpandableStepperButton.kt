package com.archive.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors

/**
 * Button that expands into a quantity stepper in place (requirement:
 * 按钮展开、数量选择器、归零还原按钮). Decreasing to zero collapses the
 * stepper back into the original button; confirming hands the picked count
 * to [onConfirm].
 */
@Composable
fun ExpandableStepperButton(
    label: String,
    max: Int,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(1) }
    val colors = LocalPurrColors.current

    AnimatedContent(
        targetState = expanded,
        transitionSpec = { (fadeIn()).togetherWith(fadeOut()) },
        modifier = modifier.animateContentSize(),
        label = "stepper",
    ) { isExpanded ->
        if (!isExpanded) {
            AppButton(label, {
                count = 1
                expanded = true
            }, Modifier)
        } else {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clickable {
                            count -= 1
                            if (count <= 0) expanded = false // 归零还原按钮
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("−", style = AppTextStyle.TITLE, color = colors.onSecondaryContainer)
                }
                AppText(
                    "$count",
                    style = AppTextStyle.MAIN,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clickable(enabled = count < max) { count += 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("+", style = AppTextStyle.TITLE, color = colors.onSecondaryContainer)
                }
                AppIconButton(
                    onClick = {
                        onConfirm(count.coerceIn(1, max))
                        expanded = false
                    },
                    icon = LucideIcons.download,
                    contentDescription = "确认下载 $count 张",
                    tint = colors.primary,
                )
            }
        }
    }
}
