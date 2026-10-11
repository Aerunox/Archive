package com.archive.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.archive.core.error.AppError
import com.archive.core.model.PendingFavoriteActionKind
import com.archive.data.repository.AuthRepository
import com.archive.data.repository.PendingFavoriteActionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 离线收藏动作补写（FEAT-OFFLINE-FAV-QUEUE）。
 *
 * 网络恢复后按入队时间序逐条补写离线期间的收藏写操作：
 * - 成功 → 从队列移除；
 * - 4xx（服务端明确拒绝，如对象已不存在）→ 放弃该条并移除，避免队列永久堵塞；
 * - 其它失败（5xx / 网络抖动）→ 保留队列并整体退避重试；
 * - 连续重试超过 [MAX_RUN_ATTEMPTS] 次仍失败 → 本轮放弃（队列保留），
 *   下次任一收藏写入或应用启动会重新调度。
 */
@HiltWorker
class FavoriteActionsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val queueRepository: PendingFavoriteActionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 未登录无从补写：队列保留，登录后由下一次调度补写
        if (!authRepository.isLoggedIn()) {
            Log.i(TAG, "not logged in, keep queue for later sync")
            return Result.success()
        }

        val actions = queueRepository.getAll()
        Log.i(TAG, "sync ${actions.size} pending favorite actions")
        for (action in actions) {
            val kind = runCatching { PendingFavoriteActionKind.valueOf(action.kind) }.getOrElse {
                Log.w(TAG, "unknown action kind '${action.kind}', drop ${action.objectKey}")
                queueRepository.remove(action.objectKey)
                continue
            }
            val postId = action.postId
            val result = when (kind) {
                PendingFavoriteActionKind.ADD_POST ->
                    authRepository.addPostToFavoritesDirect(
                        action.service, action.creatorId,
                        postId ?: run { queueRepository.remove(action.objectKey); continue }
                    )
                PendingFavoriteActionKind.REMOVE_POST ->
                    authRepository.removePostFromFavoritesDirect(
                        action.service, action.creatorId,
                        postId ?: run { queueRepository.remove(action.objectKey); continue }
                    )
                PendingFavoriteActionKind.ADD_CREATOR ->
                    authRepository.addCreatorToFavoritesDirect(action.service, action.creatorId)
                PendingFavoriteActionKind.REMOVE_CREATOR ->
                    authRepository.removeCreatorFromFavoritesDirect(action.service, action.creatorId)
            }
            when {
                result.isSuccess -> queueRepository.remove(action.objectKey)
                // 服务端明确拒绝（4xx）：该条永远补写不成功，放弃并移除
                (result.exceptionOrNull() as? AppError.Server)?.code in 400..499 -> {
                    Log.w(TAG, "permanent failure for ${action.objectKey}, drop")
                    queueRepository.remove(action.objectKey)
                }
                else -> Log.w(
                    TAG,
                    "retryable failure for ${action.objectKey}: ${result.exceptionOrNull()?.message}"
                )
            }
        }

        val remaining = queueRepository.getAll().size
        return if (remaining == 0) {
            Result.success()
        } else if (runAttemptCount < MAX_RUN_ATTEMPTS) {
            Result.retry()
        } else {
            Log.w(TAG, "give up after $runAttemptCount attempts, $remaining actions kept in queue")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "FavoriteActionsSync"

        /** 单次调度的最大重试次数；超过后放弃本轮（队列保留，等待下次调度）。 */
        private const val MAX_RUN_ATTEMPTS = 10
    }
}
