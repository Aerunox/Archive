package com.archive.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.archive.core.api.ApiClient
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Request

/** 图片问答的一轮：用户问 + 模型答（多模态接口本身不存会话，历史要每次回传）。 */
data class AiTurn(val question: String, val answer: String)

/**
 * 图片理解：「AI 摘要」与「图片问答」，走与翻译同一个接口（含同一个 [TranslationSettings.apiType]），
 * 只是模型换成多模态的（[TranslationSettings.visionModel]，留空则用 [TranslationSettings.model]）。
 *
 * 职责分得很清楚：这里只负责**把图取下来并编码**（缩放 → JPEG → base64 data URL），
 * 至于图片在请求体里长什么样（OpenAI 的 `image_url`、Anthropic 的 `source.base64`、
 * Gemini 的 `inline_data`）由 `AiProtocol` 按接口类型处理。
 *
 * 取图走 [ApiClient.sharedOkHttpClient]：站点子域只认应用专属 UA，换默认 HTTP 栈会 403；
 * 同一张图的 data URL 会缓存 —— 问答要来回多轮，不能每问一句就重下一次图。
 */
@Singleton
class VisionClient @Inject constructor() {

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedDataUrl: String? = null

    /** 概括图片内容，用于「AI 摘要」。 */
    suspend fun summarize(url: String, settings: TranslationSettings): Result<String> =
        runCatching {
            requireConfigured(settings)
            val reply = callAi(
                settings = settings,
                model = visionModel(settings),
                system = SUMMARY_SYSTEM,
                prompt = SUMMARY_PROMPT,
                imageDataUrl = dataUrl(url),
            )
            reply.text ?: error("响应里没有可用的内容")
        }

    /** 针对图片提问；[history] 是此前已完成的问答，用于多轮追问。 */
    suspend fun ask(
        url: String,
        history: List<AiTurn>,
        question: String,
        settings: TranslationSettings,
    ): Result<String> = runCatching {
        requireConfigured(settings)
        val reply = callAi(
            settings = settings,
            model = visionModel(settings),
            system = QA_SYSTEM,
            prompt = question,
            // 多轮历史按 user / assistant 交替回传
            history = history.flatMap {
                listOf(AiMessage(fromUser = true, text = it.question), AiMessage(false, it.answer))
            },
            imageDataUrl = dataUrl(url),
        )
        reply.text ?: error("响应里没有可用的内容")
    }

    /** 识图测试的结果：模型读回的文字、期望的随机数字、是否读出。 */
    data class VisionTestResult(val readback: String, val expected: String, val matched: Boolean)

    /**
     * 识图测试：现画一张「白底黑字 + 随机四位数」的图让模型读回来。
     *
     * 之所以不拿站点图片测：那样测不出「模型是不是真的在看图」—— 只回一句「我看到了图片」
     * 也会被判为成功。随机数字对上才算通过，读不出数字基本可以断定不是视觉模型。
     */
    suspend fun testVision(settings: TranslationSettings): Result<VisionTestResult> =
        runCatching {
            requireConfigured(settings)
            val code = (1000..9999).random().toString()
            val readback = callAi(
                settings = settings,
                model = visionModel(settings),
                system = VISION_TEST_SYSTEM,
                prompt = "读出图中的字母与数字，只输出它们。",
                imageDataUrl = bitmapDataUrl(testBitmap(code)),
                // 开了深度思考时要留出思考预算，否则读出的数字会被截断
                maxTokensCap = if (settings.deepThinking) 512 else 64,
            ).text ?: error("响应里没有可用的内容")
            VisionTestResult(readback = readback, expected = code, matched = readback.contains(code))
        }

    private fun visionModel(settings: TranslationSettings): String =
        settings.visionModel.ifBlank { settings.model }

    private fun requireConfigured(settings: TranslationSettings) {
        require(settings.url.isNotBlank()) { "还没配置 AI 接口：我 → 设置 → 帖子翻译" }
        require(settings.visionModel.isNotBlank() || settings.model.isNotBlank()) {
            "还没填视觉模型：我 → 设置 → 帖子翻译 → 视觉模型"
        }
    }

    /**
     * 取图 → 缩放 → JPEG → base64 data URL（同一张图只做一次）。
     *
     * 必须在 IO 线程：下载是**阻塞**网络调用。之前直接在调用方（主线程）执行，
     * 稳定抛 `NetworkOnMainThreadException` —— 就是 AI 摘要 / 图片问答双双失败的那个原因。
     */
    private suspend fun dataUrl(url: String): String {
        cachedUrl?.let { if (it == url) return cachedDataUrl ?: error("图片编码失败") }

        val dataUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = ApiClient.sharedOkHttpClient
                .newCall(Request.Builder().url(url).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) error("图片下载失败（HTTP ${response.code}）")
                    response.body.bytes()
                }
            encodeDataUrl(toJpeg(bytes) ?: error("图片解码失败，换个格式再试"))
        }

        cachedUrl = url
        cachedDataUrl = dataUrl
        return dataUrl
    }

    /** 解码并缩到长边 [MAX_SIDE]（用 inSampleSize 的 2 的幂，不做平滑缩放，够用且省内存）。 */
    private fun toJpeg(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 测试图：640×320 白底，黑色大字「ARCHIVE」+ 随机四位数。 */
    private fun testBitmap(code: String): Bitmap {
        val width = 640
        val height = 320
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        paint.textSize = 72f
        canvas.drawText(NAME_ON_TEST_IMAGE, width / 2f, height / 2f - 10f, paint)
        paint.textSize = 104f
        canvas.drawText(code, width / 2f, height / 2f + 110f, paint)
        return bitmap
    }

    /** 位图 → base64 data URL（用完即回收）。 */
    private fun bitmapDataUrl(bitmap: Bitmap): String {
        val jpeg = try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        return encodeDataUrl(jpeg)
    }

    private fun encodeDataUrl(jpeg: ByteArray): String =
        "data:image/jpeg;base64," +
            android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)

    private companion object {
        /** 上传用的长边上限：1024 足够描述画面与问答，体积通常在几百 KB 内。 */
        const val MAX_SIDE = 1024
        const val JPEG_QUALITY = 85

        /** 识图测试图上的文字（模型读回的内容里应当包含它）。 */
        const val NAME_ON_TEST_IMAGE = "ARCHIVE"

        val VISION_TEST_SYSTEM = """
            你是识图测试助手。读出图片中出现的英文字母与数字，只输出读到的内容本身，
            不要解释、不要补充、不要复述指令。
        """.trimIndent()

        val SUMMARY_SYSTEM = """
            你是图片分析助手。用简体中文客观、简洁地概括这张图片：主体对象、场景、构图、画风，
            以及画面中清晰可见的文字。要求：
            - 只输出摘要本身，3–5 句，不要 Markdown 标题、不要列表、不要任何前后缀；
            - 只描述画面里确实能看到的内容，不确定的不要臆测；
            - 画面若为成人向题材，用中性、克制的方式描述题材与画面要素，不作评判。
        """.trimIndent()

        const val SUMMARY_PROMPT = "请概括这张图。"

        val QA_SYSTEM = """
            你是图片问答助手，只依据用户发来的图片回答。要求：
            - 用简体中文直接给答案，简洁准确，不要复述问题、不要客套；
            - 答案必须来自画面中能看到的信息；看不出来的就明确回答「图片里看不出来」，不要编造；
            - 需要指位置时，用「左上 / 中间 / 右下」这类相对方位描述。
        """.trimIndent()
    }
}
