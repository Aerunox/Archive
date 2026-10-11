package com.archive.app.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 一次 AI 请求的公共出口：按接口类型拼请求（[buildRequest]）→ 发送 → 解响应（[parseReply]）。
 *
 * 翻译与图片理解共用它，所以「接口类型」在两边同时生效，不会出现一边支持一边不支持。
 * 用独立的 OkHttpClient：目标端点通常是第三方，不能带站点的 UA / Cloudflare 拦截器。
 */
private val aiHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    // 大模型整篇翻译、带图问答都比较慢，读超时给足
    .readTimeout(180, TimeUnit.SECONDS)
    .build()

private val aiJson = Json { ignoreUnknownKeys = true }

suspend fun callAi(
    settings: TranslationSettings,
    /** 本次要用的模型：翻译用文字模型，图片理解用视觉模型。 */
    model: String,
    system: String,
    prompt: String,
    history: List<AiMessage> = emptyList(),
    imageDataUrl: String? = null,
    /**
     * 测试用：把 max_tokens 压到该上限以内以控制成本。
     * 注意开了深度思考时要留够思考预算，否则回复会被截断成空。
     */
    maxTokensCap: Int? = null,
): AiReply = withContext(Dispatchers.IO) {
    val effective = maxTokensCap?.let {
        settings.copy(maxTokens = settings.maxTokens.coerceAtMost(it))
    } ?: settings

    val built = effective.apiType.buildRequest(
        settings = effective,
        model = model,
        system = system,
        history = history,
        prompt = prompt,
        imageDataUrl = imageDataUrl,
    )

    val builder = Request.Builder()
        .url(built.url)
        .header("Content-Type", "application/json")
    built.headers.forEach { (name, value) -> builder.header(name, value) }

    aiHttpClient
        .newCall(builder.post(built.body.toRequestBody("application/json".toMediaType())).build())
        .execute()
        .use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                // 带上接口类型与模型名：换过类型/模型后，这句话本身就是排查结论
                error(
                    "接口返回 ${response.code} · ${effective.apiType.label} · $model：" +
                        raw.take(200),
                )
            }
            return@withContext effective.apiType.parseReply(aiJson.parseToJsonElement(raw).jsonObject)
        }
}
