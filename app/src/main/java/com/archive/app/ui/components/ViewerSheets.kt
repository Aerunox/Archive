package com.archive.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors

/** 底部菜单里的一项：图标磁贴 + 文案（对照微信图片菜单的排布）。 */
data class ViewerSheetAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * 图片页「⋯ / 长按」菜单与它引发的两个面板：
 *
 * 1. [ViewerActionSheet]：保存原图 / 提取图中文字 / 分享（对照微信图片菜单）；
 * 2. [OcrResultSheet]：识别结果（对照微信「涂抹选择文字」，文字可长按选中）。
 *
 * OCR 的状态机都在这里，调用方只需提供「怎么识别」和「怎么保存」，
 * 分享图片（系统分享菜单）与复制文本也在这里完成。
 */
@Composable
fun ViewerImageSheetHost(
    show: Boolean,
    title: String?,
    imageUrl: String,
    /** 预览图地址：preview_only 的帖子拿不到原图，取图失败时退用它。 */
    previewUrl: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    recognize: suspend (String) -> List<String>,
    summarize: suspend (String) -> String,
    ask: suspend (String, List<com.archive.app.data.AiTurn>, String) -> String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showOcr by remember { mutableStateOf(false) }
    var ocrLoading by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf<String?>(null) }
    var ocrLines by remember { mutableStateOf<List<String>>(emptyList()) }
    // AI 面板：摘要 / 问答两个入口共用；换图自动收起，免得看到上一张图的分析结果
    var aiMode by remember(imageUrl) { mutableStateOf<AiSheetMode?>(null) }

    ViewerActionSheet(
        show = show,
        title = title,
        onDismiss = onDismiss,
        actions = listOf(
            ViewerSheetAction("保存原图", LucideIcons.downloads) {
                onDismiss()
                onSave()
            },
            ViewerSheetAction("AI 摘要", LucideIcons.info) {
                onDismiss()
                aiMode = AiSheetMode.Summary
            },
            ViewerSheetAction("图片问答", LucideIcons.messages) {
                onDismiss()
                aiMode = AiSheetMode.Ask
            },
            ViewerSheetAction("提取文字", LucideIcons.tasks) {
                onDismiss()
                showOcr = true
                ocrLoading = true
                ocrError = null
                ocrLines = emptyList()
                scope.launch {
                    try {
                        ocrLines = try {
                            recognize(imageUrl)
                        } catch (t: Throwable) {
                            // 原图拿不到（preview_only 的帖子未登录只有预览图）→ 退用预览图
                            if (previewUrl.isBlank()) throw t
                            recognize(previewUrl)
                        }.filter { line -> line.isNotBlank() }
                    } catch (t: Throwable) {
                        // Error 也要落日志：ART 的 StackOverflowError 只带一句 `stack size …KB`，
                        // 光看界面完全不知道问题出在哪
                        android.util.Log.e("Ocr", "文字识别失败", t)
                        com.archive.core.util.CrashHandler.logNonFatal(context, "OCR", t)
                        ocrError = when (t) {
                            is OutOfMemoryError -> "内存不足，换张小一点的图再试"
                            is StackOverflowError -> "识别时栈溢出（已记录日志）"
                            is java.io.IOException -> "图片下载失败，请检查网络后重试"
                            else -> t.message ?: "识别失败"
                        }
                    }
                    ocrLoading = false
                }
            },
            ViewerSheetAction("分享", LucideIcons.externalLink) {
                onDismiss()
                scope.launch { shareImage(context, imageUrl, title) }
            },
        ),
    )

    val text = ocrLines.joinToString("\n")
    OcrResultSheet(
        show = showOcr,
        loading = ocrLoading,
        error = ocrError,
        lines = ocrLines,
        onDismiss = { showOcr = false },
        onCopyAll = { copyToClipboard(context, "识别出的文字", text) },
        onShare = { shareText(context, text) },
    )

    // AI 摘要 / 图片问答：同一个面板，由菜单里的两个入口分别唤起。
    // 取图失败（preview_only 的帖子拿不到原图）时退用预览图。
    ViewerAiSheetHost(
        mode = aiMode,
        title = title,
        imageUrl = imageUrl,
        summarize = { url ->
            try {
                summarize(url)
            } catch (t: Throwable) {
                if (previewUrl.isBlank()) throw t
                summarize(previewUrl)
            }
        },
        ask = { url, turns, question ->
            try {
                ask(url, turns, question)
            } catch (t: Throwable) {
                if (previewUrl.isBlank()) throw t
                ask(previewUrl, turns, question)
            }
        },
        onDismiss = { aiMode = null },
    )
}

/**
 * 底部菜单：半透明遮罩 + 底部圆角卡片（图标磁贴一行 + 取消）。
 * 查看器是整屏、不在 Miuix 的 Scaffold 里，所以用普通 Compose 遮罩实现。
 */
@Composable
fun ViewerActionSheet(
    show: Boolean,
    title: String?,
    actions: List<ViewerSheetAction>,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val colors = LocalPurrColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.surface)
                .clickable(enabled = false) {}
                .systemBarsPadding()
                .padding(top = 18.dp, bottom = 10.dp),
        ) {
            title?.let {
                AppText(
                    it,
                    style = AppTextStyle.SMALL,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.forEach { action ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = action.onClick)
                            // 一行 5 项（保存原图 / AI 摘要 / 图片问答 / 提取文字 / 分享）：
                            // 横向内边距收紧一点，窄屏上也不会把文字挤掉
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        AppText(
                            action.label,
                            style = AppTextStyle.SMALL,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainer)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText("取消", style = AppTextStyle.MAIN, color = colors.textPrimary)
            }
        }
    }
}

/**
 * 文字识别结果窗：整屏面板，识别出的文字按行铺开、**可长按选中复制**，
 * 底部是「复制全文 / 分享文字」。样式跟随主题色。
 */
@Composable
fun OcrResultSheet(
    show: Boolean,
    loading: Boolean,
    error: String?,
    lines: List<String>,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
) {
    if (!show) return
    val colors = LocalPurrColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = LucideIcons.close,
                        contentDescription = "关闭",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                AppText("识别出的文字", style = AppTextStyle.TITLE, color = colors.textPrimary)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    loading -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                        AppText(
                            "正在识别…",
                            style = AppTextStyle.SMALL,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }

                    error != null -> AppText(
                        error,
                        style = AppTextStyle.MAIN,
                        color = colors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                    )

                    lines.isEmpty() -> AppText(
                        "没有识别到文字",
                        style = AppTextStyle.MAIN,
                        color = colors.textSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> SelectionContainer {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            lines.forEach { line ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceContainer)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    AppText(line, style = AppTextStyle.MAIN, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            if (!loading && error == null && lines.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SheetActionButton("复制全文", LucideIcons.check, Modifier.weight(1f), onCopyAll)
                    SheetActionButton("分享文字", LucideIcons.externalLink, Modifier.weight(1f), onShare)
                }
            }
        }
    }
}

@Composable
private fun SheetActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
        AppText(
            label,
            style = AppTextStyle.MAIN,
            color = colors.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "分享文字")) }
}

/**
 * 分享图片本身：先把原图字节落到应用私有目录，再用 FileProvider 交给系统分享菜单。
 * （分享链接和分享图片是两回事，用户要的是后者。）
 */
private suspend fun shareImage(context: Context, url: String, title: String?) {
    if (url.isBlank()) return
    val file = com.archive.app.util.ShareFiles.cacheImage(context, url, title) ?: return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "分享图片")) }
}
