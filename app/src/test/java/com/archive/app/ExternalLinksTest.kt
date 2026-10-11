package com.archive.app

import com.archive.app.util.ExternalLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 帖子正文外部链接抽取（对照 Pawchive 的行为）。 */
class ExternalLinksTest {

    @Test
    fun `extracts https links with anchor text as title`() {
        val html = """<p>Full Animation here: <a href="https://possessionhub.com/zatanna">Zatanna&#x27;s Summoning</a></p>"""
        val links = ExternalLinks.extract(html)
        assertEquals(1, links.size)
        assertEquals("Zatanna's Summoning", links[0].title)
        assertEquals("possessionhub.com", links[0].host)
    }

    @Test
    fun `deduplicates trailing slash variants`() {
        val html = """
            <a href="https://example.com/a">one</a>
            <a href="https://example.com/a/">two</a>
        """.trimIndent()
        assertEquals(1, ExternalLinks.extract(html).size)
    }

    @Test
    fun `skips site file hosts and data paths`() {
        val html = """
            <a href="https://img.pawchive.pw/thumbnail/data/ab/cd.jpg">thumb</a>
            <a href="https://pawchive.pw/data/ab/cd.jpg">file</a>
            <a href="https://example.com/keep">keep</a>
        """.trimIndent()
        val links = ExternalLinks.extract(html)
        assertEquals(listOf("example.com"), links.map { it.host })
    }

    @Test
    fun `skips non https links`() {
        val html = """<a href="http://example.com/x">http</a><a href="/relative/path">rel</a>"""
        assertTrue(ExternalLinks.extract(html).isEmpty())
    }

    @Test
    fun `handles empty or null content`() {
        assertTrue(ExternalLinks.extract(null).isEmpty())
        assertTrue(ExternalLinks.extract("").isEmpty())
        assertTrue(ExternalLinks.extract("<p>no links</p>").isEmpty())
    }

    @Test
    fun `supports single quoted href and uses host when text is empty`() {
        val html = """<a href='https://example.org/page'></a>"""
        val links = ExternalLinks.extract(html)
        assertEquals(1, links.size)
        assertEquals("example.org", links[0].title)
    }
}
