package com.archive.app.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archive.app.data.remote.UrlProvider
import com.archive.app.domain.PostSummary
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.Fmt

/**
 * 帖子卡片：布局对照 X（Twitter）的信息流卡片——
 *
 * ```
 * (◯ 头像)  作者名 · 服务 · 时间
 *           标题（最多 3 行）
 *           摘要（灰色，最多 2 行）
 *           [ 全宽缩略图，圆角 ]
 *           [ 分享 · 打开作者 · 收藏 ]   ← 底部操作行
 * ─────────────────────────────────────
 * ```
 *
 * 卡片不使用卡片背景与投影，条目之间用细分隔线区分（X 的信息流观感）。
 * [compact] 为网格视图的紧凑形态（大图 + 标题）。
 */
@Composable
fun PostCard(
    post: PostSummary,
    modifier: Modifier = Modifier,
    showCreator: Boolean = true,
    compact: Boolean = false,
    preferOriginal: Boolean = false,
    isFavorite: Boolean? = null,
    onFavorite: (() -> Unit)? = null,
    onOpenCreator: (() -> Unit)? = null,
    /** 被屏蔽时是否隐藏自己。屏蔽管理页要原样展示被屏蔽的内容，那边传 false。 */
    hideWhenBlocked: Boolean = true,
    onClick: () -> Unit,
) {
    // 已屏蔽的作者 / 已屏蔽的帖子：当场消失，不用等列表重新加载（两份名单各自独立）
    val blockedCreators by com.archive.app.data.BlockedContentFilter.blockedCreators
        .collectAsStateWithLifecycle()
    val blockedPosts by com.archive.app.data.BlockedContentFilter.blockedPosts
        .collectAsStateWithLifecycle()
    if (hideWhenBlocked) {
        if ((post.service to post.creatorId) in blockedCreators) return
        if (com.archive.app.data.BlockedPostKey(post.service, post.creatorId, post.postId) in blockedPosts) {
            return
        }
    }

    val settings = LocalAppSettings.current
    val colors = LocalPurrColors.current
    val context = LocalContext.current
    // 本地收藏：全局提供（未提供时按钮不显示）
    val localStore = com.archive.app.ui.LocalLocalStore.current
    val scope = rememberCoroutineScope()
    val localFavorite by remember(localStore, post.service, post.creatorId, post.postId) {
        localStore?.observeFavorite(post.service, post.creatorId, post.postId) ?: kotlinx.coroutines.flow.flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteOn = isFavorite ?: localFavorite
    val favoriteActions = com.archive.app.ui.LocalFavoriteActions.current
    val favoriteAction: (() -> Unit)? = onFavorite ?: favoriteActions?.let { actions ->
        {
            scope.launch {
                actions.toggle(
                    com.archive.app.data.FavoriteRecord(
                        service = post.service,
                        creatorId = post.creatorId,
                        creatorName = post.creatorId,
                        postId = post.postId,
                        title = post.title,
                        thumbPath = post.cover?.path,
                    ),
                )
            }
            Unit
        }
    }
    val cover = post.cover
    val coverPath = cover?.path
    val coverUrl = coverPath?.let { UrlProvider.thumbnailUrl(settings.baseUrl, it) }
    val originalUrl = coverPath
        ?.takeIf { preferOriginal }
        ?.let { UrlProvider.fileUrl(settings.baseUrl, it) }
    val avatarUrl = UrlProvider.iconUrl(settings.baseUrl, post.service, post.creatorId)
    // 帖子接口不返回昵称：解析作者显示名（未解析出时回退为 id）
    val displayName = rememberCreatorDisplayName(post.service, post.creatorId)

    fun share() {
        val link = settings.baseUrl.trimEnd('/') + "/" + post.service + "/user/" +
            post.creatorId + "/post/" + post.postId
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, post.title + "\n" + link)
                    },
                    "分享帖子",
                ),
            )
        }
    }

    if (compact) {
        Column(
            modifier
                .fillMaxWidth()
                // 长按进入多选 / 多选模式下点击即选中（见 PostSelection.kt）
                .postSelection(post, onClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            coverUrl?.let { url ->
                ThumbImage(
                    url = url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentDescription = post.title,
                    originalUrl = originalUrl,
                )
            }
            AppText(
                post.title,
                style = AppTextStyle.MAIN,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            AppText(
                Fmt.relativeIso(post.published),
                style = AppTextStyle.SMALL,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                // 长按进入多选 / 多选模式下点击即选中（见 PostSelection.kt）
                .postSelection(post, onClick)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        ) {
            // 头像（点击进作者主页）
            CreatorAvatar(
                url = avatarUrl,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = onOpenCreator != null) { onOpenCreator?.invoke() },
                contentDescription = null,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                // 名称 · 服务 · 时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCreator) {
                        AppText(
                            displayName,
                            style = AppTextStyle.MAIN,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    AppText(
                        " · " + post.service + " · " + Fmt.relativeIso(post.published),
                        style = AppTextStyle.SMALL,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 标题（正文）
                AppText(
                    post.title,
                    style = AppTextStyle.MAIN,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )

                // 摘要
                if (!post.excerpt.isNullOrBlank()) {
                    AppText(
                        post.excerpt!!,
                        style = AppTextStyle.SECONDARY,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // 全宽缩略图
                coverUrl?.let { url ->
                    ThumbImage(
                        url = url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(min = 140.dp, max = 240.dp)
                            .aspectRatio(16f / 9f),
                        contentDescription = post.title,
                        originalUrl = originalUrl,
                        cornerRadius = 16,
                    )
                }

                // 底部操作行
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RowAction(
                        icon = LucideIcons.externalLink,
                        contentDescription = "分享",
                        onClick = { share() },
                    )
                    if (onOpenCreator != null) {
                        RowAction(
                            icon = LucideIcons.mine,
                            contentDescription = "打开作者主页",
                            onClick = onOpenCreator,
                        )
                    }
                    favoriteAction?.let { action ->
                        RowAction(
                            icon = if (favoriteOn) LucideIcons.favoritesFill else LucideIcons.favorites,
                            contentDescription = if (favoriteOn) "取消收藏" else "收藏",
                            tint = if (favoriteOn) colors.primary else colors.textTertiary,
                            onClick = action,
                        )
                    }
                }
            }
        }
        // 帖子之间的分割线与屏幕同宽
        AppDivider()
    }
}

/** 操作行里的单个图标（X 的底部动作条样式：小图标 + 足够点击区域）。 */
@Composable
private fun RowAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val colors = LocalPurrColors.current
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
