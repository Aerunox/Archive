package com.archive.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 结构化译文：标题与正文 HTML，结构必须与原文一致。 */
data class PostTranslation(
    val title: String?,
    val html: String,
)

private const val TITLE_OPEN = "<<<TITLE"
private const val TITLE_CLOSE = "TITLE>>>"
private const val HTML_OPEN = "<<<HTML"
private const val HTML_CLOSE = "HTML>>>"

/**
 * 提示词里的占位说明。
 *
 * 用尖括号包起来是刻意为之：早先这里写的是「译文标题」「译文正文 HTML」这种**看起来像正文**
 * 的文字，小模型（实测 GLM-4-Flash）会把它当成译文照抄回来 —— 表现就是整篇帖子被替换成
 * 这两句示例。加括号 + 明确要求「替换掉」能显著降低误抄，解析层再有护栏兜底。
 */
private const val PLACEHOLDER_TITLE = "<译文标题>"
private const val PLACEHOLDER_HTML = "<译文正文 HTML>"

/**
 * 解析翻译回复。
 *
 * 契约是**哨兵分段**（`<<<TITLE` / `<<<HTML`），而不是 JSON —— 实测模型经常在 JSON 字符串里
 * 直接换行或漏转义引号，导致整个 JSON 非法（表现为译文区显示一整段原始 JSON）。
 * 哨兵之间的内容原样保留，正文里出现任何字符（引号、大括号、换行）都破坏不了解析。
 *
 * 解析按可靠性降级：
 * 1. 哨兵分段（当前契约）；
 * 2. JSON：正常解析 + 「字符串里带原始换行 / 漏转义」的正则修补（兼容模型自作主张输出 JSON）；
 * 3. 兜底：整段当正文（宁可标题不翻，也不要整篇翻不出来）。
 */
internal fun parsePostTranslation(raw: String, fallbackTitle: String): PostTranslation {
    val cleaned = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    // 护栏：模型把格式里的占位说明当成译文照抄回来了。这时它没有产出任何译文，
    // 宁可抛出明确错误让用户重试 / 换模型，也不要悄悄把整篇帖子换成占位文字。
    if (cleaned.contains(PLACEHOLDER_TITLE) || cleaned.contains(PLACEHOLDER_HTML)) {
        error("模型把输出格式的占位说明当成了译文，请换用更强的模型或重试")
    }

    // 1) 哨兵分段：最稳的一条路
    if (TITLE_OPEN in cleaned && TITLE_CLOSE in cleaned && HTML_OPEN in cleaned) {
        val title = cleaned.substringAfter(TITLE_OPEN).substringBefore(TITLE_CLOSE).trim()
        val html = cleaned
            .substringAfter(HTML_OPEN)
            .let { if (HTML_CLOSE in it) it.substringBefore(HTML_CLOSE) else it }
            .trim()
        if (html.isNotEmpty()) {
            return PostTranslation(title = title.ifBlank { fallbackTitle }, html = html)
        }
    }

    // 2) JSON（正常 / 修补）
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start >= 0 && end > start) {
        val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
        val parsed = runCatching {
            lenientJson.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
        }.getOrNull()
        val jsonHtml = parsed?.get("html")?.jsonPrimitive?.contentOrNull
        val jsonTitle = parsed?.get("title")?.jsonPrimitive?.contentOrNull
        if (!jsonHtml.isNullOrBlank()) {
            return PostTranslation(
                title = jsonTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                // 模型常在值的首尾多出换行，裁掉（正文内部的结构不受影响）
                html = jsonHtml.trim(),
            )
        }

        // 修补：模型在字符串里直接换行 / 漏转义引号 → JSON 非法。
        // 用正则把两个字段的值抠出来（html 一直取到最后一个引号，标题在 "html" 之前找）。
        val repairedHtml = Regex("\"html\"\\s*:\\s*\"(.*)\"", RegexOption.DOT_MATCHES_ALL)
            .find(cleaned)
            ?.groupValues?.get(1)
            ?.let(::unescapeJsonString)
            ?.trim()
        if (!repairedHtml.isNullOrBlank()) {
            val repairedTitle = Regex("\"title\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
                .find(cleaned.substringBefore("\"html\""))
                ?.groupValues?.get(1)
                ?.let(::unescapeJsonString)
                ?.trim()
            return PostTranslation(
                title = repairedTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                html = repairedHtml,
            )
        }
    }

    // 3) 兜底：整段当正文
    return PostTranslation(title = fallbackTitle, html = cleaned)
}

/** 反转义模型输出里最常见的 JSON 转义（占位符技巧保证替换顺序正确）。 */
private fun unescapeJsonString(value: String): String = value
    .replace("\\\\", "\u0000")
    .replace("\\n", "\n")
    .replace("\\r", "\r")
    .replace("\\t", "\t")
    .replace("\\\"", "\"")
    .replace("\u0000", "\\")

