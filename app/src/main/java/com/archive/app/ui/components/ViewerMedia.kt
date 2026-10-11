package com.archive.app.ui.components

import com.archive.app.data.remote.UrlProvider
import com.archive.app.domain.Attachment
import com.archive.app.domain.MediaKind
import com.archive.app.domain.PostDetail
import com.archive.app.domain.kind

/**
 * 查看器里的一页媒体。图片与视频混在同一个列表里，左右滑动就是「上一个/下一个媒体文件」。
 */
sealed interface ViewerMedia {
    val attachment: Attachment

    data class Image(override val attachment: Attachment) : ViewerMedia

    /** [url] 是可直接播放的地址（站点临时流优先，否则 file 域原文件）。 */
    data class Video(override val attachment: Attachment, val url: String) : ViewerMedia
}

/**
 * 统一媒体列表：附件里的图片（必须有 path）与**可播放**的视频，
 * 顺序沿用帖子的附件顺序（[PostDetail.file] 在最前）。
 *
 * 放不了的（站点未导入且无临时链接 / 临时链接已过期 / 非图片视频的音频附件）不进这个列表，
 * 它们继续在帖子正文里以附件行展示。
 *
 * 帖子正文与查看器都调用这个函数算下标，因此 `viewer/{service}/{creatorId}/{postId}?index=`
 * 指向的一定是同一个媒体文件。
 */
fun viewerMedia(baseUrl: String, post: PostDetail): List<ViewerMedia> =
    (listOfNotNull(post.file) + post.attachments).mapNotNull { attachment ->
        when {
            attachment.kind() == MediaKind.IMAGE && !attachment.path.isNullOrBlank() ->
                ViewerMedia.Image(attachment)

            attachment.kind() == MediaKind.VIDEO -> {
                val url = UrlProvider.playbackUrl(baseUrl, attachment.path, attachment.tempUrl)
                if (url != null && !attachment.tempLinkExpired()) {
                    ViewerMedia.Video(attachment, url)
                } else {
                    null
                }
            }

            else -> null
        }
    }

/** 某个附件在这个列表里的下标（找不到返回 -1）。 */
fun List<ViewerMedia>.indexOfAttachment(target: Attachment): Int =
    indexOfFirst { it.attachment.sameFileAs(target) }

/** deferred 附件没有 path（站点未导入），退化成用文件名比较。 */
private fun Attachment.sameFileAs(other: Attachment): Boolean =
    if (path != null) path == other.path else name != null && name == other.name
