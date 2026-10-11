package com.archive.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.Fmt
import com.archive.app.util.UpdateNotes
import com.archive.data.github.UpdateReleaseInfo

/** 用系统浏览器打开链接（更新下载页 / 发布页都走它）。 */
internal fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/**
 * 「发现新版本」提示：应用启动自动检查到新版本时弹它，也用于「关于」页里手动点开了详情。
 *
 * 一个窗口把该给的信息与入口都给全：
 * - 版本号（当前 → 最新）与发布时间；
 * - **更新内容**（GitHub Release 的更新日志，实时渲染）；
 * - 入口：「立即更新」跳发布页、「忽略此版本」之后的自动检查不再为它弹窗
 *   （手动检查不受影响）、「稍后」只是关掉。
 */
@Composable
fun UpdateAvailableDialog(
    currentVersion: String,
    latestVersion: String,
    publishedAt: String,
    notes: String,
    baseUrl: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onIgnore: (() -> Unit)? = null,
) {
    val colors = LocalPurrColors.current
    AppDialog(
        show = true,
        title = "发现新版本",
        summary = "当前 $currentVersion → 最新 $latestVersion" +
            if (publishedAt.isNotBlank()) " · 发布于 ${Fmt.isoDate(publishedAt)}" else "",
        onDismiss = onDismiss,
        confirmLabel = "立即更新",
        onConfirm = onUpdate,
        dismissLabel = "稍后",
    ) {
        AppText("更新内容", style = AppTextStyle.SMALL, color = colors.textSecondary)
        UpdateNotesBlock(notes, baseUrl, maxHeight = 260.dp)
        if (onIgnore != null) {
            AppText(
                "忽略此版本（之后的自动检查不再提示）",
                style = AppTextStyle.SMALL,
                color = colors.primary,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable { onIgnore() },
            )
        }
    }
}

/**
 * 更新日志正文：Markdown 转成应用内可渲染的 HTML 后限高滚动。
 *
 * 限高是为了「日志很长」这种情况：不限高会把对话框（或浮层）顶出屏幕，
 * 按钮就够不到了。
 */
@Composable
internal fun UpdateNotesBlock(notes: String, baseUrl: String, maxHeight: Dp) {
    val colors = LocalPurrColors.current
    if (notes.isBlank()) {
        AppText(
            "该版本没有填写更新说明，去发布页看详情。",
            style = AppTextStyle.SMALL,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(top = 6.dp)) {
        item { HtmlText(UpdateNotes.toHtml(notes), baseUrl) }
    }
}

/**
 * 应用级「发现新版本」提示浮层（启动自动检查命中时用）。
 *
 * **为什么不用 AppDialog**：Miuix 的 OverlayDialog 依赖页面级 AppScaffold 提供的 popup 宿主，
 * 而这个提示要在**任何页面之上**弹出（应用启动时），根组件那一层没有 scaffold。
 * 于是用同一套设计组件（AppCard / AppButton / AppText）自绘遮罩 + 卡片：
 * 观感与 AppDialog 一致，又能脱离 scaffold 工作。
 *
 * 交互：点遮罩 =「稍后」（关掉，不改任何设置）；弹窗内给出「立即更新」「忽略此版本」两个明确出口。
 */
@Composable
fun UpdatePromptOverlay(
    currentVersion: String,
    latestVersion: String,
    publishedAt: String,
    notes: String,
    baseUrl: String,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPurrColors.current
    val scrimInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim.copy(alpha = 0.45f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                // 卡片自己吞掉点击，避免点在卡片上也当成「点外部」
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            AppText(
                "发现新版本",
                style = AppTextStyle.TITLE,
                fontWeight = FontWeight.SemiBold,
            )
            AppText(
                "当前 $currentVersion → 最新 $latestVersion" +
                    if (publishedAt.isNotBlank()) " · 发布于 ${Fmt.isoDate(publishedAt)}" else "",
                style = AppTextStyle.SMALL,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            AppText(
                "更新内容",
                style = AppTextStyle.SMALL,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            UpdateNotesBlock(notes, baseUrl, maxHeight = 240.dp)
            AppText(
                "忽略此版本（之后的自动检查不再提示）",
                style = AppTextStyle.SMALL,
                color = colors.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { onIgnore() },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(text = "稍后", onClick = onDismiss, primary = false)
                Box(Modifier.padding(start = 8.dp)) {
                    AppButton(text = "立即更新", onClick = onUpdate, primary = true)
                }
            }
        }
    }
}

/**
 * 「更新记录」：仓库里最近若干条发布，逐条给出**版本号 · 发布时间 · 更新日志**。
 *
 * 列表限高后内部滚动（发布记录可能十几条，长了会把对话框撑出屏幕）。
 */
@Composable
fun UpdateHistoryDialog(
    loading: Boolean,
    failed: Boolean,
    releases: List<UpdateReleaseInfo>,
    baseUrl: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalPurrColors.current
    val context = LocalContext.current
    AppDialog(
        show = true,
        title = "更新记录",
        summary = "来自项目仓库的发布记录",
        onDismiss = onDismiss,
        dismissLabel = "关闭",
    ) {
        when {
            loading -> AppLoading()
            releases.isEmpty() -> AppText(
                if (failed) "拉取发布记录失败：检查网络，或仓库还没有发布版本。" else "暂无发布记录。",
                style = AppTextStyle.SMALL,
                color = colors.textSecondary,
            )
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(releases.size) { index ->
                    val release = releases[index]
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                release.version,
                                style = AppTextStyle.MAIN,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (release.prerelease) {
                                AppText(
                                    "预发布",
                                    style = AppTextStyle.SMALL,
                                    color = colors.error,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Box(Modifier.weight(1f))
                            AppText(
                                Fmt.isoDate(release.publishedAt),
                                style = AppTextStyle.SMALL,
                                color = colors.textTertiary,
                            )
                        }
                        if (release.notes.isNotBlank()) {
                            HtmlText(
                                UpdateNotes.toHtml(release.notes),
                                baseUrl,
                                Modifier.padding(top = 6.dp),
                            )
                        }
                        AppText(
                            "在浏览器中打开 ›",
                            style = AppTextStyle.SMALL,
                            color = colors.primary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { openUrl(context, release.url) },
                        )
                    }
                    if (index != releases.lastIndex) AppDivider()
                }
            }
        }
    }
}
