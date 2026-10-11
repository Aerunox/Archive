package com.archive.app

import com.archive.app.data.parsePostTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻译回复解析的回归测试。
 *
 * 核心场景来自线上实测（2026-10-08）：模型在 JSON 字符串里直接换行 → JSON 非法 →
 * 旧实现把整段原始 JSON 当正文贴进了帖子。契约改成哨兵分段后，这批用例钉住各条解析路径。
 */
class TranslationParsingTest {

    private val title = "Alexis Rhodes / Asuka Tenjoin | Yu-Gi-Oh [请求]"
    private val html = "套装总共包含100多张图片，尽情享受！存档密码是 [这里] (点击/轻触 '这里')"

    @Test
    fun `parses sentinel format with multiline html`() {
        val raw = "<<<TITLE\n$title\nTITLE>>>\n<<<HTML\n<p>第一段</p>\n\n<p>第二段</p>\nHTML>>>"

        val result = parsePostTranslation(raw, fallbackTitle = "原标题")

        assertEquals(title, result.title)
        assertEquals("<p>第一段</p>\n\n<p>第二段</p>", result.html)
    }

    /** 正文里出现引号、大括号、方括号都不能破坏解析 —— 这正是弃用 JSON 的原因。 */
    @Test
    fun `sentinel survives braces quotes and links in html`() {
        val raw = "<<<TITLE\n$title\nTITLE>>>\n<<<HTML\n" +
            "<p>Password is <a href=\"https://x.com/{a}\">[这里]</a> (Click/Tap on 'here')</p>\n" +
            "{\"not\":\"json\"}\nHTML>>>"

        val result = parsePostTranslation(raw, fallbackTitle = "原标题")

        assertTrue(result.html.contains("<a href=\"https://x.com/{a}\">[这里]</a>"))
        assertTrue(result.html.contains("{\"not\":\"json\"}"))
    }

    /** 线上实测的失败形状：JSON 字符串里带原始换行 → 非法 JSON，靠正则修补救回。 */
    @Test
    fun `repairs json with raw newlines inside string`() {
        val raw = "{\"title\":\"$title\",\"html\":\"\n\n$html\"}"

        val result = parsePostTranslation(raw, fallbackTitle = "原标题")

        assertEquals(title, result.title)
        assertEquals(html, result.html)
    }

    @Test
    fun `parses well formed json`() {
        val raw = "{\"title\":\"标题\",\"html\":\"<p>正文</p>\"}"

        val result = parsePostTranslation(raw, fallbackTitle = "原标题")

        assertEquals("标题", result.title)
        assertEquals("<p>正文</p>", result.html)
    }

    /**
     * 线上实测（2026-10-08）：小模型把提示词里的格式示例当成译文照抄回来，
     * 整篇帖子被替换成「译文标题 / 译文正文 HTML」。解析层必须拒绝这种情况。
     */
    @Test
    fun `placeholder echo is rejected instead of replacing the post`() {
        val raw = "<<<TITLE\n<译文标题>\nTITLE>>>\n<<<HTML\n<译文正文 HTML>\nHTML>>>"

        val error = runCatching { parsePostTranslation(raw, fallbackTitle = "原标题") }.exceptionOrNull()

        assertTrue("应该抛出明确错误，而不是把占位文字当成译文", error is IllegalStateException)
    }

    @Test
    fun `falls back to whole text as body`() {
        val result = parsePostTranslation("就是一段没有标记的译文", fallbackTitle = "原标题")

        assertEquals("原标题", result.title)
        assertEquals("就是一段没有标记的译文", result.html)
    }
}