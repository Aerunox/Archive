package com.archive.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.theme.LocalPurrColors

data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Floating plus FAB that expands an action panel in place (requirement:
 * 悬浮加号、原位展开、操作面板、关闭收回). The plus icon rotates into a
 * close mark; the panel scales/slides out from the FAB; back or picking an
 * action retracts it.
 */
@Composable
fun ExpandableFab(
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalPurrColors.current

    BackHandler(enabled = open) { open = false }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() +
                scaleIn(initialScale = 0.6f),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut() +
                scaleOut(targetScale = 0.6f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                actions.forEach { action ->
                    AppCard(onClick = {
                        open = false
                        action.onClick()
                    }) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppText(action.label, style = AppTextStyle.SECONDARY)
                            androidx.compose.material3.Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = colors.primary,
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            MorphPlusIcon(open = open, tint = colors.onPrimary)
        }
    }
}
