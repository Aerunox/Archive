package com.archive.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppSearchField
import com.archive.app.ui.design.lucide.LucideIcons

/**
 * 原位展开的搜索框（需求：搜索按钮、原位展开、自动定位光标、不跳转页面）。
 *
 * 收起时是右对齐的搜索图标按钮，展开后在同一格里换成发现页同款的搜索框
 * （浅灰胶囊 + 放大镜 + 聚焦时右侧「取消」），点「取消」清空并收回到图标。
 *
 * 这里刻意不再用主题的通用输入框组件：Miuix 的 TextField 是按整行设置项设计的，
 * 宽度被压进工具栏里的一小格时会出现文字裁切、圆角与焦点框错位；搜索框本来就有
 * 专门的手绘实现（[AppSearchField]），复用它在两个页面表现一致。
 *
 * 调用方负责给出这一格的宽度（如 `Modifier.weight(1f)`），本组件只负责两种状态的切换。
 */
@Composable
fun ExpandableSearchField(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
) {
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        if (expanded) {
            AppSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth(),
                autoFocus = true,
                onCancel = { onExpandedChange(false) },
            )
        } else {
            AppIconButton(
                onClick = { onExpandedChange(true) },
                icon = LucideIcons.search,
                contentDescription = "搜索",
            )
        }
    }
}
