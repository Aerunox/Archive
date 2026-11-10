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

    /**
     * 实体解码（单遍扫描）。
     *
     * 命名实体与数字字符引用放在同一个正则里交替匹配，这是正确性要求而不是风格：
     * 顺序 replace 会把「显示为字面量的转义」二次解码——
     * 源文本 `&amp;lt;`（应显示 "&lt;"）先解 &lt; 得 `&amp;<`、再解 &amp; 变 "&<"；
     * `&amp;#39;`（应显示 "&#39;"）两段管线下最终变成 "'"。单遍匹配到 `&amp;` 即停，
     * 其后的内容按普通文本保留，两类复合转义都不会被篡改。
     */
    fun decodeEntities(text: String): String =
        ENTITY.replace(text) { match ->
            when (val entity = match.value) {
                "&nbsp;" -> " "
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&apos;" -> "'"
                "&amp;" -> "&"
                else -> {
                    // 数字字符引用（&#39; / &#x27; …），真实帖子正文里出现过
                    val body = entity.substring(2, entity.length - 1)
                    val value = if (body[0] == 'x' || body[0] == 'X') {
                        body.substring(1).toIntOrNull(16)
                    } else {
                        body.toIntOrNull()
                    }
                    value?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: entity
                }
            }
        }

    private val ENTITY = Regex("&nbsp;|&lt;|&gt;|&quot;|&apos;|&amp;|&#\\d+;|&#[xX][0-9a-fA-F]+;")

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
