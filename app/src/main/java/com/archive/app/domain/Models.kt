package com.archive.app.domain

enum class MediaKind { IMAGE, VIDEO, AUDIO, FILE }

data class Attachment(
    val name: String?,
    /** 站点文件路径；deferred（站点未导入、走临时流）的附件为 null。 */
    val path: String? = null,
    /** true = 站点只收录了预览（原文件未导入 / 需要登录会话），直接取原文件必然 404。 */
    val previewOnly: Boolean = false,
    /** true = 站点尚未导入原文件（视频基本都是这种），播放/下载走下面的临时地址。 */
    val deferred: Boolean = false,
    /** 临时播放地址，deferred 视频是 HLS（master.m3u8），随详情接口下发。 */
    val tempUrl: String? = null,
    /** 临时直链下载地址（deferred 视频也能整段下载）。 */
    val tempDownloadUrl: String? = null,
    /** 临时地址过期时间（ISO8601，站点给约 30 天）。 */
    val tempExpires: String? = null,
) {
    /** 展示用文件名：优先 name，其次路径末段。 */
    fun displayName(fallback: String = "file"): String =
        name?.takeIf { it.isNotBlank() }
            ?: path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: fallback

    /** 是否有可用地址（临时链接过期或两者皆无时为 false）。 */
    fun hasPlayableSource(now: java.time.Instant = java.time.Instant.now()): Boolean =
        !tempLinkExpired(now) && (!tempUrl.isNullOrBlank() || !path.isNullOrBlank())

    /**
     * 临时链接是否已过期。站点给的 `temp_expires` 到期后地址即失效，
     * 需要重新拉一次详情换取新地址。
     */
    fun tempLinkExpired(now: java.time.Instant = java.time.Instant.now()): Boolean {
        val raw = tempExpires?.takeIf { it.isNotBlank() } ?: return false
        val instant = runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { java.time.Instant.parse(raw) }
            .getOrNull() ?: return false
        return instant.isBefore(now)
    }
}

data class Creator(
    val service: String,
    val id: String,
    val name: String,
    val publicId: String?,
    val indexed: Long?,
    val updated: Long?,
    val favorited: Int,
    val everImported: Boolean,
)

data class CreatorProfile(
    val service: String,
    val id: String,
    val name: String,
    val publicId: String?,
    val indexed: String?,
    val updated: String?,
    val everImported: Boolean?,
)

data class PostSummary(
    val service: String,
    val creatorId: String,
    val postId: String,
    val title: String,
    val excerpt: String?,
    val published: String?,
    val cover: Attachment?,
    val previewOnly: Boolean,
)

data class PostDetail(
    val service: String,
    val creatorId: String,
    val postId: String,
    val title: String,
    val contentHtml: String,
    val added: String?,
    val published: String?,
    val edited: String?,
    val file: Attachment?,
    val attachments: List<Attachment>,
    val tags: List<String>,
    val prev: String?,
    val next: String?,
    val previewOnly: Boolean,
)

/** 文件哈希反查结果：站点帖子（可进详情）+ Discord 帖子（仅文本摘要）。 */
data class HashSearchResult(
    val sitePosts: List<PostSummary>,
    val discordHits: List<DiscordHit>,
)

data class DiscordHit(
    val id: String,
    val server: String,
    val channel: String,
    val excerpt: String?,
    val published: String?,
)

data class Announcement(
    val service: String,
    val userId: String,
    val content: String,
    val added: String?,
)

data class CreatorTag(
    val tag: String,
    val postCount: Int,
)

data class PostComment(
    val id: String,
    val parentId: String?,
    val commenter: String,
    val content: String,
    val published: String?,
)

data class SessionFavorite(
    val id: String,
    val service: String,
    val name: String?,
    val updated: String?,
)

fun Attachment.kind(): MediaKind {
    val n = (name ?: path.orEmpty()).lowercase()
    val ext = n.substringAfterLast('.', "")
    return when (ext) {
        in IMAGE_EXTS -> MediaKind.IMAGE
        in VIDEO_EXTS -> MediaKind.VIDEO
        in AUDIO_EXTS -> MediaKind.AUDIO
        else -> MediaKind.FILE
    }
}

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
private val VIDEO_EXTS = setOf("mp4", "webm", "mov", "mkv", "m4v")
private val AUDIO_EXTS = setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus")
