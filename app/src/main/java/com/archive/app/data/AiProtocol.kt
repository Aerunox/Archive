package com.archive.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 接口类型：决定请求体怎么拼、响应怎么解、鉴权用哪个头、URL 要不要替换 `{model}`。
 *
 * 四个类型覆盖了目前主流的自建 / 云上端点；`{model}` 占位符只在把模型写进 URL 的
 * 接口（Gemini）里需要，命中就替换、没写就原样使用。
 */
enum class ApiType(
    val label: String,
    /** 下拉里的补充说明（鉴权方式 / 适用场景）。 */
    val hint: String,
    /** 地址字段下方的示例，随所选类型切换。 */
    val urlHint: String,
) {
    /** OpenAI 及绝大多数兼容端点（通义 / 智谱 / DeepSeek / 硅基流动…）。 */
    CHAT_COMPLETION(
        label = "OpenAI Chat Completion",
        hint = "通义 / 智谱 / DeepSeek 这类兼容端点都选它",
        urlHint = "…/v1/chat/completions，例如 https://api.openai.com/v1/chat/completions",
    ),

    /** OpenAI 的 Responses 接口（地址形如 `.../v1/responses`）。 */
    RESPONSES(
        label = "OpenAI Responses",
        hint = "OpenAI 新接口：输入用 input，输出在 output 数组里",
        urlHint = "…/v1/responses，例如 https://api.openai.com/v1/responses",
    ),

    /** Anthropic Messages（地址形如 `.../v1/messages`，鉴权走 `x-api-key`）。 */
    ANTHROPIC(
        label = "Anthropic Messages",
        hint = "鉴权用 x-api-key + anthropic-version",
        urlHint = "…/v1/messages，例如 https://api.anthropic.com/v1/messages",
    ),

    /** Google Gemini generateContent（模型在地址里，用 `{model}` 占位）。 */
    GEMINI(
        label = "Gemini generateContent (legacy)",
        hint = "模型写在地址里，鉴权用 x-goog-api-key",
        urlHint = "…/v1beta/models/{model}:generateContent（{model} 会替换成模型名）",
    );

    companion object {
        /** 存的是枚举名，取不到就退回默认类型（改过配置也不至于打不开）。 */
        fun fromName(name: String?): ApiType = entries.firstOrNull { it.name == name } ?: CHAT_COMPLETION

        val LABELS: List<String> = entries.map { it.label }
    }
}

/** 对话历史里的一条。system 单独传，这里只有 user / assistant 两种。 */
data class AiMessage(val fromUser: Boolean, val text: String)

/** 组装好的 HTTP 请求：地址（可能已替换 `{model}`）、请求体、需要额外加的头。 */
data class AiHttpRequest(
    val url: String,
    val body: String,
    val headers: Map<String, String>,
)

/** 解析出的回复：正文、推理内容长度、检索信号。 */
data class AiReply(
    val text: String?,
    val reasoningChars: Int = 0,
    val searchSignal: Boolean = false,
)

/**
 * 按接口类型拼请求体。
 *
 * [model] 由调用方给出（翻译用 text 模型、图片理解用 vision 模型），
 * [imageDataUrl] 为 `data:image/jpeg;base64,...`，只有多模态调用才传。
 */
fun ApiType.buildRequest(
    settings: TranslationSettings,
    model: String,
    system: String,
    history: List<AiMessage>,
    prompt: String,
    imageDataUrl: String? = null,
): AiHttpRequest = AiHttpRequest(
    url = settings.url.replace("{model}", model),
    body = when (this) {
        ApiType.CHAT_COMPLETION -> chatBody(settings, model, system, history, prompt, imageDataUrl)
        ApiType.RESPONSES -> responsesBody(settings, model, system, history, prompt, imageDataUrl)
        ApiType.ANTHROPIC -> anthropicBody(settings, model, system, history, prompt, imageDataUrl)
        ApiType.GEMINI -> geminiBody(settings, model, system, history, prompt, imageDataUrl)
    }.toString(),
    headers = headersFor(settings),
)

