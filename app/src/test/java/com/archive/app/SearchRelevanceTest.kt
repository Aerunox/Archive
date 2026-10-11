package com.archive.app

import com.archive.app.domain.PostSummary
import com.archive.app.util.PostSortMode
import com.archive.app.util.SearchRelevance
import com.archive.app.util.sortedFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 搜索相关度打分：标题整串命中 > 标题单词 > 创作者 > 正文。 */
class SearchRelevanceTest {

    @Test
    fun `title phrase match scores highest`() {
        val phrase = SearchRelevance.score("stout", "Reisalin Stout (ライザリン)", "BOKABA", null)
        val bodyOnly = SearchRelevance.score("stout", "October Update", "BOKABA", "a stout brew")
        assertTrue("phrase=$phrase should beat bodyOnly=$bodyOnly", phrase > bodyOnly)
    }

    @Test
    fun `title term beats creator and body`() {
        val title = SearchRelevance.score("atelier", "Atelier Ryza art", "someone", null)
        val creator = SearchRelevance.score("atelier", "unrelated", "atelier-fan", null)
        val body = SearchRelevance.score("atelier", "unrelated", "someone", "atelier soon")
        assertTrue(title > creator)
        assertTrue(creator > body)
    }

    @Test
    fun `empty query scores zero`() {
        assertEquals(0, SearchRelevance.score("", "anything", "anyone", "anywhere"))
        assertEquals(0, SearchRelevance.score("   ", "anything", "anyone", "anywhere"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(
            SearchRelevance.score("ryza", "Ryza Art", "creator", null),
            SearchRelevance.score("RYZA", "ryza art", "creator", null),
        )
    }

    @Test
    fun `multi word query accumulates matches`() {
        val both = SearchRelevance.score("ryza art", "Ryza art pack", "creator", null)
        val one = SearchRelevance.score("ryza art", "Ryza pack", "creator", null)
        assertTrue(both > one)
    }

    @Test
    fun `no match scores zero so original order is kept`() {
        assertEquals(0, SearchRelevance.score("zzz", "Ryza Art", "creator", "body"))
    }

    /**
     * 相关度排序**必须带上关键词**。
     *
     * 作者页内搜索曾经忘了把关键词交给排序，于是「相关度」实际表现为发布时间倒序
     * （选了相关度像没反应）。这条用例把「带上关键词才按相关度排」钉住：
     * 谁再把 query 漏掉，第二条断言就会失败。
     */
    @Test
    fun `relevance sort needs the query or it degrades to date order`() {
        val posts = listOf(
            post(id = "newer", title = "October update", published = "2026-10-01T00:00:00Z"),
            post(id = "older", title = "Ryza art pack", published = "2026-01-01T00:00:00Z"),
        )

        // 带关键词：标题命中的那条排到最前（哪怕它更旧）
        assertEquals(
            listOf("older", "newer"),
            posts.sortedFor(PostSortMode.RELEVANCE, query = "ryza").map { it.postId },
        )
        // 不带关键词：退化为发布时间倒序 —— 这正是「忘了传 query」时看到的样子
        assertEquals(
            listOf("newer", "older"),
            posts.sortedFor(PostSortMode.RELEVANCE).map { it.postId },
        )
    }

    private fun post(id: String, title: String, published: String?) = PostSummary(
        service = "patreon",
        creatorId = "creator",
        postId = id,
        title = title,
        excerpt = null,
        published = published,
        cover = null,
        previewOnly = false,
    )
}
