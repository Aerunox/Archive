package com.archive.data.repository

import android.content.Context
import android.widget.Toast
import com.archive.core.db.PendingFavoriteActionDao
import com.archive.core.model.PendingFavoriteActionEntity
import com.archive.core.model.PendingFavoriteActionKind
import com.archive.data.R
import com.archive.data.work.FavoriteActionsSyncWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 离线收藏动作队列（FEAT-OFFLINE-FAV-QUEUE）。
 *
 * 云端收藏写操作在设备离线时入队并调度 [FavoriteActionsSyncWorker]
 * （NetworkType.CONNECTED 约束 + 线性退避），联网后自动补写。
 * 队列以对象为幂等键，最新意图覆盖旧意图；补写成功即移除。
 */
@Singleton
class PendingFavoriteActionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PendingFavoriteActionDao
) {

    /** 待同步条数流（收藏页"待同步"横幅）。 */
    fun observeCount(): Flow<Int> = dao.observeCount()

    /**
     * 入队一条离线收藏动作（同一对象最新意图覆盖旧意图），
     * 并调度网络恢复后的补写任务；随后以轻提示告知用户"联网后自动同步"。
     */
    suspend fun enqueue(
        kind: PendingFavoriteActionKind,
        service: String,
        creatorId: String,
        postId: String?
    ) {
        val objectKey =
            if (postId != null) "post|$service|$creatorId|$postId" else "creator|$service|$creatorId"
        dao.upsert(
            PendingFavoriteActionEntity(
                objectKey = objectKey,
                kind = kind.name,
                service = service,
                creatorId = creatorId,
                postId = postId,
                createdAt = System.currentTimeMillis()
            )
        )
        scheduleSync()
        // Toast 用 Handler 投递而非 withContext(Dispatchers.Main)：仓库方法可能在
        // 测试/阻塞上下文中被 runBlocking 调用，挂起等待 Main 会与调用线程形成死锁
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.offline_action_queued, Toast.LENGTH_SHORT).show()
        }
    }

    /** 全部队列（Worker 按入队时间序补写）。 */
    suspend fun getAll(): List<PendingFavoriteActionEntity> = dao.getAll()

    /** 补写成功 / 永久失败后移除。 */
    suspend fun remove(objectKey: String) = dao.delete(objectKey)

    /** 调度一次补写任务（唯一任务名串联；网络恢复后执行）。 */
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<FavoriteActionsSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, WORK_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // APPEND_OR_REPLACE：连续入队时串联到未跑的既有任务之后；前序失败则以新任务替换
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        /** 唯一任务名（测试亦用于断言调度结果）。 */
        internal const val WORK_NAME = "favorite_actions_sync"

        /** 补写失败重试的线性退避基数（秒）。 */
        private const val WORK_BACKOFF_SECONDS = 30L
    }
}
