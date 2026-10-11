package com.archive.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.CreatorSortMode
import com.archive.app.util.PostSortMode

/**
 * 通用排序入口：小胶囊显示当前排序方式，点开弹出全部选项（当前项打勾）。
 *
 * 四个帖子列表页（首页 / 搜索结果 / 收藏 / 作者-帖子）与两个创作者列表（发现 / 收藏-作者）
 * 共用它，保证排序入口的位置、样式与文案一致。
 *
 * @param disabledNote 返回非 null 表示该项不可用，返回值作为不可用的原因显示在选项下方。
 * @param note 弹窗顶部的一句说明。
 */
@Composable
fun <T> SortMenu(
    options: List<T>,
    current: T,
    chipLabel: String,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    disabledNote: (T) -> String? = { null },
    note: String? = null,
) {
    val colors = LocalPurrColors.current
    var open by remember { mutableStateOf(false) }

    AppCard(modifier = modifier, onClick = { open = true }) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(chipLabel, style = AppTextStyle.SMALL, color = colors.primary)
            Icon(
                imageVector = LucideIcons.expander,
                contentDescription = "排序方式",
                tint = colors.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }

    AppDialog(
        show = open,
        title = "排序方式",
        summary = note,
        onDismiss = { open = false },
    ) {
        options.forEach { option ->
            val disabled = disabledNote(option)
            AppListItem(
                title = labelOf(option),
                summary = disabled,
                end = {
                    if (option == current) {
                        Icon(
                            imageVector = LucideIcons.check,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                onClick = if (disabled != null) {
                    null
                } else {
                    {
                        onSelect(option)
                        open = false
                    }
                },
            )
        }
    }
}

/**
 * 帖子列表的排序入口（发布时间 / 收藏时间 / 搜索相关度）。
 *
 * @param hiddenModes 不提供的排序方式：例如收藏页没有搜索关键词，「相关度」在那儿没有意义，
 *   就不列出来（列出来点它也只是退化成发布时间倒序，反而让人困惑）。
 */
@Composable
fun PostSortMenu(
    current: PostSortMode,
    onSelect: (PostSortMode) -> Unit,
    modifier: Modifier = Modifier,
    disabledModes: Set<PostSortMode> = emptySet(),
    hiddenModes: Set<PostSortMode> = emptySet(),
    note: String? = null,
) = SortMenu(
    options = PostSortMode.entries.filterNot { it in hiddenModes },
    current = current,
    chipLabel = current.shortLabel,
    labelOf = { it.label },
    onSelect = onSelect,
    modifier = modifier,
    disabledNote = { if (it in disabledModes) "需登录（站点只返回云端收藏序号）" else null },
    note = note,
)

/**
 * 创作者列表（发现 / 收藏-作者）的排序入口。
 *
 * @param hiddenModes 不提供的排序方式：收藏页没有搜索关键词，「相关度」在那儿没有意义，
 *   就不列出来（列出来点它也只是退化成时间序，反而让人困惑）。
 */
@Composable
fun CreatorSortMenu(
    current: CreatorSortMode,
    onSelect: (CreatorSortMode) -> Unit,
    modifier: Modifier = Modifier,
    hiddenModes: Set<CreatorSortMode> = emptySet(),
    note: String? = null,
) = SortMenu(
    options = CreatorSortMode.entries.filterNot { it in hiddenModes },
    current = current,
    chipLabel = current.shortLabel,
    labelOf = { it.label },
    onSelect = onSelect,
    modifier = modifier,
    note = note,
)
