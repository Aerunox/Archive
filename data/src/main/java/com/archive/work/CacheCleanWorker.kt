package com.archive.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.archive.core.store.SettingsManager
import com.archive.data.repository.CacheRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 缓存清理 WorkManager 任务（FRONTEND-003；ARCH-003：已迁移至 Hilt 构造函数注入）。
 *
 * 解决问题：
 * - 原 onCreate 中直接清理缓存与 Coil 初始化、图片加载、恢复中下载竞争文件锁
 * - 每次启动无条件清空，无任务协调、无结果记录
 *
 * 策略：
 * - 通过 WorkManager 受约束任务执行，避免与启动期文件访问竞争
 * - 手动（设置页）与自动清理统一走 [CacheRepository] 入口（ARCH-010）
 * - 清理完成后记录时间戳，供设置页展示"上次清理时间"
 */
@HiltWorker
class CacheCleanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cacheRepository: CacheRepository,
    private val settingsManager: SettingsManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 周期任务在用户关闭开关后可能仍有一次排队执行，这里再判一次开关，关掉就静默跳过。
        if (!settingsManager.isAutoCleanCacheEnabled()) return Result.success()
        return try {
            // ARCH-010：统一入口，覆盖 Coil 缓存 / cacheDir / externalCacheDir / API 内存缓存。
            // 注意 clearCache 只清临时缓存目录，绝不碰下载文件（MediaStore/SAF 下载目录）。
            cacheRepository.clearCache()
            cacheRepository.recordClean()
            Result.success()
        } catch (e: Exception) {
            Log.w("CacheCleanWorker", "cache clean failed", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "cache_clean_work"

        /** 按频率注册的周期任务名（与一次性清理的 WORK_NAME 区分）。 */
        const val PERIODIC_WORK_NAME = "cache_clean_periodic"

        /**
         * 按开关与频率调度 / 取消周期清理任务（App 启动与设置页切换时调用）。
         * - 关闭开关：取消周期任务；
         * - 频率为「每次启动」：没有周期档，取消周期任务（一次性清理由 Application 侧排）；
         * - 其余频率：以对应小时数注册唯一周期任务，UPDATE 策略保留既有节奏。
         */
        fun schedule(context: Context, enabled: Boolean, frequency: SettingsManager.CacheCleanFrequency) {
            val workManager = WorkManager.getInstance(context)
            val hours = frequency.periodicHours
            if (!enabled || hours == null) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CacheCleanWorker>(hours, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * 检查是否应基于容量阈值触发自动清理（FRONTEND-003）。
         * - 缓存大小超过阈值时返回 true
         * - 从未清理过时返回 true（首次启动场景）
         *
         * 注：由调用方（PawchiveApplication）传入已注入的 CacheRepository，
         * 消除 work → app 的逆向依赖（ARCH-002 阶段 2）。
         */
        fun shouldCleanByThreshold(cacheRepository: CacheRepository): Boolean {
            return cacheRepository.shouldAutoClean()
        }
    }
}
