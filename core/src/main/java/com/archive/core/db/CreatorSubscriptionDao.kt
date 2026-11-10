package com.archive.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.archive.core.model.CreatorSubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 创作者订阅 DAO（ARCH-FEATURE-003）。
 */
@Dao
interface CreatorSubscriptionDao {

    /** 观察全部订阅（订阅时间倒序）。 */
    @Query("SELECT * FROM creator_subscriptions ORDER BY subscribedAt DESC")
    fun observeAll(): Flow<List<CreatorSubscriptionEntity>>

    /** 同步读取全部订阅（周期同步 Worker 用）。 */
    @Query("SELECT * FROM creator_subscriptions")
    suspend fun getAll(): List<CreatorSubscriptionEntity>

    @Query("SELECT * FROM creator_subscriptions WHERE service = :service AND creatorId = :creatorId")
    suspend fun getById(service: String, creatorId: String): CreatorSubscriptionEntity?

    @Upsert
    suspend fun upsert(subscription: CreatorSubscriptionEntity)

    @Upsert
    suspend fun upsertAll(subscriptions: List<CreatorSubscriptionEntity>)

    @Query("DELETE FROM creator_subscriptions WHERE service = :service AND creatorId = :creatorId")
    suspend fun delete(service: String, creatorId: String)

    /** 清空全部订阅（备份导入覆盖语义用）。 */
    @Query("DELETE FROM creator_subscriptions")
    suspend fun clearAll()

    /**
     * 备份导入（覆盖语义）整体替换。清空与写回同一事务：
     * 中途中断不会留下"订阅被清空但备份内容没写入"的空档。
     */
    @Transaction
    suspend fun importAll(subscriptions: List<CreatorSubscriptionEntity>) {
        clearAll()
        upsertAll(subscriptions)
    }

    /**
     * 定向推进增量基线（周期同步用）。只改 lastPostId 一列而非整行 upsert：
     * 同步开始后才被用户改动的提醒开关（notifyEnabled/pushEnabled）不能被同步启动时的
     * 旧快照回滚覆盖。
     */
    @Query(
        "UPDATE creator_subscriptions SET lastPostId = :lastPostId " +
            "WHERE service = :service AND creatorId = :creatorId"
    )
    suspend fun updateLastPostId(service: String, creatorId: String, lastPostId: String)
}
