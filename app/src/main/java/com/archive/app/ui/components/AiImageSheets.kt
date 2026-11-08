package com.archive.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archive.app.data.AiTurn
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppSpinner
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextField
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import kotlinx.coroutines.launch

/** 图片 AI 面板的两个入口：摘要（打开即生成）与问答（打开即聚焦输入框）。 */
enum class AiSheetMode { Summary, Ask }

/**
 * 图片 AI 面板：上半是「AI 摘要」（按需自动生成），下半是「图片问答」（可多轮追问）。
 *
 * - 两个菜单入口共用同一个面板，避免两套几乎一样的界面；
 * - 状态按 [imageUrl] 记忆：换一张图就重来（摘要与问答都是针对当前这张图的）；
 * - 出错只影响各自区块，比如摘要失败仍可以直接提问；
 * - 问答的历史由调用方回传给接口（多模态接口本身不保存会话）。
 */
@Composable
fun ViewerAiSheetHost(
    mode: AiSheetMode?,
    title: String?,
    imageUrl: String,
    summarize: suspend (String) -> String,
    ask: suspend (String, List<AiTurn>, String) -> String,
    onDismiss: () -> Unit,
) {
    if (mode == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalPurrColors.current

    var summary by remember(imageUrl) { mutableStateOf<String?>(null) }
    var summaryLoading by remember(imageUrl) { mutableStateOf(false) }
    var summaryError by remember(imageUrl) { mutableStateOf<String?>(null) }
    var turns by remember(imageUrl) { mutableStateOf<List<AiTurn>>(emptyList()) }
    var question by remember(imageUrl) { mutableStateOf("") }
    var asking by remember(imageUrl) { mutableStateOf(false) }
    var askError by remember(imageUrl) { mutableStateOf<String?>(null) }

    // 失败信息要能定位问题：有些异常（内存不足、链路错误、协程被取消）`message` 就是 null，
    // 只显示「失败」等于把线索全藏起来。这里带上类名与最内层原因，同时落日志便于排查。
    fun reportFailure(tag: String, t: Throwable): String {
        android.util.Log.e("AiSheet", "$tag 失败", t)
        com.archive.core.util.CrashHandler.logNonFatal(context, tag, t)
        val own = t.message?.takeIf { it.isNotBlank() }
        val deepest = generateSequence(t.cause) { it.cause }
            .mapNotNull { it.message?.takeIf { text -> text.isNotBlank() && text != own } }
            .lastOrNull()
        return buildString {
            append(t.javaClass.simpleName)
            own?.let { append("：").append(it) }
            deepest?.let { append("（原因：").append(it.take(140)).append("）") }
        }
    }

    fun generateSummary() {
        if (summaryLoading) return
        summaryLoading = true
        summaryError = null
        scope.launch {
            summary = runCatching { summarize(imageUrl) }
                .onFailure { summaryError = reportFailure("AI 摘要", it) }
                .getOrNull()
            summaryLoading = false
        }
    }

    fun send() {
        val text = question.trim()
        if (text.isEmpty() || asking) return
        asking = true
        askError = null
        val history = turns
        scope.launch {
            runCatching { ask(imageUrl, history, text) }
                .onSuccess {
                    turns = history + AiTurn(text, it)
                    question = ""
                }
                .onFailure { askError = reportFailure("图片问答", it) }
            asking = false
        }
    }

    // 摘要入口：进来就自动分析一次（已生成过则不重复请求）
    LaunchedEffect(mode, imageUrl) {
        if (mode == AiSheetMode.Summary && summary == null && !summaryLoading) generateSummary()
    }

    // 问答入口：直接把光标放到输入框里
    val focus = remember { FocusRequester() }
    LaunchedEffect(mode, imageUrl) {
        if (mode == AiSheetMode.Ask) runCatching { focus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            // ── 顶栏：关闭 + 标题 ──
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
                AppText(
                    if (mode == AiSheetMode.Summary) "AI 摘要" else "图片问答",
                    style = AppTextStyle.TITLE,
                    color = colors.textPrimary,
                )
            }

            // ── 内容：摘要 + 问答历史（可滚动）──
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                title?.let {
                    AppText(
                        it,
                        style = AppTextStyle.SMALL,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                SectionHeader("AI 摘要")
                when {
                    summaryLoading -> LoadingRow("正在分析图片…")
                    summaryError != null -> AppText(
                        summaryError.orEmpty(),
                        style = AppTextStyle.MAIN,
                        color = colors.error,
                    )

                    summary != null -> SelectionContainer {
                        Card {
                            AppText(
                                summary.orEmpty(),
                                style = AppTextStyle.MAIN,
                                color = colors.textPrimary,
                            )
                        }
                    }

                    else -> AppText(
                        "还没有摘要",
                        style = AppTextStyle.MAIN,
                        color = colors.textSecondary,
                    )
                }
                if (!summaryLoading && summary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton("重新生成", onClick = { generateSummary() }, primary = false)
                        AppButton(
                            "复制摘要",
                            onClick = { copyToClipboard(context, "AI 摘要", summary.orEmpty()) },
                            primary = false,
                        )
                    }
                    // 文字模型收到带图请求时多数端点不报错、而是忽略图片 —— 模型会凭空编一段
                    // 「摘要」，表现就是内容与图片对不上。这里把最常见的原因直接告诉用户。
                    AppText(
                        "若摘要与图片对不上：多半是当前模型不支持看图。" +
                            "请在「我 → 设置 → 帖子翻译 → 视觉模型（图片）」填多模态模型" +
                            "（如 glm-4v-flash / qwen-vl-max / gpt-4o-mini）。",
                        style = AppTextStyle.SMALL,
                        color = colors.textTertiary,
                    )
                }

                SectionHeader("针对这张图提问")
                if (turns.isEmpty() && !asking) {
                    AppText(
                        "例如：画面里有什么文字？主角在做什么？",
                        style = AppTextStyle.SMALL,
                        color = colors.textTertiary,
                    )
                }
                turns.forEach { turn ->
                    Card {
                        AppText(
                            "问：" + turn.question,
                            style = AppTextStyle.SMALL,
                            color = colors.textTertiary,
                        )
                        Box(Modifier.padding(top = 6.dp)) {
                            SelectionContainer {
                                AppText(
                                    turn.answer,
                                    style = AppTextStyle.MAIN,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
                if (asking) LoadingRow("正在看图作答…")
                askError?.let {
                    AppText(it, style = AppTextStyle.MAIN, color = colors.error)
                }
            }

            // ── 输入区（常驻底部，避免被键盘挡掉）──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    AppTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                AppButton(
                    text = if (asking) "…" else "发送",
                    onClick = { send() },
                    primary = true,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    AppText(
        text,
        style = AppTextStyle.SMALL,
        color = LocalPurrColors.current.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun LoadingRow(text: String) {
    val colors = LocalPurrColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSpinner(size = 15.dp, color = colors.primary)
        AppText(text, style = AppTextStyle.SMALL, color = colors.textSecondary)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Card(content: @Composable () -> Unit) {
    val colors = LocalPurrColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column { content() }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
