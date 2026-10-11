package com.archive.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.archive.app.data.remote.UrlProvider
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors

/**
 * 创作者行（发现页统一样式）：圆形头像 + 名称 + 「服务 · 收藏 N · 更新 X」+ 右侧箭头。
 *
 * 发现页、作者详情的「相似」「关联」标签共用这一个组件，保证三处观感一致。
 *
 * @param chevronRotation 传入角度即可让箭头旋转（发现页展开态用），null 表示静态箭头。
 * @param end 额外的行尾内容（显示在箭头之前）。
 */
@Composable
fun CreatorRow(
    name: String,
    service: String,
    creatorId: String,
    baseUrl: String,
    modifier: Modifier = Modifier,
    favorited: Int? = null,
    updatedText: String? = null,
    extraSuffix: String? = null,
    chevronRotation: Float? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalPurrColors.current
    AppListItem(
        title = name,
        summary = buildString {
            append(service)
            favorited?.let { append(" · 收藏 ").append(com.archive.app.util.Fmt.count(it)) }
            updatedText?.let { append(" · 更新 ").append(it) }
            extraSuffix?.let { append(" · ").append(it) }
        },
        modifier = modifier,
        start = {
            // CreatorAvatar：头像固定不模糊（之前这里直接用了 ThumbImage，
            // 于是「模糊缩略图」开关会把整个列表的作者头像一起糊掉）
            CreatorAvatar(
                url = UrlProvider.iconUrl(baseUrl, service, creatorId),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentDescription = null,
            )
        },
        end = {
            end?.invoke(this)
            Icon(
                imageVector = LucideIcons.expander,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .padding(start = if (end != null) 4.dp else 0.dp)
                    .graphicsLayer { rotationZ = chevronRotation ?: 0f },
            )
        },
        onClick = onClick,
    )
}

/** 行尾小按钮容器（取消收藏等）。 */
@Composable
fun CreatorRowAction(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