/** 从响应体里解出正文 / 推理内容长度 / 检索信号。 */
fun ApiType.parseReply(root: JsonObject): AiReply = when (this) {
    ApiType.CHAT_COMPLETION -> {
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
        AiReply(
            text = message?.textOrNull("content"),
            reasoningChars = message?.textOrNull("reasoning_content")?.length ?: 0,
            searchSignal = message?.get("search_info") != null ||
                root["search_info"] != null ||
                message?.get("tool_calls") != null,
        )
    }

    ApiType.RESPONSES -> {
        val output = root["output"]?.jsonArray.orEmpty()
        val text = output
            .filter { it.jsonObject.typeOrNull() == "message" }
            .flatMap { it.jsonObject["content"]?.jsonArray.orEmpty() }
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n")
            .trim()
        AiReply(
            text = text.takeIf { it.isNotEmpty() },
            // reasoning 项里是 summary 数组，不是 text
            reasoningChars = output
                .filter { it.jsonObject.typeOrNull() == "reasoning" }
                .sumOf { item ->
                    item.jsonObject["summary"]?.jsonArray.orEmpty()
                        .sumOf { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.length ?: 0 }
                },
            searchSignal = output.any { (it.jsonObject.typeOrNull() ?: "").contains("search") } ||
                root["search_info"] != null,
        )
    }

    ApiType.ANTHROPIC -> {
        val blocks = root["content"]?.jsonArray.orEmpty().map { it.jsonObject }
        val text = blocks
            .filter { it.typeOrNull() == "text" }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n")
            .trim()
        AiReply(
            text = text.takeIf { it.isNotEmpty() },
            reasoningChars = blocks
                .filter { it.typeOrNull() == "thinking" }
                .sumOf { it["thinking"]?.jsonPrimitive?.contentOrNull?.length ?: 0 },
            searchSignal = blocks.any { (it.typeOrNull() ?: "").contains("web_search") } ||
                root["usage"]?.jsonObject?.get("server_tool_use") != null,
        )
    }

    ApiType.GEMINI -> {
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts
            // 注意：思考片段是靠 parts[].thought=true 标记的，不是 type 字段
            .filter { !it.jsonObject.thoughtFlag() }
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()
        AiReply(
            text = text.takeIf { it.isNotEmpty() },
            // Gemini 把思考内容放在 parts 里、以 thought=true 标记
            reasoningChars = parts
                .filter { it.jsonObject.thoughtFlag() }
                .sumOf { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.length ?: 0 },
            searchSignal = candidate?.get("groundingMetadata") != null,
        )
    }
}

// ── 请求体 ──

private fun chatBody(
    settings: TranslationSettings,
    model: String,
    system: String,
    history: List<AiMessage>,
    prompt: String,
    image: String?,
): JsonObject {
    val base = buildJsonObject {
        put("model", model)
        put("temperature", settings.temperature)
        put("top_p", settings.topP)
        put("max_tokens", settings.maxTokens)
        put("frequency_penalty", settings.frequencyPenalty)
        put("presence_penalty", settings.presencePenalty)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", system)
            }
            history.forEach { message ->
                addJsonObject {
                    put("role", if (message.fromUser) "user" else "assistant")
                    put("content", message.text)
                }
            }
            add(
                buildJsonObject {
                    put("role", "user")
                    if (image == null) {
                        // 纯文本优先用字符串形式：兼容性最好（部分端点不接受 content 数组）
                        put("content", prompt)
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", image) }
                            }
                        }
                    }
                },
            )
        }
    }
    // 深度思考 / 思考等级 / 联网搜索（通义、智谱这些兼容端点的字段）
    return JsonObject(base + settings.advancedParams())
}

private fun responsesBody(
    settings: TranslationSettings,
    model: String,
    system: String,
    history: List<AiMessage>,
    prompt: String,
    image: String?,
): JsonObject = buildJsonObject {
    put("model", model)
    put("instructions", system)
    put("temperature", settings.temperature)
    put("top_p", settings.topP)
    // Responses 用 max_output_tokens，且不支持 frequency/presence_penalty
    put("max_output_tokens", settings.maxTokens)
    putJsonArray("input") {
        history.forEach { message ->
            addJsonObject {
                put("role", if (message.fromUser) "user" else "assistant")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", if (message.fromUser) "input_text" else "output_text")
                        put("text", message.text)
                    }
                }
            }
        }
        addJsonObject {
            put("role", "user")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "input_text")
                    put("text", prompt)
                }
                image?.let {
                    addJsonObject {
                        put("type", "input_image")
                        put("image_url", it)
                    }
                }
            }
        }
    }
    // 思考等级在这里是 reasoning.effort（low/medium/high）
    if (settings.deepThinking) {
        putJsonObject("reasoning") { put("effort", effortOf(settings.thinkingLevel)) }
    }
    if (settings.webSearch) {
        putJsonArray("tools") { addJsonObject { put("type", "web_search") } }
    }
    if (settings.frequencyPenalty != 0f) {
        // 端点不支持时会整请求报错，所以只在用户真的改过这项时才带
        put("frequency_penalty", settings.frequencyPenalty)
    }
}

