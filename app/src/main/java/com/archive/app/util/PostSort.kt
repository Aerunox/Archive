package com.archive.app.util

import com.archive.core.model.FavoritePost
import com.archive.app.domain.PostSummary
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 首页 / 搜索结果 / 收藏 / 作者-帖子 四个列表页共用的排序方式。
 *
 * 站点接口没有任何排序参数（只有 offset 分页），所以排序全部在客户端完成，作用范围是
 * **当前已加载的内容**：滚动加载出新的一页后，列表会按新数据整体重排一次。
 */
enum class PostSortMode(val label: String, val shortLabel: String) {
    PUBLISHED_DESC("发布时间：新 → 旧", "最新发布"),
    PUBLISHED_ASC("发布时间：旧 → 新", "最早发布"),
    FAVED_DESC("收藏时间：新 → 旧", "最新收藏"),
    FAVED_ASC("收藏时间：旧 → 新", "最早收藏"),
    RELEVANCE("搜索相关度", "相关度"),
    ;

    /** 需要云端收藏序号才能排序（未登录时这两项不可用）。 */
    val needsFavedSeq: Boolean get() = this == FAVED_DESC || this == FAVED_ASC
}

/**
 * 发现页 / 收藏页-作者 这类**创作者列表**的排序方式。
 *
 * 与帖子不同，创作者只有两个可比的量：更新时间，以及站点给的收藏量
 * （发现页本地库里是收藏**数**，收藏页云端返回的是收藏**序号**，两者都是「越大越靠前/越新」）。
 */
enum class CreatorSortMode(val label: String, val shortLabel: String) {
    UPDATED_DESC("更新时间：新 → 旧", "最新更新"),
    UPDATED_ASC("更新时间：旧 → 新", "最早更新"),
    FAVED_DESC("收藏：多 → 少", "收藏多→少"),
    FAVED_ASC("收藏：少 → 多", "收藏少→多"),
    RELEVANCE("搜索相关度", "相关度"),
    ;

    /**
     * 本地作者库（Room）排序用的键。
     *
     * 「相关度」自己没有键：命中质量由 SQL 里的一段 CASE 排在第一顺位（见 `CreatorDao.paging`），
     * 这里的键只当**同一档命中质量内的兜底** —— 用收藏量，命中一样好时先看更受欢迎的。
     */
    val localSortKey: String
        get() = when (this) {
            FAVED_DESC, FAVED_ASC, RELEVANCE -> "favorited"
            else -> "updated"
        }

    val ascending: Boolean get() = this == UPDATED_ASC || this == FAVED_ASC

    /** 相关度排序：要关键词参与，且只能在本地库的 SQL 分页里算（拿到内存里重排会让翻页顺序错乱）。 */
    val isRelevance: Boolean get() = this == RELEVANCE
}

/**
 * 按 [mode] 对创作者列表排序。两个列表的数据源不同，所以时间与收藏量都用取值函数传入：
 * 发现页本地库的 `updated` 是 Unix 秒、`favorited` 是收藏数；
 * 收藏页云端的 `updated` 是 ISO 字符串、`faved_seq` 是收藏序号。
 */
fun <T> List<T>.sortedForCreators(
    mode: CreatorSortMode,
    updatedOf: (T) -> Long? = { null },
    favedOf: (T) -> Int? = { null },
): List<T> = when (mode) {
    CreatorSortMode.UPDATED_DESC -> sortedWith(compareByDescending { updatedOf(it) ?: 0L })
    CreatorSortMode.UPDATED_ASC -> sortedWith(compareBy { updatedOf(it) ?: 0L })
    CreatorSortMode.FAVED_DESC -> sortedWith(compareByDescending { favedOf(it) ?: 0 })
    CreatorSortMode.FAVED_ASC -> sortedWith(compareBy { favedOf(it) ?: 0 })
    // 相关度只对「有关键词的搜索」有意义；云端收藏作者列表没有关键词，
    // 这里退化为更新时间倒序（该列表也不会展示「相关度」这一项，见 CreatorSortMenu 的 hiddenModes）
    CreatorSortMode.RELEVANCE -> sortedWith(compareByDescending { updatedOf(it) ?: 0L })
}

/**
 * 云端收藏序号索引：帖子 → faved_seq。
 *
 * 站点不返回「收藏时间」，只给一个随收藏时间单调递增的序号，所以「按收藏时间排序」用它。
 */
class FavedSeqIndex(private val seqByKey: Map<String, Int>) {

    fun seqOf(service: String, creatorId: String, postId: String): Int? =
        seqByKey[keyOf(service, creatorId, postId)]

    val isEmpty: Boolean get() = seqByKey.isEmpty()

