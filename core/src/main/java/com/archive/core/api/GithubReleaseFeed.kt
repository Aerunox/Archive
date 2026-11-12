package com.archive.core.api

import android.util.Xml
import com.archive.core.model.GithubRelease
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * 解析 GitHub 的 releases.atom feed（网页端点，不计入 api.github.com 的匿名配额）。
 *
 * 每条 entry 里有：
 * - id：形如 tag:github.com,2008:Repository/1407396387/v1.0.2，末段就是 tag；
 * - title：发布标题，形如「Archive 1.0.2」；
 * - updated：发布时间（ISO 8601，与 API 的 published_at 同格式）；
 * - link rel="alternate"：该发布在浏览器里的落地页（页面里就是 apk 附件）；
 * - content type="html"：更新说明，但已经是**渲染后的 HTML**（还带实体转义），
 *   和 API 返回的 Markdown 不是一回事，硬塞进 Markdown 渲染器只会显示成一堆源码 ——
 *   所以这里刻意不取它，让界面按「去发布页看详情」呈现。
 */
object GithubReleaseFeed {

    /** feed 里的发布（顺序与 feed 一致：从新到旧）。解析失败返回空列表，不抛异常。 */
    fun parse(xml: String): List<GithubRelease> = runCatching {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        val result = ArrayList<GithubRelease>()
        var event = parser.eventType
        var inEntry = false
        var tag = ""
        var name = ""
        var publishedAt = ""
        var htmlUrl = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name == "entry" -> {
                        inEntry = true
                        tag = ""
                        name = ""
                        publishedAt = ""
                        htmlUrl = ""
                    }
                    !inEntry -> Unit // feed 级的 id/title/updated 一律忽略
                    parser.name == "id" -> if (tag.isBlank()) {
                        tag = parser.nextText().trim().substringAfterLast('/')
                    }
                    parser.name == "title" -> name = parser.nextText().trim()
                    parser.name == "updated" -> publishedAt = parser.nextText().trim()
                    parser.name == "link" -> {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null && (rel == null || rel == "alternate")) htmlUrl = href.trim()
                    }
                }
                XmlPullParser.END_TAG -> if (inEntry && parser.name == "entry") {
                    inEntry = false
                    if (tag.isNotBlank()) {
                        result += GithubRelease(
                            tagName = tag,
                            name = name,
                            body = null,
                            htmlUrl = htmlUrl.ifBlank { GithubRepo.TAG_URL_PREFIX + tag },
                            publishedAt = publishedAt,
                            assets = null,
                        )
                    }
                }
            }
            event = parser.next()
        }
        result
    }.getOrElse { emptyList() }
}