private fun anthropicBody(
    settings: TranslationSettings,
    model: String,
    system: String,
    history: List<AiMessage>,
    prompt: String,
    image: String?,
): JsonObject = buildJsonObject {
    put("model", model)
    put("max_tokens", settings.maxTokens)
    // Anthropic 规定 temperature 与 top_p 互斥（同时传必 400
    // "top_p may not be set when temperature is set"），只带 temperature。
    put("temperature", settings.temperature)
    put("system", system)
    putJsonArray("messages") {
        history.forEach { message ->
            addJsonObject {
                put("role", if (message.fromUser) "user" else "assistant")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", message.text)
                    }
                }
            }
        }
        addJsonObject {
            put("role", "user")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "text")
                    put("text", prompt)
                }
                image?.let {
                    addJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64")
                            put("media_type", mediaTypeOf(it))
                            put("data", base64Of(it))
                        }
                    }
                }
            }
        }
    }
    if (settings.deepThinking) {
        // Anthropic 的思考开关与预算
        putJsonObject("thinking") {
            put("type", "enabled")
            put("budget_tokens", budgetOf(settings.thinkingLevel))
        }
    }
    if (settings.webSearch) {
        // 服务端内置工具
        putJsonArray("tools") {
            addJsonObject {
                put("type", WEB_SEARCH_TOOL_VERSION)
                put("name", "web_search")
                put("max_uses", 5)
            }
        }
    }
}

private fun geminiBody(
    settings: TranslationSettings,
    model: String,
    system: String,
    history: List<AiMessage>,
    prompt: String,
    image: String?,
): JsonObject = buildJsonObject {
    putJsonObject("systemInstruction") {
        putJsonArray("parts") { addJsonObject { put("text", system) } }
    }
    putJsonArray("contents") {
        history.forEach { message ->
            addJsonObject {
                put("role", if (message.fromUser) "user" else "model")
                putJsonArray("parts") { addJsonObject { put("text", message.text) } }
            }
        }
        addJsonObject {
            put("role", "user")
            putJsonArray("parts") {
                addJsonObject { put("text", prompt) }
                image?.let {
                    addJsonObject {
                        putJsonObject("inline_data") {
                            put("mime_type", mediaTypeOf(it))
                            put("data", base64Of(it))
                        }
                    }
                }
            }
        }
    }
    putJsonObject("generationConfig") {
        put("temperature", settings.temperature)
        put("topP", settings.topP)
        put("maxOutputTokens", settings.maxTokens)
        if (settings.deepThinking) {
            putJsonObject("thinkingConfig") { put("thinkingBudget", budgetOf(settings.thinkingLevel)) }
        }
    }
    if (settings.webSearch) {
        putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
    }
}

// ── 鉴权 ──

/** 各家的鉴权头不同；key 留空（自建端点可能免鉴权）就不带鉴权头。 */
private fun headersFor(settings: TranslationSettings): Map<String, String> = buildMap {
    if (settings.apiType == ApiType.ANTHROPIC) {
        put("anthropic-version", ANTHROPIC_VERSION)
    }
    if (settings.apiKey.isBlank()) return@buildMap
    when (settings.apiType) {
        ApiType.ANTHROPIC -> put("x-api-key", settings.apiKey)
        ApiType.GEMINI -> put("x-goog-api-key", settings.apiKey)
        else -> put("Authorization", "Bearer ${settings.apiKey}")
    }
}

/** 小工具：把一个 `data:<mime>;base64,<data>` 拆开，Anthropic / Gemini 要求分开传。 */
private fun mediaTypeOf(dataUrl: String): String =
    dataUrl.removePrefix("data:").substringBefore(";").ifBlank { "image/jpeg" }

private fun base64Of(dataUrl: String): String = dataUrl.substringAfter(",", dataUrl)

private fun JsonObject.textOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.typeOrNull(): String? = this["type"]?.jsonPrimitive?.contentOrNull

private fun JsonObject.thoughtFlag(): Boolean =
    this["thought"]?.jsonPrimitive?.contentOrNull == "true"

private const val ANTHROPIC_VERSION = "2023-06-01"
private const val WEB_SEARCH_TOOL_VERSION = "web_search_20250305"

/** 空列表当空数组用（`?.orEmpty()` 对 JsonArray 不适用）。 */
private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