/**
 * 帖子翻译：把内容交给用户自填的 AI 接口。
 *
 * 请求怎么拼、响应怎么解、鉴权用哪个头都交给 `AiProtocol` + [callAi]（按
 * [TranslationSettings.apiType] 选择），这里只负责：
 * 「正文 HTML → 同结构 HTML 译文」这层提示词契约与结果解析。
 */
@Singleton
class TranslationClient @Inject constructor() {

    /**
     * 帖子正文的**结构保真**翻译：输入标题 + 正文 HTML，输出同结构的标题 + HTML。
     *
     * 之所以传 HTML 而不是纯文本：帖子的段落、标题层级、列表符号、编号都存在标签里，
     * 一旦转成纯文本再翻，这些结构就丢了，回来也无法「原地替换」。
     */
    suspend fun translatePost(
        title: String,
        contentHtml: String,
        settings: TranslationSettings,
    ): Result<PostTranslation> = runCatching {
        require(settings.url.isNotBlank()) { "还没填接口地址" }
        require(settings.model.isNotBlank()) { "还没填模型名" }

        val payload = buildJsonObject {
            put("title", title)
            put("html", contentHtml)
        }.toString()

        val reply = callAi(
            settings = settings,
            model = settings.model,
            system = settings.prompt + STRUCTURE_RULES,
            prompt = payload,
        )
        parsePostTranslation(
            raw = reply.text ?: error("响应里没有可用的译文"),
            fallbackTitle = title,
        )
    }

    /**
     * 连通性测试结果：模型回复，以及两个「开关是否真被端点接受」的信号。
     *
     * - [reasoningChars] > 0：响应里带回了推理内容，说明深度思考确实生效；
     * - [searchSignal]：响应里出现检索相关字段。它只能作为**正向**证据 ——
     *   各家回传方式不同，没有这些字段不代表一定没检索。
     */
    data class ConnectionTestResult(
        val reply: String,
        val reasoningChars: Int,
        val searchSignal: Boolean,
        /** 本次实际带上的「推理与联网」开关（人类可读）。 */
        val applied: List<String>,
    )

    /**
     * 连通性测试：发一条最小请求，验证「地址 / Key / 模型 / **接口类型**」这条链路。
     *
     * 走的是与翻译**同一个** [callAi]，所以当前选择的接口类型、以及「推理与联网」里
     * 打开的开关都会真的生效：测试通过即说明它们被端点接受。
     */
    suspend fun testConnection(settings: TranslationSettings): Result<ConnectionTestResult> =
        runCatching {
            require(settings.url.isNotBlank()) { "还没填接口地址" }
            require(settings.model.isNotBlank()) { "还没填模型名" }

            val reply = callAi(
                settings = settings,
                model = settings.model,
                system = "你是连通性测试助手。收到任何内容都只回复：pong",
                prompt = "ping",
                // 开了深度思考时思考过程本身要占 token：上限给 16 会被吃光导致测试误报失败
                maxTokensCap = if (settings.deepThinking) 256 else 16,
            )
            ConnectionTestResult(
                reply = reply.text ?: "（没有文本内容）",
                reasoningChars = reply.reasoningChars,
                searchSignal = reply.searchSignal,
                applied = settings.appliedFeatureLabels(),
            )
        }

    private companion object {
        /**
         * 结构契约。要求与用户提示词一起下发；输出用哨兵分段而不是 JSON ——
         * 正文里出现任何字符都破坏不了解析（JSON 曾因模型在字符串里裸换行而整段失效）。
         */
        val STRUCTURE_RULES = """

            ——— 以下是必须严格遵守的硬性要求 ———
            1. 逐句对应、**原地替换**：译文与原文句子一一对应、位置对齐，不得增、删、改写句子，
               不得调整句子或段落的顺序。
            2. 完整保留原有格式：段落划分与空行、换行、缩进、标题层级（h1–h6）、列表符号与编号体系、
               标点符号、空格，一律与原样一致；不得合并或拆分段落。
            3. 只翻译人能读的文字：HTML 标签、属性名与属性值、URL、代码、占位符、表情符号原样保留，
               不得新增、删除或移动任何标签，也不得改动标签嵌套关系。
            4. 只输出译文，不要解释、不要概括、不要补充，也不要重复原文。
            5. 输出分两段，用标记分隔。下面只说明**格式**：请把尖括号里的说明替换成你的实际译文
               （尖括号连同这些说明文字都不要出现在输出里），两个标记本身原样照抄：
               <<<TITLE
               ${PLACEHOLDER_TITLE}
               TITLE>>>
               <<<HTML
               ${PLACEHOLDER_HTML}
               HTML>>>
               除这两段外不要输出任何其他文字、注释或代码块标记。
        """.trimIndent()
    }
}
