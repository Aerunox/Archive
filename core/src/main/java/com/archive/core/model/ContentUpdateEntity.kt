package com.archive.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 创作者订阅实体（ARCH-FEATURE-003：内容更新订阅）。
 *
 * 复合主键 (service, creatorId)：同一创作者 id 在不同服务下互不冲突。
 * [lastPostId] 记录最近一次同步到的最新帖 id；为 null 表示首次订阅
 * （以订阅时最新帖为基线，不把历史帖当新帖通知）。
 */
@Entity(
    tableName = "creator_subscriptions",
    primaryKeys = ["service", "creatorId"],
    indices = [Index("service")]
)
data class CreatorSubscriptionEntity(
    val service: String,
    val creatorId: String,
    /** 订阅时缓存的创作者名称（更新列表展示用，不实时刷新）。 */
    val name: String?,
    /** 最近一次同步到的最新帖 id；null = 首次订阅，仅初始化基线不产生通知。 */
    val lastPostId: String?,
    val subscribedAt: Long,
    /**
     * 是否接收该作者的内容更新提醒（作者主页长按「关注」按钮设置）。
     *
     * 关闭后周期同步**照常推进基线**，只是不再写入内容更新行 ——
     * 所以重新打开时不会补推关闭期间积压的帖子（否则一开就是几十条旧帖轰炸）。
     */
    val notifyEnabled: Boolean = true,
    /**
     * 是否把该作者的更新推送到系统通知栏（在 [notifyEnabled] 之上再叠一层）。
     *
     * 应用内条目与系统推送分开：只想在应用内看到、不想被通知栏打扰时用这个开关。
     */
    val pushEnabled: Boolean = true
)

/**
 * 内容更新通知实体（ARCH-FEATURE-003）。
 *
 * 每次周期同步发现"订阅基线之后的新帖"时插入一行；(service, creatorId, postId)
 * 唯一索引 + [androidx.room.OnConflictStrategy.IGNORE] 保证同一帖子不会重复通知。
 * [read] 标记未读状态，未读数即 read = false 的行数。
 */
@Entity(
    tableName = "content_updates",
    indices = [
        Index("service"),
        Index(value = ["service", "creatorId", "postId"], unique = true),
        Index("read")
    ]
)
data class ContentUpdateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val service: String,
    val creatorId: String,
    val postId: String,
    val postTitle: String?,
    val discoveredAt: Long,
    val read: Boolean = false
)
