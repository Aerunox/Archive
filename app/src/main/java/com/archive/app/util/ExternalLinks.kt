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

    /** 从 https URL 里取 host：URI 构造对未编码字符（空格/中文）会抛异常，
     *  兜底用正则从 scheme 后截取到第一个 /、?、# 之前。 */
    private fun hostOf(href: String): String {
        val viaUri = runCatching { java.net.URI(href).host }.getOrNull()
        if (!viaUri.isNullOrBlank()) return viaUri
        val rest = href.removePrefix("https://").removePrefix("HTTPS://")
        return rest.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore('@').lowercase()
    }

    fun extract(html: String?): List<Link> {
        if (html.isNullOrBlank()) return emptyList()
        val seen = linkedSetOf<String>()
        val out = mutableListOf<Link>()
        ANCHOR.findAll(html).forEach { match ->
            val href = (match.groupValues[1].ifBlank { null }
                ?: match.groupValues[2].ifBlank { null }
                ?: match.groupValues[3].ifBlank { null })?.trim().orEmpty()
            if (!href.startsWith("https://", ignoreCase = true)) return@forEach
            val host = hostOf(href)
            if (host.isBlank()) return@forEach
            if (host in FILE_HOSTS) return@forEach
            // 站点自身域名的文件路径也不算外部链接（按 host+path 精确判断，
            // contains 会把 evil.com/?pawchive.pw/data/ 这类伪造串误伤）
            if (host == "pawchive.pw" && href.removePrefix("https://").removePrefix("pawchive.pw")
                    .substringBefore('?').substringBefore('#').contains("/data/")
            ) return@forEach
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
