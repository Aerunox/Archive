package com.archive.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.theme.LocalPurrColors

/**
 * Top tab row with a spring-following indicator (requirement: 顶部标签切换 +
 * 指示条动画 + 跟随感). The indicator bar slides to the selected tab with a
 * slightly under-damped spring so it trails the finger like a physical tab.
 */
@Composable
fun AppTabRow(
    tabs: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalPurrColors.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * selected,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "tabIndicator",
        )
        Column {
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                tabs.forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            label,
                            style = AppTextStyle.MAIN,
                            color = if (index == selected) colors.primary else colors.textSecondary,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                Box(
                    Modifier
                        .offset(x = indicatorX)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(colors.primary),
                )
            }
        }
    }
}
