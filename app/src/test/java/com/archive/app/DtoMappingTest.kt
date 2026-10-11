package com.archive.app

import com.archive.app.data.toDetail
import com.archive.app.data.toDomain
import com.archive.app.data.toSummary
import com.archive.app.data.remote.CommentDto
import com.archive.app.data.remote.CreatorDto
import com.archive.app.data.remote.PostDto
import com.archive.app.data.remote.UrlProvider
import com.archive.app.domain.MediaKind
import com.archive.app.domain.kind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping tests use JSON shapes captured from the live API (2026-10-06):
 * list items are summaries (with `substring`, `preview_only`), detail items
 * carry tags/prev/next, and /creators returns Unix seconds while /profile
 * returns ISO strings.
 */
class DtoMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `post summary decodes and maps cover and preview flag`() {
        val payload = """
            {"id":"171486256","user":"30500811","service":"patreon",
             "title":"Reisalin Stout","content":"<p>hi</p>",
             "embed":{},"shared_file":false,
             "added":"2026-10-06T07:00:00","published":"2026-10-05T12:13:08","edited":null,
             "file":{"name":"cover.jpg","path":"/a9/d7/a9d71a.jpg","preview_only":true},
             "attachments":[{"name":"a2.jpg","path":"/7d/92/7d92c2.jpg","preview_only":true}],
             "has_full":false,"origin":"import","preview_state":null,
             "substring":"short text"}
        """.trimIndent()
        val dto = json.decodeFromString(PostDto.serializer(), payload)
        val summary = dto.toSummary()!!
        assertEquals("171486256", summary.postId)
        assertEquals("patreon", summary.service)
        assertEquals("/a9/d7/a9d71a.jpg", summary.cover!!.path)
        assertTrue(summary.previewOnly)
        assertEquals("short text", summary.excerpt)
    }

    @Test
    fun `post detail decodes tags prev next`() {
        val payload = """
            {"id":"171486256","user":"30500811","service":"patreon","title":"T",
             "content":"<p>body</p>","detail_fetched":true,
             "tags":["NSFW","Benefits"],"prev":"171400000","next":"171405069",
             "poll":null,"captions":null,
             "file":{"name":"f.jpg","path":"/aa/aa.jpg","preview_only":true},
             "attachments":[{"name":"1.jpg","path":"/bb/1.jpg"}]}
        """.trimIndent()
        val dto = json.decodeFromString(PostDto.serializer(), payload)
        val detail = dto.toDetail("patreon", "30500811", "171486256")
        assertEquals(listOf("NSFW", "Benefits"), detail.tags)
        assertEquals("171400000", detail.prev)
        assertEquals("171405069", detail.next)
        assertEquals(1, detail.attachments.size)
    }

    /**
     * 站点未导入原件的视频（`deferred`）没有 path，只有临时流地址；
     * 早期实现用 `filter { path 非空 }` 把它们整个丢掉，视频就永远不显示。
     */
    @Test
    fun `deferred video attachment keeps temp urls and survives mapping`() {
        val payload = """
            {"id":"171102772","user":"81104462","service":"patreon","title":"V",
             "content":"body","attachments":[
               {"name":"755699804.mp4","deferred":true,
                "temp_url":"https://t1.pawchive.pw/v/ff202a8c/master.m3u8?e=1791392960&s=a5bd",
                "temp_download_url":"https://t1.pawchive.pw/d/ff202a8c/755699804.mp4?e=1791392960&s=bdea",
                "temp_expires":"2026-11-07T00:00:00+00:00"},
               {"name":"song.mp3","node":2,"path":"/58/30/song.mp3"}]}
        """.trimIndent()
        val detail = json.decodeFromString(PostDto.serializer(), payload)
            .toDetail("patreon", "81104462", "171102772")

        assertEquals(2, detail.attachments.size)
        val video = detail.attachments[0]
        assertNull(video.path)
        assertTrue(video.deferred)
        assertEquals("755699804.mp4", video.displayName())
        assertEquals(
            "https://t1.pawchive.pw/v/ff202a8c/master.m3u8?e=1791392960&s=a5bd",
            UrlProvider.playbackUrl("https://pawchive.pw", video.path, video.tempUrl),
        )
        assertEquals(
            "https://t1.pawchive.pw/d/ff202a8c/755699804.mp4?e=1791392960&s=bdea",
            UrlProvider.downloadUrl("https://pawchive.pw", video.path, video.tempDownloadUrl),
        )
        assertEquals(MediaKind.VIDEO, video.kind())
        assertEquals(MediaKind.AUDIO, detail.attachments[1].kind())
    }

    @Test
    fun `creator decodes numeric unix timestamps`() {
        val payload = """
            {"id":"30500811","name":"BOKABA","public_id":"BOKABA","service":"patreon",
             "indexed":1785423600,"updated":1791270000,"favorited":2336,"ever_imported":true}
        """.trimIndent()
        val dto = json.decodeFromString(CreatorDto.serializer(), payload)
        assertEquals(1785423600L, dto.indexed)
        assertEquals(2336, dto.favorited)
        assertTrue(dto.everImported)
    }

    @Test
    fun `summary without id is dropped`() {
        val dto = json.decodeFromString(PostDto.serializer(), """{"title":"no id"}""")
        assertNull(dto.toSummary())
    }

    @Test
    fun `unknown fields are ignored`() {
        val dto = json.decodeFromString(
            PostDto.serializer(),
            """{"id":"1","someFutureField":{"nested":true},"user":"u","service":"patreon"}""",
        )
        assertEquals("1", dto.id)
        assertTrue(dto.toSummary() != null)
    }

    /**
     * 站点把评论者拆成两段：`commenter` 是数字 ID，`commenter_name` 才是昵称。
     * 早期实现只映射了前者，帖子详情页的评论区显示的就是一串数字（实测 2026-10-08）。
     */
    @Test
    fun `comment prefers nickname over numeric id`() {
        val payload = """
            {"id":"227295152","parent_id":null,"commenter":"105541049",
             "commenter_name":"Captain Keyes","content":"<p>hi</p>",
             "published":"2026-10-08T00:04:51.041000","revisions":[]}
        """.trimIndent()
        val comment = json.decodeFromString(CommentDto.serializer(), payload).toDomain()!!

        assertEquals("227295152", comment.id)
        assertEquals("Captain Keyes", comment.commenter)
    }

    /** 老评论没有 commenter_name，退回数字 ID（总比显示空白好）。 */
    @Test
    fun `comment falls back to id when nickname missing`() {
        val payload = """{"id":"1","commenter":"84534108","content":"c"}"""
        val comment = json.decodeFromString(CommentDto.serializer(), payload).toDomain()!!

        assertEquals("84534108", comment.commenter)
    }
}
