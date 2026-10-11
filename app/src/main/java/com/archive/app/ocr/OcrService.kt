package com.archive.app.ocr

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.archive.core.api.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 文字识别服务：把网络上的图片取下来交给 [OcrEngine]（PaddleOCR 的 PP-OCR 模型）。
 *
 * - 取图走 [ApiClient.sharedOkHttpClient]：`file.pawchive.pw` 这类子域只认应用专属 UA，
 *   换默认 HTTP 栈会 403；
 * - 模型第一次用到时才从 assets 加载（约 16MB，ONNX Runtime 建会话还要一点时间），之后复用；
 * - 解码与推理都在后台线程，不占主线程。
 */
@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val lock = Mutex()

    @Volatile
    private var engine: OcrEngine? = null

    /** 正在进行中的识别数：只有为 0 才能安全关闭引擎。 */
    private val inFlight = java.util.concurrent.atomic.AtomicInteger(0)

    /** 收到释放请求时正在识别 → 挂起，等识别结束再关。 */
    @Volatile
    private var releasePending = false

    /** 识别一张网络图片，返回按阅读顺序排好的文本行。取不到图时**抛错**（含 HTTP 码）。 */
    suspend fun recognize(url: String): List<OcrEngine.Line> {
        val image = withContext(Dispatchers.IO) { loadImage(url) }
        // 先计数再取引擎：否则「取到引擎」与「开始用」之间存在窗口，
        // 恰好这时收到 trim 会把正在用的会话关掉，崩在原生层
        inFlight.incrementAndGet()
        try {
            val ocr = ensureEngine()
            return withContext(Dispatchers.Default) { ocr.recognize(image) }
        } finally {
            if (inFlight.decrementAndGet() == 0 && releasePending) releaseEngine()
        }
    }

    /**
     * 释放 OCR 引擎（ONNX Runtime 的两个会话 + 运行时 arena，都是原生内存）。
     *
     * 识字不是常用功能，但引擎一旦建好就会一直占着 —— 后台常驻内存里，它是**最容易交还**
     * 的一块：模型字节从 assets 重读只要几毫秒，重建会话几百毫秒，且只在真正识字时才付这个代价。
     * 由 Application 在退到后台 / 系统内存吃紧时调用。
     *
     * 正在识别时不关：关掉运行中的 ORT 会话会崩在原生层，所以挂个标记，等识别结束再关。
     */
    fun releaseEngine() {
        if (inFlight.get() > 0) {
            releasePending = true
            return
        }
        releasePending = false
        val old = engine ?: return
        engine = null
        runCatching { old.close() }
    }

    private suspend fun ensureEngine(): OcrEngine {
        engine?.let { return it }
        return lock.withLock {
            engine?.let { return@withLock it }
            // 新建即视为「已满足释放请求」，避免刚建好又被上一次的标记关掉
            releasePending = false
            val built = OcrEngine.create(
                OrtEnvironment.getEnvironment(),
                readAsset(DET_MODEL),
                readAsset(REC_MODEL),
                readAsset(KEYS).decodeToString(),
            )
            engine = built
            built
        }
    }

    private fun readAsset(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    /**
     * 下载并解码；失败**抛出**（带 HTTP 码），而不是返回 null。
     *
     * 之前静默返回 null 会让「图片下载失败」被当成「图里没有文字」，用户无从分辨；
     * 抛错之后，preview_only 帖子的「退用预览图」重试也有触发点。
     */
    private fun loadImage(url: String): ImageBuffer {
        val request = Request.Builder().url(url).build()
        ApiClient.sharedOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("图片下载失败（HTTP ${response.code}）")
            return decode(response.body.bytes()) ?: error("图片解码失败，换个图片再试")
        }
    }

    /** 解码成 ARGB 像素；长边限制在 [MAX_SIDE] 内，避免超大原图吃满内存。 */
    private fun decode(bytes: ByteArray): ImageBuffer? {
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
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return ImageBuffer(bitmap.width, bitmap.height, pixels)
    }

    private companion object {
        const val DET_MODEL = "ocr/ch_PP-OCRv4_det_infer.onnx"
        const val REC_MODEL = "ocr/ch_PP-OCRv4_rec_infer.onnx"
        const val KEYS = "ocr/ppocr_keys_v1.txt"

        /** 与 RapidOCR 的 `Global.max_side_len` 一致。 */
        const val MAX_SIDE = 2000
    }
}
