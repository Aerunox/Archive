package com.archive.app

import com.archive.app.data.remote.UrlProvider
import com.archive.app.domain.Attachment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlProviderTest {

    @Test
    fun `api v1 is derived from base url`() {
        assertEquals("https://pawchive.pw/api/v1", UrlProvider.apiV1("https://pawchive.pw"))
        assertEquals("https://pawchive.pw/api/v1", UrlProvider.apiV1("https://pawchive.pw/"))
    }

    @Test
    fun `thumbnail uses img subdomain with thumbnail-data prefix`() {
        assertEquals(
            "https://img.pawchive.pw/thumbnail/data/a9/d7/abc.jpg",
            UrlProvider.thumbnailUrl("https://pawchive.pw", "/a9/d7/abc.jpg"),
        )
    }

    /**
     * 只有 file 子域提供原文件：主域与 img 域的 `/data{path}` 实测都是 404
     * （对照 Pawchive 里写死的 `file.pawchive.pw/data{path}`）。
     */
    @Test
    fun `full file uses file subdomain data prefix`() {
        assertEquals(
            "https://file.pawchive.pw/data/7d/92/xyz.jpg",
            UrlProvider.fileUrl("https://pawchive.pw", "/7d/92/xyz.jpg"),
        )
    }

    @Test
    fun `icon and banner follow site rules`() {
        assertEquals(
            "https://pawchive.pw/icons/patreon/30500811",
            UrlProvider.iconUrl("https://pawchive.pw", "patreon", "30500811"),
        )
        assertEquals(
            "https://pawchive.pw/banners/fanbox/123",
            UrlProvider.bannerUrl("https://pawchive.pw", "fanbox", "123"),
        )
    }

    @Test
    fun `img base derives img host and keeps existing img hosts`() {
        assertEquals("https://img.pawchive.pw", UrlProvider.imgBase("https://pawchive.pw"))
        assertEquals("https://img.pawchive.pw", UrlProvider.imgBase("https://img.pawchive.pw"))
    }

    @Test
    fun `file base derives file host and keeps existing file hosts`() {
        assertEquals("https://file.pawchive.pw", UrlProvider.fileBase("https://pawchive.pw"))
        assertEquals("https://file.pawchive.pw", UrlProvider.fileBase("https://file.pawchive.pw/"))
    }

    @Test
    fun `content image resolves relative and absolute sources`() {
        assertEquals(
            "https://example.com/a/b.jpg",
            UrlProvider.contentImageUrl("https://example.com", "/a/b.jpg"),
        )
        assertEquals(
            "https://cdn.other.com/x.png",
            UrlProvider.contentImageUrl("https://example.com", "https://cdn.other.com/x.png"),
        )
    }

    @Test
    fun `alternate base url changes every rule`() {
        val base = "https://mirror.example.org"
        assertEquals("https://img.mirror.example.org/thumbnail/data/p", UrlProvider.thumbnailUrl(base, "/p"))
        assertEquals("https://file.mirror.example.org/data/p", UrlProvider.fileUrl(base, "/p"))
    }

    // ── deferred（站点未导入原件）附件：临时流地址优先 ──

    @Test
    fun `playback prefers temp url over file host`() {
        assertEquals(
            "https://t1.pawchive.pw/v/abc/master.m3u8?e=1&s=2",
            UrlProvider.playbackUrl(
                "https://pawchive.pw",
                "/ab/cd/x.mp4",
                "https://t1.pawchive.pw/v/abc/master.m3u8?e=1&s=2",
            ),
        )
    }

    @Test
    fun `playback falls back to file host and is null without any source`() {
        assertEquals(
            "https://file.pawchive.pw/data/ab/cd/x.mp4",
            UrlProvider.playbackUrl("https://pawchive.pw", "/ab/cd/x.mp4", null),
        )
        assertNull(UrlProvider.playbackUrl("https://pawchive.pw", null, null))
        assertNull(UrlProvider.playbackUrl("https://pawchive.pw", "", "  "))
    }

    @Test
    fun `download prefers temp download url over file host`() {
        assertEquals(
            "https://t1.pawchive.pw/d/abc/x.mp4?e=1&s=2",
            UrlProvider.downloadUrl("https://pawchive.pw", null, "https://t1.pawchive.pw/d/abc/x.mp4?e=1&s=2"),
        )
        assertEquals(
            "https://file.pawchive.pw/data/ab/cd/x.mp3",
            UrlProvider.downloadUrl("https://pawchive.pw", "/ab/cd/x.mp3", null),
        )
        assertNull(UrlProvider.downloadUrl("https://pawchive.pw", null, null))
    }

    @Test
    fun `temporary url detection`() {
        assertTrue(UrlProvider.isTemporaryUrl("https://t1.pawchive.pw/d/x.mp4?e=1&s=2"))
        assertFalse(UrlProvider.isTemporaryUrl("https://file.pawchive.pw/data/x.mp4"))
    }

    // ── 附件模型：deferred / 过期 / 展示名 ──

    @Test
    fun `deferred attachment without path still exposes a display name`() {
        val att = Attachment(name = "755699804.mp4", path = null, deferred = true)
        assertEquals("755699804.mp4", att.displayName())
        // 只有 deferred 标记、既没有 path 也没有临时地址时，确实无处可取
        assertFalse(att.hasPlayableSource())
    }

    @Test
    fun `deferred attachment with temp url is playable`() {
        val att = Attachment(
            name = "755699804.mp4",
            path = null,
            deferred = true,
            tempUrl = "https://t1.pawchive.pw/v/x/master.m3u8?e=1791392960&s=abc",
        )
        assertTrue(att.hasPlayableSource())
        assertEquals("755699804.mp4", att.displayName())
    }

    @Test
    fun `expired temp link makes the attachment unplayable`() {
        val att = Attachment(
            name = "v.mp4",
            path = null,
            deferred = true,
            tempUrl = "https://t1.pawchive.pw/v/x/master.m3u8?e=1",
            tempExpires = "2026-11-07T00:00:00+00:00",
        )
        val before = Instant.parse("2026-10-07T00:00:00Z")
        val after = Instant.parse("2026-11-08T00:00:00Z")
        assertFalse(att.tempLinkExpired(before))
        assertTrue(att.hasPlayableSource(before))
        assertTrue(att.tempLinkExpired(after))
        assertFalse(att.hasPlayableSource(after))
    }

    @Test
    fun `attachment without temp fields never reports expiry`() {
        val att = Attachment(name = "x.mp3", path = "/58/30/x.mp3")
        assertFalse(att.tempLinkExpired())
        assertTrue(att.hasPlayableSource())
    }

    @Test
    fun `pathless attachment without temp url has no source`() {
        val att = Attachment(name = "unknown.bin", path = null)
        assertFalse(att.hasPlayableSource())
    }
}
