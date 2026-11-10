package com.archive.app.data

import com.archive.app.data.local.CreatorEntity
import com.archive.app.data.local.FavoritePostEntity
import com.archive.app.data.local.HistoryEntity
import com.archive.app.data.remote.AnnouncementDto
import com.archive.app.data.remote.CommentDto
import com.archive.app.data.remote.CreatorDto
import com.archive.app.data.remote.CreatorTagDto
import com.archive.app.data.remote.FavoriteDto
import com.archive.app.data.remote.FileDto
import com.archive.app.data.remote.PostDto
import com.archive.app.data.remote.ProfileDto
import com.archive.app.domain.Announcement
import com.archive.app.domain.Attachment
import com.archive.app.domain.Creator
import com.archive.app.domain.CreatorProfile
import com.archive.app.domain.CreatorTag
import com.archive.app.domain.PostComment
import com.archive.app.domain.PostDetail
import com.archive.app.domain.PostSummary
import com.archive.app.domain.SessionFavorite

fun CreatorDto.toDomain(): Creator = Creator(
    service = service,
    id = id,
    name = name,
    publicId = publicId,
    indexed = indexed,
    updated = updated,
    favorited = favorited,
    everImported = everImported,
)

fun CreatorDto.toEntity(): CreatorEntity = CreatorEntity(
    service = service,
    id = id,
    name = name,
    publicId = publicId,
    indexed = indexed,
    updated = updated,
    favorited = favorited,
    everImported = everImported,
)

fun CreatorEntity.toDomain(): Creator = Creator(
    service = service,
    id = id,
    name = name,
    publicId = publicId,
    indexed = indexed,
    updated = updated,
    favorited = favorited,
    everImported = everImported,
)

fun ProfileDto.toDomain(service: String, id: String): CreatorProfile = CreatorProfile(
    service = service,
    id = id,
    name = name ?: id,
    publicId = publicId,
    indexed = indexed,
    updated = updated,
    everImported = everImported,
)

/**
 * 关联账号条目（`/{service}/user/{id}/links` 返回的是 CreatorProfile 列表）。
 *
 * 注意：**不能**复用 [ProfileDto.toDomain]——那个函数用路径参数当作身份（适用于
 * profile 端点），用在 links 列表上会让每条记录的 service/id 都变成当前作者的，
 * 导致列表 key 重复（Lazy 列表直接崩溃）且点击跳转到当前页。这里取条目自身的身份。
 */
fun ProfileDto.toLinkDomain(): CreatorProfile? {
    val linkId = id?.takeIf { it.isNotBlank() } ?: return null
    val linkService = service?.takeIf { it.isNotBlank() } ?: return null
    return CreatorProfile(
        service = linkService,
        id = linkId,
        name = name?.takeIf { it.isNotBlank() } ?: linkId,
        publicId = publicId,
        indexed = indexed,
        updated = updated,
        everImported = everImported,
    )
}

private fun PostDto.firstAttachment(): Attachment? {
    file?.path?.let { return Attachment(file?.name, it) }
    val first = attachments.firstOrNull { !it.path.isNullOrBlank() } ?: return null
    return Attachment(first.name, first.path!!)
}

fun PostDto.toSummary(): PostSummary? {
    val pid = id ?: return null
    return PostSummary(
        service = service ?: "",
        creatorId = user ?: "",
        postId = pid,
        title = title?.ifBlank { null } ?: "(无标题)",
        excerpt = listOfNotNull(substring, content)
            .map { com.archive.app.util.HtmlParser.htmlToPlainText(it) }
            .firstOrNull { it.isNotBlank() }
            ?.take(160),
        published = published,
        cover = firstAttachment(),
        previewOnly = file?.previewOnly ?: false,
    )
}

fun PostDto.toDetail(fallbackService: String, fallbackCreator: String, fallbackId: String): PostDetail =
    PostDetail(
        service = service ?: fallbackService,
        creatorId = user ?: fallbackCreator,
        postId = id ?: fallbackId,
        title = title?.ifBlank { null } ?: "(无标题)",
        contentHtml = content ?: "",
        added = added,
        published = published,
        edited = edited,
        file = file?.toAttachment(),
        attachments = attachments.map { it.toAttachment() },
        tags = tags,
        prev = prev,
        next = next,
        previewOnly = file?.previewOnly ?: false,
    )

