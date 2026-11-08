package com.archive.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.archive.app.data.TranslationClient
import com.archive.app.data.forPurpose
import com.archive.app.data.TranslationSettings
import com.archive.app.data.TranslationSettingsRepository
import com.archive.app.ui.design.AppSpinner
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 帖子翻译的状态与动作。
 *
 * 译文是**结构化的**（标题 + 正文 HTML），界面把原文**原地替换**成译文、复用同一个
 * HTML 渲染器，所以段落划分、标题层级、列表符号、换行与原文完全一致；
 * 「逐句对应、不得增删或调序」由客户端下发的结构契约约束（见 `TranslationClient`）。
 */
@HiltViewModel
class PostTranslationViewModel @Inject constructor(
    repository: TranslationSettingsRepository,
    private val client: TranslationClient,
) : ViewModel() {

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TranslationSettings(),
    )

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    /** 译文标题 / 正文 HTML；为 null 表示当前显示的是原文。 */
    val translatedTitle = MutableStateFlow<String?>(null)
    val translatedHtml = MutableStateFlow<String?>(null)

    /** 已翻译内容指纹：同一篇重复点「显示翻译」不会重复请求。 */
    private var sourceKey: String? = null

    val translating: Boolean get() = translatedHtml.value != null

    /** 开启翻译：原文（标题 + 正文 HTML）→ 同结构译文。 */
    fun translate(title: String, contentHtml: String) {
        if (loading.value) return
        val key = title + "\u0000" + contentHtml
        if (translatedHtml.value != null && sourceKey == key) return

        val current = settings.value
        if (!current.configured) {
            error.value = "还没配置翻译接口：点右侧齿轮填地址与模型"
            return
        }
        loading.value = true
        error.value = null
        viewModelScope.launch {
            // 用途指派：翻译帖子 → 用户指派的档案（没管理档案时就是旧的单套配置）
            client.translatePost(
                title = title,
                contentHtml = contentHtml,
                settings = current.forPurpose(com.archive.app.data.AiPurpose.TRANSLATION),
            )
                .onSuccess { result ->
                    // 与原文逐字符比对（忽略空白）：完全相同说明模型根本没翻。
                    // 这时绝不能把按钮切成「显示原文」装作成功 —— 那会变成「点了没反应」的假象。
                    val same = result.html.filterNot { c -> c.isWhitespace() } ==
                        contentHtml.filterNot { c -> c.isWhitespace() }
                    android.util.Log.i(
                        "Translate",
                        "译文 ${result.html.length} 字 / 原文 ${contentHtml.length} 字 / 相同=$same；" +
                            "译文开头：${result.html.take(120)}",
                    )
                    if (same) {
                        error.value = "模型返回的译文与原文一致，可能没有执行翻译；请换用更强的模型或重试"
                    } else {
                        translatedTitle.value = result.title
                        translatedHtml.value = result.html
                        sourceKey = key
                    }
                }
                .onFailure { error.value = it.message ?: "翻译失败" }
            loading.value = false
        }
    }

    /** 切回原文（译文留在内存里，再次点「显示翻译」不会重新请求）。 */
    fun showOriginal() {
        translatedTitle.value = null
        translatedHtml.value = null
        error.value = null
    }
}

/**
 * 「打开帖子翻译设置」的全局入口：由根组件提供（那里握有 NavController）。
 * 翻译区位于帖子正文的深层组合里，用 CompositionLocal 传比逐层透传回调干净
 * （与 `LocalMainTabSwitcher` 的做法一致）。
 */
val LocalOpenTranslationSettings =
    androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * 粗略的源语言猜测：只按文字体系判断，给「翻译自 XX」这行提示用，够准就够了。
 * 有假名算日语、有谚文算韩语、有西里尔算俄语、有汉字算中文，其余（拉丁字母）按英语。
 */
internal fun guessSourceLanguage(text: String): String {
    var kana = false
    var hangul = false
    var cyrillic = false
    var han = false
    for (c in text) {
        when (c) {
            in '\u3040'..'\u30FF' -> kana = true // 平假名 / 片假名
            in '\uAC00'..'\uD7AF' -> hangul = true // 谚文音节
            in '\u0400'..'\u04FF' -> cyrillic = true // 西里尔字母
            in '\u3400'..'\u9FFF' -> han = true // 汉字（含扩展 A）
        }
    }
    return when {
        kana -> "日语"
        hangul -> "韩语"
        cyrillic -> "俄语"
        han -> "中文"
        else -> "英语"
    }
}

/**
 * 帖子正文上方的翻译横幅（对照 X 译帖的「文A 翻译自英语 显示原文 ⚙」）。
 *
 * 这里只放横幅：译文由正文与标题**原地替换**渲染（见 PostDetailScreen），
 * 所以段落结构与原文完全对齐，不会额外多出一段排版不一致的文字。
 * 蓝色链接是开关：翻译前「显示翻译」，翻译完切「显示原文」；齿轮进翻译设置。
 */
@Composable
fun PostTranslationSection(
    title: String,
    contentHtml: String,
    modifier: Modifier = Modifier,
    /** 显式传入优先；不传则用根组件提供的 [LocalOpenTranslationSettings]。 */
    onOpenSettings: (() -> Unit)? = null,
) {
    if (title.isBlank() && contentHtml.isBlank()) return
    val viewModel: PostTranslationViewModel = hiltViewModel()
    val translatedHtml by viewModel.translatedHtml.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = LocalPurrColors.current

    // 自动翻译：进入帖子（或切换开关）时自动开始 —— translate 内部有同篇去重与并发守卫，
    // 所以这里重复触发是安全的；翻译进行中再把开关关掉不会中断已经在跑的请求
    androidx.compose.runtime.LaunchedEffect(title, contentHtml, settings.autoTranslate) {
        if (settings.autoTranslate) viewModel.translate(title, contentHtml)
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = LucideIcons.languages,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            AppText(
                "翻译自" + guessSourceLanguage(title + contentHtml),
                style = AppTextStyle.SECONDARY,
                color = colors.textTertiary,
            )
            if (loading) {
                AppSpinner(size = 12.dp, color = colors.primary)
            }
            AppText(
                when {
                    translatedHtml != null -> "显示原文"
                    loading -> "翻译中…"
                    else -> "显示翻译"
                },
                style = AppTextStyle.SECONDARY,
                color = colors.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !loading) {
                        if (translatedHtml != null) {
                            viewModel.showOriginal()
                        } else {
                            viewModel.translate(title, contentHtml)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            // 齿轮：直接进「帖子翻译」设置（对照参考图链接右侧的设置图标）
            (onOpenSettings ?: LocalOpenTranslationSettings.current)?.let { open ->
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = open)
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = LucideIcons.settings,
                        contentDescription = "翻译设置",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        error?.let {
            AppText(
                it,
                style = AppTextStyle.SMALL,
                color = colors.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
