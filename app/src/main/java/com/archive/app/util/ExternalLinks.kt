package com.archive.app.util

/**
 * 从帖子正文 HTML 中抽取「外部链接」（对照 Pawchive 的
 * `extractExternalLinksFromHtml`）：取 `<a href>` 的 https 链接，跳过站点文件域名
 * （文件本身已作为附件/主图展示），按 URL 去重，标题用锚文本。
 */
object ExternalLinks {

    data class Link(val title: String, val url: String, val host: String)

    /** 站点文件域名：这些链接指向图片/附件本体，不作为外部链接列出。 */
    private val FILE_HOSTS = setOf(
        "img.pawchive.pw",
        "file.pawchive.pw",
    )

    private val ANCHOR = Regex(
        """(?is)<a\b[^>]*?href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*?>(.*?)</a>""",
    )

    fun extract(html: String?): List<Link> {
        if (html.isNullOrBlank()) return emptyList()
        val seen = linkedSetOf<String>()
        val out = mutableListOf<Link>()
        ANCHOR.findAll(html).forEach { match ->
            val href = (match.groupValues[1].ifBlank { null }
                ?: match.groupValues[2].ifBlank { null }
                ?: match.groupValues[3].ifBlank { null })?.trim().orEmpty()
            if (!href.startsWith("https://", ignoreCase = true)) return@forEach
            val host = runCatching { java.net.URI(href).host.orEmpty() }.getOrDefault("")
            if (host.isBlank()) return@forEach
            if (host.lowercase() in FILE_HOSTS) return@forEach
            // 站点自身域名的文件路径也不算外部链接
            if (href.contains("/data/") && href.contains("pawchive.pw")) return@forEach
            val key = href.trimEnd('/')
            if (!seen.add(key)) return@forEach
            val title = HtmlParser
                .htmlToPlainText(match.groupValues[4])
                .ifBlank { host }
            out += Link(title = title, url = href, host = host)
        }
        return out
    }
}
