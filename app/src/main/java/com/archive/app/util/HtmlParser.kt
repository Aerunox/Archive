package com.archive.app.util

/**
 * Minimal HTML → node parser for post bodies. Pawchive content uses a small
 * subset (p/br/b/strong/i/em/u/a/img/spans), so a purpose-built parser keeps
 * the app free of a WebView dependency and fully unit-testable.
 */
sealed interface HtmlNode {
    data class Paragraph(val spans: List<HtmlSpan>) : HtmlNode
    data class Image(val src: String) : HtmlNode
}

data class HtmlSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val link: String? = null,
)

object HtmlParser {

    fun parse(html: String): List<HtmlNode> {
        if (html.isBlank()) return emptyList()
        val cleaned = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\s*\\1\\s*>"), "")
            .replace(Regex("(?is)<!--.*?-->"), "")
            .replace("\r", "")
            .replace('\n', ' ')

        val nodes = mutableListOf<HtmlNode>()
        val spans = mutableListOf<HtmlSpan>()
        var bold = false
        var italic = false
        var underline = false
        var link: String? = null
        val buffer = StringBuilder()

        fun flushText() {
            if (buffer.isNotEmpty()) {
                spans += HtmlSpan(buffer.toString(), bold, italic, underline, link)
                buffer.clear()
            }
        }

        fun flushParagraph() {
            flushText()
            if (spans.isNotEmpty()) {
                nodes += HtmlNode.Paragraph(spans.toList())
                spans.clear()
            }
        }

        var i = 0
        while (i < cleaned.length) {
            val lt = cleaned.indexOf('<', i)
            if (lt < 0) {
                buffer.append(decodeEntities(cleaned.substring(i)))
                break
            }
            if (lt > i) buffer.append(decodeEntities(cleaned.substring(i, lt)))
            val gt = cleaned.indexOf('>', lt)
            if (gt < 0) {
                buffer.append(cleaned.substring(lt))
                break
            }
            val tag = cleaned.substring(lt + 1, gt).trim()
            i = gt + 1
            val lower = tag.lowercase()
            when {
                // 精确匹配标签名：startsWith("img")/("a") 会把 abbr/area/article/aside
                // 等合法标签也误判成 <a>/<img>，带 href 的自造标签会误开链接状态
                lower == "img" || lower.startsWith("img ") || lower.startsWith("img/") -> {
                    flushText()
                    attrOf(tag, "src")?.let { nodes += HtmlNode.Image(it) }
                }
                lower == "br" || lower.startsWith("br ") || lower == "br/" -> flushParagraph()
                lower == "p" || lower.startsWith("p ") -> flushParagraph()
                lower == "/p" -> flushParagraph()
                lower == "b" || lower == "strong" -> { flushText(); bold = true }
                lower == "/b" || lower == "/strong" -> { flushText(); bold = false }
                lower == "i" || lower == "em" -> { flushText(); italic = true }
                lower == "/i" || lower == "/em" -> { flushText(); italic = false }
                lower == "u" -> { flushText(); underline = true }
                lower == "/u" -> { flushText(); underline = false }
                lower == "a" || lower.startsWith("a ") -> { flushText(); link = attrOf(tag, "href") }
                lower == "/a" -> { flushText(); link = null }
                else -> Unit // unknown tags are dropped
            }
        }
        flushParagraph()
        return nodes
    }

    private fun attrOf(tag: String, name: String): String? {
        val re = Regex("(?i)$name\\s*=\\s*\"([^\"]*)\"")
        val m = re.find(tag) ?: Regex("(?i)$name\\s*=\\s*'([^']*)'").find(tag)
        return m?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    fun decodeEntities(text: String): String {
        // &amp; 必须最后解码：先解 &amp; 会把源文本里的 "&amp;lt;" 二次解码成 "<"，
        // 显示内容被篡改（&lt;/&gt; 等先解码后，遗留的 & 序列已不可能再组成这些实体名）。
        var out = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        // numeric character references (&#39; / &#x27; …) appear in real post bodies
        out = NUMERIC_ENTITY.replace(out) { match ->
            val value = if (match.groupValues[1].isNotEmpty()) {
                match.groupValues[1].toIntOrNull()
            } else {
                match.groupValues[2].toIntOrNull(16)
            }
            value?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: match.value
        }
        return out
    }

    private val NUMERIC_ENTITY = Regex("""&#(\d+);|&#[xX]([0-9a-fA-F]+);""")

    /**
     * HTML → 纯文本，用于列表摘要等只需要一行的场景：
     * 去掉脚本/样式与全部标签、把块级标签换成空格、解码实体、压缩空白。
     */
    fun htmlToPlainText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val withoutBlocks = html
            .replace(Regex("""(?is)<(script|style)[^>]*>.*?</\s*\1\s*>"""), " ")
            .replace(Regex("""(?is)<!--.*?-->"""), " ")
            .replace(Regex("""(?i)<br\s*/?>"""), " ")
            .replace(Regex("""(?i)</(p|div|blockquote|li|h[1-6])\s*>"""), " ")
            .replace(Regex("<[^>]*>"), " ")
        return decodeEntities(withoutBlocks)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
