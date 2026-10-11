package com.archive.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 离线收藏动作（FEAT-OFFLINE-FAV-QUEUE）。
 *
 * 云端收藏写操作（增/删帖子与创作者收藏）在设备离线时入队，
 * 网络恢复后由 FavoriteActionsSyncWorker 依序补写。
 * 以 [objectKey] 为幂等键：同一对象的多次操作只保留最后一次（最新意图优先，
 * 例如离线时先收藏再取消收藏，最终只补写"取消收藏"）。
 */
@Entity(tableName = "pending_favorite_actions")
data class PendingFavoriteActionEntity(
    /** 幂等键："post|{service}|{creatorId}|{postId}" 或 "creator|{service}|{creatorId}"。 */
    @PrimaryKey
    val objectKey: String,
    /** 动作类型（[PendingFavoriteActionKind] 的 name）。 */
    val kind: String,
    val service: String,
    val creatorId: String,
    /** 帖子收藏动作才有；创作者收藏动作为 null。 */
    val postId: String?,
    val createdAt: Long
)

/** 离线收藏动作类型。 */
enum class PendingFavoriteActionKind {
    ADD_POST,
    REMOVE_POST,
    ADD_CREATOR,
    REMOVE_CREATOR
}