    companion object {
        val EMPTY = FavedSeqIndex(emptyMap())

        fun keyOf(service: String, creatorId: String, postId: String): String =
            "$service/$creatorId/$postId"

        /** [favorites] 是账号收藏接口按顺序返回的结果；缺 faved_seq 的条目不写序号：
         *  兜底值 favorites.size-index 与真实 faved_seq 数值域重叠，会把缺序号的
         *  条目混进真序号排序中间；不写则走 sortedByFavedSeq 的"null 排最后"分支。 */
        fun from(favorites: List<FavoritePost>): FavedSeqIndex {
            if (favorites.isEmpty()) return EMPTY
            val map = HashMap<String, Int>(favorites.size)
            favorites.forEachIndexed { index, post ->
                post.favedSeq?.let { map[keyOf(post.service, post.user, post.id)] = it }
            }
            return FavedSeqIndex(map)
        }
    }
}

/**
 * 按 [mode] 对**已加载内容**排序（稳定排序：同键条目保持原有相对次序）。
 *
 * 「收藏时间」用云端 [FavedSeqIndex] 的序号：**没有收藏序号的条目不论升降序都排在最后**，
 * 且它们之间保持原有次序 —— 否则未收藏的帖子会混进收藏过的帖子中间，看起来像乱序。
 * 关键词为空时「相关度」没有意义，退化为发布时间倒序。
 */
fun <T> List<T>.sortedFor(
    mode: PostSortMode,
    query: String = "",
    publishedOf: (T) -> String?,
    favedSeqOf: (T) -> Int? = { null },
    titleOf: (T) -> String? = { null },
    excerptOf: (T) -> String? = { null },
    creatorNameOf: (T) -> String? = { null },
): List<T> = when (mode) {
    PostSortMode.PUBLISHED_DESC -> sortedWith(compareByDescending { publishedEpoch(publishedOf(it)) })
    PostSortMode.PUBLISHED_ASC -> sortedWith(compareBy { publishedEpoch(publishedOf(it)) })
    PostSortMode.FAVED_DESC -> sortedByFavedSeq(favedSeqOf, descending = true)
    PostSortMode.FAVED_ASC -> sortedByFavedSeq(favedSeqOf, descending = false)
    PostSortMode.RELEVANCE -> sortedByRelevance(query, publishedOf, titleOf, excerptOf, creatorNameOf)
}

/** 帖子列表（首页 / 搜索 / 作者帖）的便捷重载。 */
fun List<PostSummary>.sortedFor(
    mode: PostSortMode,
    query: String = "",
    favedSeq: FavedSeqIndex = FavedSeqIndex.EMPTY,
): List<PostSummary> = sortedFor(
    mode = mode,
    query = query,
    publishedOf = { it.published },
    favedSeqOf = { favedSeq.seqOf(it.service, it.creatorId, it.postId) },
    titleOf = { it.title },
    excerptOf = { it.excerpt },
    creatorNameOf = { it.creatorId },
)

/** 收藏页（云端收藏）的便捷重载。 */
fun List<FavoritePost>.sortedFor(
    mode: PostSortMode,
    query: String = "",
): List<FavoritePost> = sortedFor(
    mode = mode,
    query = query,
    publishedOf = { it.published },
    favedSeqOf = { it.favedSeq },
    titleOf = { it.title },
    excerptOf = { it.content },
)

/** ISO 时间（可能不带时区）→ epoch 毫秒；解析不了给 0，即当成「最旧」。 */
fun publishedEpoch(iso: String?): Long {
    val raw = iso?.takeIf { it.isNotBlank() } ?: return 0L
    return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .recoverCatching { Instant.parse(raw).toEpochMilli() }
        .getOrDefault(0L)
}

private fun <T> List<T>.sortedByFavedSeq(favedSeqOf: (T) -> Int?, descending: Boolean): List<T> {
    val (faved, rest) = partition { favedSeqOf(it) != null }
    val ordered = if (descending) {
        faved.sortedByDescending { favedSeqOf(it) ?: 0 }
    } else {
        faved.sortedBy { favedSeqOf(it) ?: 0 }
    }
    return ordered + rest
}

private fun <T> List<T>.sortedByRelevance(
    query: String,
    publishedOf: (T) -> String?,
    titleOf: (T) -> String?,
    excerptOf: (T) -> String?,
    creatorNameOf: (T) -> String?,
): List<T> {
    val q = query.trim()
    if (q.isEmpty()) return sortedWith(compareByDescending { publishedEpoch(publishedOf(it)) })
    return sortedWith(
        compareByDescending<T> { SearchRelevance.score(q, titleOf(it), creatorNameOf(it), excerptOf(it)) }
            .thenByDescending { publishedEpoch(publishedOf(it)) },
    )
}
