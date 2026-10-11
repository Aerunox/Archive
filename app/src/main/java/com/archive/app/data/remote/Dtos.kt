package com.archive.app.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Accepts both JSON numbers and numeric strings. Needed because the API
 * returns `indexed`/`updated` as Unix seconds (number) on /creators but as
 * ISO strings on /profile.
 */
object FlexLongSerializer : KSerializer<Long?> {
    override val descriptor = PrimitiveSerialDescriptor("FlexLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.content.toLongOrNull()
    }

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

@Serializable
data class CreatorDto(
    val id: String,
    val name: String,
    @SerialName("public_id") val publicId: String? = null,
    val service: String,
    @Serializable(with = FlexLongSerializer::class) val indexed: Long? = null,
    @Serializable(with = FlexLongSerializer::class) val updated: Long? = null,
    val favorited: Int = 0,
    @SerialName("ever_imported") val everImported: Boolean = false,
)

@Serializable
data class ProfileDto(
    val id: String? = null,
    val name: String? = null,
    val service: String? = null,
    @SerialName("public_id") val publicId: String? = null,
    @SerialName("relation_id") val relationId: String? = null,
    val indexed: String? = null,
    val updated: String? = null,
    @SerialName("ever_imported") val everImported: Boolean? = null,
)

@Serializable
data class FileDto(
    val name: String? = null,
    val path: String? = null,
    @SerialName("preview_only") val previewOnly: Boolean? = null,
    /**
     * true = 站点尚未导入该原文件（视频基本都是这种）。此时 `path` 缺失，
     * 详情接口改为下发带签名的临时流地址（[tempUrl] / [tempDownloadUrl]）。
     */
    val deferred: Boolean? = null,
    @SerialName("temp_url") val tempUrl: String? = null,
    @SerialName("temp_download_url") val tempDownloadUrl: String? = null,
    @SerialName("temp_expires") val tempExpires: String? = null,
)

@Serializable
data class EmbedDto(
    val url: String? = null,
    val type: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val publisher: String? = null,
    val html: String? = null,
    val description: String? = null,
    @SerialName("author_name") val authorName: String? = null,
)

/**
 * Covers both the summary shape returned by list endpoints (carries
 * `substring`, no tags) and the full detail shape (carries tags/prev/next/
 * poll). All fields are nullable because the two shapes differ.
 */
@Serializable
data class PostDto(
    val id: String? = null,
    val user: String? = null,
    val service: String? = null,
    val title: String? = null,
    val content: String? = null,
    val substring: String? = null,
    val embed: EmbedDto? = null,
    @SerialName("shared_file") val sharedFile: Boolean? = null,
    val added: String? = null,
    val published: String? = null,
    val edited: String? = null,
    val file: FileDto? = null,
    val attachments: List<FileDto> = emptyList(),
    // 实测两种形态：JSON 数组 或 Postgres 数组字面量字符串（见 FlexStringListSerializer）
    @Serializable(with = FlexStringListSerializer::class)
    val tags: List<String> = emptyList(),
    val prev: String? = null,
    val next: String? = null,
    val poll: JsonElement? = null,
    val captions: JsonElement? = null,
    @SerialName("preview_state") val previewState: JsonElement? = null,
    val origin: JsonElement? = null,
    @SerialName("has_full") val hasFull: Boolean? = null,
    @SerialName("detail_fetched") val detailFetched: Boolean? = null,
)

@Serializable
data class AnnouncementDto(
    val service: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val hash: String? = null,
    val content: String? = null,
    val added: String? = null,
)

@Serializable
data class CreatorTagDto(
    val tag: String,
    @SerialName("post_count") val postCount: Int = 0,
)

@Serializable
data class CommentRevisionDto(
    val id: Int? = null,
    val content: String? = null,
    val added: String? = null,
)

@Serializable
data class CommentDto(
    val id: String? = null,
    @SerialName("parent_id") val parentId: String? = null,

    /** 评论者的数字 ID（站点把 id 放在 commenter 里）。 */
    val commenter: String? = null,

    /** 评论者昵称；老评论可能没有这个字段，界面回退到 [commenter]。 */
    @SerialName("commenter_name") val commenterName: String? = null,
    val content: String? = null,
    val published: String? = null,
    val revisions: List<CommentRevisionDto> = emptyList(),
)

@Serializable
data class FavoriteDto(
    @SerialName("faved_seq") val favedSeq: Int? = null,
    val id: String? = null,
    val indexed: String? = null,
    @SerialName("last_imported") val lastImported: String? = null,
    val name: String? = null,
    val service: String? = null,
    val updated: String? = null,
)
