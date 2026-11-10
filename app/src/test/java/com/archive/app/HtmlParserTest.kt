package com.archive.app

import com.archive.app.util.HtmlNode
import com.archive.app.util.HtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {

    @Test
    fun `paragraphs and breaks split`() {
        val nodes = HtmlParser.parse("<p>one</p><p>two</p>three<br>four")
        assertEquals(4, nodes.size)
        val texts = nodes.map { (it as HtmlNode.Paragraph).spans.single().text }
        assertEquals(listOf("one", "two", "three", "four"), texts)
    }

    @Test
    fun `bold italic underline and links are tracked`() {
        val nodes = HtmlParser.parse("<p><b>bold</b> <i>it</i> <u>u</u> <a href=\"https://x.example\">link</a></p>")
        val spans = (nodes.single() as HtmlNode.Paragraph).spans
        assertTrue(spans.any { it.bold && it.text == "bold" })
        assertTrue(spans.any { it.italic && it.text == "it" })
        assertTrue(spans.any { it.underline && it.text == "u" })
        val link = spans.first { it.link != null }
        assertEquals("https://x.example", link.link)
        assertEquals("link", link.text)
    }

    @Test
    fun `images become image nodes with src`() {
        val nodes = HtmlParser.parse("<p>a<img src=\"/data/x.jpg\"/>b</p>")
        val images = nodes.filterIsInstance<HtmlNode.Image>()
        assertEquals(1, images.size)
        assertEquals("/data/x.jpg", images.single().src)
    }

    @Test
    fun `script and style are stripped and entities decoded`() {
        val nodes = HtmlParser.parse("<p>&amp;&lt;&gt;</p><script>alert(1)</script><style>.x{}</style>")
        val text = (nodes.single() as HtmlNode.Paragraph).spans.single().text
        assertEquals("&<>", text)
    }

    @Test
    fun `html to plain text strips tags for list excerpts`() {
        assertEquals(
            "@Dart, Sorry, I could only find one lora for him",
            HtmlParser.htmlToPlainText("<p>@Dart, Sorry, I could only find one lora for him</p>"),
        )
        assertEquals(
            "Vacation's over and we're starting the new arc",
            HtmlParser.htmlToPlainText("<p>Vacation&#x27;s over and we&#39;re starting the new arc</p>"),
        )
        assertEquals(
            "Wizard Segs Animation : link",
            HtmlParser.htmlToPlainText(
                "<p>Wizard Segs</p><p>Animation : <a href=\"https://x\">link</a></p>",
            ),
        )
    }

    @Test
    fun `empty markup becomes empty text so the card hides the excerpt`() {
        assertTrue(HtmlParser.htmlToPlainText("<p></p>").isEmpty())
        assertTrue(HtmlParser.htmlToPlainText("<p>   </p><br/>").isEmpty())
        assertTrue(HtmlParser.htmlToPlainText(null).isEmpty())
    }

    @Test
    fun `script and style never leak into excerpts`() {
        val text = HtmlParser.htmlToPlainText("<p>hi</p><script>alert(1)</script><style>.x{}</style>")
        assertEquals("hi", text)
    }

    @Test
    fun `numeric entities decode in both decimal and hex form`() {
        assertEquals("A'b", HtmlParser.htmlToPlainText("A&#39;b"))
        assertEquals("A'b", HtmlParser.htmlToPlainText("A&#x27;b"))
    }

    @Test
    fun `escaped literals are decoded exactly once`() {
        // 源文本 &amp;lt; 显示为字面量 "&lt;"，不能再往下解成 "<"
        assertEquals("&lt;", HtmlParser.htmlToPlainText("&amp;lt;"))
        // &amp;#39; 显示为字面量 "&#39;"，不能变成 "'"
        assertEquals("&#39;", HtmlParser.htmlToPlainText("&amp;#39;"))
    }
}
