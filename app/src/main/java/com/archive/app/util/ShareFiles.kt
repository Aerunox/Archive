package com.archive.app.util

import android.content.Context
import com.archive.core.api.ApiClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 分享用的临时图片文件：把原图取回来写到 `cacheDir/share/`，再交给 FileProvider。
 *
 * 走 [ApiClient.sharedOkHttpClient]：图片在 `file.pawchive.pw` 子域，只认应用专属 UA，
 * 换默认 HTTP 栈会 403。目录里只保留最近一次分享的文件，避免长期占地。
 */
object ShareFiles {

    suspend fun cacheImage(context: Context, url: String, title: String?): File? =
        withContext(Dispatchers.IO) {
            val bytes = fetch(url) ?: return@withContext null
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName(title, url)).apply { writeBytes(bytes) }
        }

    private fun fetch(url: String): ByteArray? = runCatching {
        ApiClient.sharedOkHttpClient.newCall(Request.Builder().url(url).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) return@use null
                // 分享只是把图写临时目录，超大原图全量读内存只会 OOM 后报"分享失败"；
                // Content-Length 预检超限直接失败
                val len = response.body.contentLength()
                if (len > MAX_SHARE_BYTES) return@use null
                response.body.bytes()
            }
    }.getOrNull()

    /** 文件名尽量沿用附件原名（分享出去对方看到的就是它），兜底用 URL 末段。 */
    private fun fileName(title: String?, url: String): String {
        val raw = title?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?')
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]"), "_").take(60)
        // cleaned 可能为空串（title 与 URL 末段同时为空/全非法），".jpg" 是隐藏文件
        return when {
            cleaned.contains('.') -> cleaned
            cleaned.isEmpty() -> "share_${System.currentTimeMillis()}.jpg"
            else -> "$cleaned.jpg"
        }
    }

    private const val MAX_SHARE_BYTES = 64L * 1024 * 1024
}
