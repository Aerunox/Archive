package com.archive.app

import com.archive.app.util.UpdateNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新日志转换：输出的标签必须是应用内 `HtmlParser` 认识的那几个（br / b / i / u / a）。
 *
 * 背景：旧实现输出 `<small>` / `<big>` / `<tt>` / `<blockquote>`，而解析器遇到不认识的标签
 * 会**直接丢掉**，更新日志的层次就全没了。这条用例把「只输出认识的标签」钉住。
 */
class UpdateNotesTest {

    @Test
    fun `只输出解析器认识的标签`() {
        val html = UpdateNotes.toHtml(
            """
            ## 新增
            - 全屏播放
            - 更新设置模块

            > 注意：需要重新登录
            普通段落 **加粗** 与 `代码`
            """.trimIndent(),
        )
        val allowed = setOf("br", "b", "i", "u", "a")
        Regex("""</?([a-zA-Z]+)""").findAll(html).forEach { match ->
            val tag = match.groupValues[1].lowercase()
            assertTrue("输出了解析器不认识的标签 <$tag>：$html", tag in allowed)
        }
    }

    @Test
    fun `标题转加粗 列表转圆点 引用转斜体`() {
        assertEquals("<br><b>新增</b><br>", UpdateNotes.toHtml("## 新增"))
        assertEquals("• 全屏播放<br>", UpdateNotes.toHtml("- 全屏播放"))
        assertEquals("<i>注意</i><br>", UpdateNotes.toHtml("> 注意"))
    }

    @Test
    fun `行内语法与链接`() {
        assertEquals("<b>加粗</b><br>", UpdateNotes.toHtml("**加粗**"))
        assertEquals("<i>斜体</i><br>", UpdateNotes.toHtml("*斜体*"))
        assertEquals("<b>代码</b><br>", UpdateNotes.toHtml("`代码`"))
        assertEquals(
            "<a href=\"https://example.com\">说明</a><br>",
            UpdateNotes.toHtml("[说明](https://example.com)"),
        )
    }

    @Test
    fun `文本里的尖括号与和号被转义（解析器会解码回来）`() {
        assertEquals("a &amp; b &lt;c&gt;<br>", UpdateNotes.toHtml("a & b <c>"))
    }

    @Test
    fun `空日志返回空串`() {
        assertEquals("", UpdateNotes.toHtml(""))
        assertEquals("", UpdateNotes.toHtml("   "))
    }
}