/**
 * 保留没有 path 的附件：站点未导入原文件时（`deferred: true`，视频基本都是）
 * 详情接口只下发临时流地址，丢掉它们就等于丢掉整个视频。
 */
private fun FileDto.toAttachment(): Attachment = Attachment(
    name = name,
    path = path?.takeIf { it.isNotBlank() },
    previewOnly = previewOnly ?: false,
    deferred = deferred ?: false,
    tempUrl = tempUrl?.takeIf { it.isNotBlank() },
    tempDownloadUrl = tempDownloadUrl?.takeIf { it.isNotBlank() },
    tempExpires = tempExpires?.takeIf { it.isNotBlank() },
)

/**
 * 离线归档（BookmarkManager 收藏全文缓存）里的 core `Post` → 列表摘要。
 * 「离线归档」搜索 Tab 的结果卡片用；与 PostDto 的 [PostDto.toSummary] 同形但数据源不同
 * （core 模型没有 substring，摘要直接从正文 HTML 提取）。
 */
fun com.archive.core.model.Post.toSummary(): com.archive.app.domain.PostSummary {
    val cover = file?.let { f ->
        f.path?.takeIf { it.isNotBlank() }?.let { path ->
            com.archive.app.domain.Attachment(f.name, path)
        }
    } ?: attachments.orEmpty()
        .firstOrNull { !it.path.isNullOrBlank() }
        ?.let { com.archive.app.domain.Attachment(it.name, it.path!!) }
    return com.archive.app.domain.PostSummary(
        service = service,
        creatorId = user,
        postId = id,
        title = title?.ifBlank { null } ?: "(无标题)",
        excerpt = content
            ?.let { com.archive.app.util.HtmlParser.htmlToPlainText(it) }
            ?.takeIf { it.isNotBlank() }
            ?.take(160),
        published = published,
        cover = cover,
        previewOnly = file?.previewOnly ?: false,
    )
}

fun com.archive.core.model.FileSearchPost.toSummaryForSearch(): com.archive.app.domain.PostSummary? {
    val pid = id.takeIf { it.isNotBlank() } ?: return null
    return com.archive.app.domain.PostSummary(
        service = service,
        creatorId = user,
        postId = pid,
        title = title?.takeIf { it.isNotBlank() } ?: "(无标题)",
        excerpt = com.archive.app.util.HtmlParser.htmlToPlainText(substring).take(160).takeIf { it.isNotBlank() },
        published = published,
        cover = file?.let { f -> f.path?.let { com.archive.app.domain.Attachment(f.name, it) } },
        previewOnly = file?.previewOnly ?: false,
    )
}

fun AnnouncementDto.toDomain(): Announcement? {
    if (content.isNullOrBlank()) return null
    return Announcement(
        service = service ?: "",
        userId = userId ?: "",
        content = content,
        added = added,
    )
}

fun CreatorTagDto.toDomain(): CreatorTag = CreatorTag(tag = tag, postCount = postCount)

fun CommentDto.toDomain(): PostComment? {
    if (id == null) return null
    return PostComment(
        id = id,
        parentId = parentId,
        // 站点把评论者拆成两段：commenter 是数字 ID，commenter_name 才是昵称。
        // 界面只展示这一处，所以在这里就取昵称；老评论缺这个字段时回退到 ID。
        commenter = commenterName?.takeIf { it.isNotBlank() } ?: commenter ?: "",
        content = content ?: "",
        published = published,
    )
}

fun FavoriteDto.toDomain(): SessionFavorite? {
    if (id == null || service == null) return null
    return SessionFavorite(id = id, service = service, name = name, updated = updated)
}

data class FavoriteRecord(
    val service: String,
    val creatorId: String,
    val creatorName: String,
    val postId: String,
    val title: String,
    val thumbPath: String?,
)

fun FavoriteRecord.toEntity(): FavoritePostEntity = FavoritePostEntity(
    service = service,
    creatorId = creatorId,
    creatorName = creatorName,
    postId = postId,
    title = title,
    thumbPath = thumbPath,
    addedAt = System.currentTimeMillis(),
)

data class HistoryRecord(
    val service: String,
    val creatorId: String,
    val creatorName: String,
    val postId: String,
    val title: String,
    val thumbPath: String?,
)

fun HistoryRecord.toEntity(): HistoryEntity = HistoryEntity(
    service = service,
    creatorId = creatorId,
    creatorName = creatorName,
    postId = postId,
    title = title,
    thumbPath = thumbPath,
    viewedAt = System.currentTimeMillis(),
)
