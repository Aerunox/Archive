package com.archive.app.util

/**
 * 更新日志（GitHub Release 的 Markdown）→ 应用内富文本能渲染的 HTML 子集。
 *
 * 只输出 `HtmlParser` 真正认识的标签（`br` / `b` / `i` / `u` / `a`）：
 * 标题转成加粗、列表转成「• 」、行内代码转成加粗（解析器没有 `tt`）、引用转成斜体。
 * 旧实现会产出 `<small>` / `<big>` / `<tt>` / `<blockquote>`，这些标签会被解析器**直接丢弃**，
 * 结果是更新日志的层次感全没了 —— 转换规则跟着解析器的能力走，才不会白写。
 *
 * 文本先做 HTML 转义（解析器会解码 `&amp;` 这类实体），避免日志里的 `<` `&` 被当成标签。
 */
object UpdateNotes {

    fun toHtml(markdown: String): String {
        if (markdown.isBlank()) return ""
        val out = StringBuilder()
        markdown.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> out.append("<br>")
                line.startsWith("#") ->
                    out.append("<br><b>")
                        .append(inline(line.trimStart('#').trim()))
                        .append("</b><br>")
                line.startsWith("- ") || line.startsWith("* ") ->
                    out.append("• ").append(inline(line.substring(2))).append("<br>")
                line.startsWith("> ") ->
                    out.append("<i>").append(inline(line.substring(2))).append("</i><br>")
                else -> out.append(inline(line)).append("<br>")
            }
        }
        return out.toString()
    }

    /** 行内语法：**粗体** / *斜体* / `代码` / [文字](链接)。 */
    private fun inline(text: String): String {
        var out = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        out = out.replace(LINK, "<a href=\"$2\">$1</a>")
        out = out.replace(BOLD_STAR, "<b>$1</b>")
        out = out.replace(BOLD_UNDERSCORE, "<b>$1</b>")
        out = out.replace(CODE, "<b>$1</b>")
        out = out.replace(ITALIC_STAR, "<i>$1</i>")
        out = out.replace(ITALIC_UNDERSCORE, "<i>$1</i>")
        return out
    }

    private val LINK = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val BOLD_STAR = Regex("""\*\*(.+?)\*\*""")
    private val BOLD_UNDERSCORE = Regex("""__(.+?)__""")
    private val CODE = Regex("""`([^`]+)`""")
    private val ITALIC_STAR = Regex("""\*(.+?)\*""")
    private val ITALIC_UNDERSCORE = Regex("""_(.+?)_""")
}
