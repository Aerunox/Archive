package com.archive.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.archive.core.model.PendingFavoriteActionEntity
import kotlinx.coroutines.flow.Flow

/** 离线收藏动作队列 DAO（FEAT-OFFLINE-FAV-QUEUE）。 */
@Dao
interface PendingFavoriteActionDao {

    /** 幂等键冲突时覆盖（同一对象最新意图优先）。 */
    @Upsert
    suspend fun upsert(action: PendingFavoriteActionEntity)

    /** 全部队列（按入队时间序补写）。 */
    @Query("SELECT * FROM pending_favorite_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingFavoriteActionEntity>

    /** 队列条数（收藏页"待同步"横幅）。 */
    @Query("SELECT COUNT(*) FROM pending_favorite_actions")
    fun observeCount(): Flow<Int>

    /** 补写成功后移除。 */
    @Query("DELETE FROM pending_favorite_actions WHERE objectKey = :objectKey")
    suspend fun delete(objectKey: String)

    /** 清空队列（清除全部本地数据时随其它数据一并清理）。 */
    @Query("DELETE FROM pending_favorite_actions")
    suspend fun clearAll()
}
