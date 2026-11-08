package com.archive.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.archive.core.api.ApiClient
import com.archive.core.api.ClearanceCoordinator
import com.archive.core.api.CloudflareManager
import com.archive.core.store.SettingsManager
import com.archive.core.util.CrashHandler
import com.archive.data.repository.CacheRepository
import com.archive.data.repository.PendingFavoriteActionRepository
import com.archive.work.AutoBackupWorker
import com.archive.work.CacheCleanWorker
import com.archive.work.ContentUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ArchiveApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var cacheRepository: CacheRepository

    @Inject
    lateinit var pendingFavoriteActionRepository: PendingFavoriteActionRepository

    @Inject
    lateinit var ocrService: com.archive.app.ocr.OcrService

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 显式初始化 WorkManager，确保 HiltWorkerFactory 时序（抄自 Pawchive Application）
        WorkManager.initialize(this, workManagerConfiguration)
        // 全局崩溃埋点（FEATURE-006）
        CrashHandler.init(this)
        // Cloudflare 过盾：初始化 + 启动异步预热，减少首次请求 403
        CloudflareManager.init(this)

        // 屏蔽名单快照：列表侧只读它的静态判断方法，不在这里主动取一次，
        // Hilt 不会实例化它，两个名单的快照就永远是空的 —— 屏蔽了也不生效。
        runCatching {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(this, com.archive.app.ui.AppEntryPoint::class.java)
                .blockedContentFilter()
        }
        ClearanceCoordinator.preheat()
        // 自动清理缓存（容量阈值策略）
        scheduleAutoCacheCleanIfNeeded()
        // 订阅创作者内容更新周期同步
        scheduleContentUpdateSync()
        // 离线收藏动作队列：启动兜底补写
        pendingFavoriteActionRepository.scheduleSync()
        // 定时自动备份
        if (settingsManager.isAutoBackupEnabled()) {
            AutoBackupWorker.schedule(this, enabled = true)
        }
    }

    /**
     * 系统内存吃紧 / 界面退到后台时交还内存。
     *
     * 之前这里什么都没接 —— 实测收到 `RUNNING_CRITICAL` 后 PSS 一点没降。现在按档位分两类处理：
     *
     * - **图片内存缓存**（重解码只要磁盘缓存还在，代价很小）：后台或前台吃紧都清；
     * - **OCR 引擎**（ONNX Runtime 会话，几十 MB 原生内存，识字时才用得上）：
     *   后台或前台严重吃紧才释放，重建几百毫秒且只在真正识字时才付。
     *
     * 阈值用一个常量判断：`TRIM_MEMORY_UI_HIDDEN`(20) 及以上就是「界面不可见」那一档
     * （BACKGROUND 40 / MODERATE 60 / COMPLETE 80），比逐个枚举更不容易漏。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val hidden = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        val foregroundTight = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL

        if (hidden || foregroundTight) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
        }
        if (hidden || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            ocrService.releaseEngine()
        }
    }

    private fun scheduleAutoCacheCleanIfNeeded() {
        // 先按当前开关与频率把周期任务注册 / 取消好（每天 / 每周 / 每月走周期任务；
        // 「每次启动」没有周期档，下面在启动时排一次性任务）。
        CacheCleanWorker.schedule(this, settingsManager.isAutoCleanCacheEnabled(), settingsManager.getCacheCleanFrequency())

        // 保留原有阈值策略：缓存超阈值时立即清理一次（容量兜底）。
        scheduleThresholdClean()

        // 「每次启动」档：每次冷启动排一次清理（延迟错峰，避开 Coil 初始化与启动期文件竞争）。
        if (settingsManager.isAutoCleanCacheEnabled() &&
            settingsManager.getCacheCleanFrequency() == SettingsManager.CacheCleanFrequency.EVERY_LAUNCH
        ) {
            val request = OneTimeWorkRequestBuilder<CacheCleanWorker>()
                .setInitialDelay(10L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(this@ArchiveApp).enqueue(request)
        }
    }

    private fun scheduleThresholdClean() {
        applicationScope.launch {
            if (!cacheRepository.isAutoCleanEnabled()) return@launch
            if (!CacheCleanWorker.shouldCleanByThreshold(cacheRepository)) return@launch
            val request = OneTimeWorkRequestBuilder<CacheCleanWorker>()
                .setInitialDelay(10L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(this@ArchiveApp).enqueue(request)
        }
    }

    private fun scheduleContentUpdateSync() {
        val intervalMinutes = settingsManager.getSyncIntervalMinutes().toLong()
        val request = PeriodicWorkRequestBuilder<ContentUpdateWorker>(
            intervalMinutes,
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ContentUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // 走 Pawchive core 的 Cloudflare 感知客户端：img.pawchive.pw 并非完全
            // 公开 CDN，部分请求会被 403 拦截，sharedOkHttpClient 内置自动重试
            // 内存缓存给固定上限（原先是堆的 20%，在 12GB 内存的机器上能长到几十 MB，
            // 且随堆上限浮动、行为不可预期）。32MB 约等于 4~5 张信息流封面，
            // 命中不了就从磁盘缓存重解码，代价很小。
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("archive_images").absolutePath.toPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .components {
                // 走 Pawchive core 的 Cloudflare 感知 OkHttp（403 自动重试过盾）
                add(OkHttpNetworkFetcherFactory(callFactory = { ApiClient.sharedOkHttpClient }))
                // minSdk 30 ≥ 28：走 ImageDecoder 通道解码动图（GIF / 动画 WebP / HEIF），
                // 与 Pawchive 的 ImageDecoderDecoder 等价；GifDecoder 仅作兜底
                add(coil3.gif.AnimatedImageDecoder.Factory())
                add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
