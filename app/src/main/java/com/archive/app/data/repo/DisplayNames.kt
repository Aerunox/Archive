package com.archive.app.data.repo

import com.archive.data.repository.CreatorNameCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 作者昵称解析：帖子接口只返回作者 **id**（没有昵称字段），所以昵称需要另外解析。
 *
 * 优先级：
 *  1. 内存缓存（[CreatorNameCache]，Pawchive 的组件，带并发限流）；
 *  2. 本地作者库（Room 里的 /creators 全量数据）；
 *  3. 线上兜底：请求 `/{service}/user/{id}/profile` 取 `name`，成功后写入内存缓存。
 *
 * 第 3 步只对没同步过作者库的设备生效，且每个作者只请求一次（结果进内存缓存）。
 */
@Singleton
class DisplayNames @Inject constructor(
    private val creatorRepository: CreatorRepository,
) {

    /** 立即取缓存值（本地库命中或内存缓存命中）；没有则返回 null。 */
    suspend fun resolve(service: String, creatorId: String): String? {
        CreatorNameCache.getCachedName(service, creatorId)?.let { return it }
        val local = withContext(Dispatchers.IO) {
            runCatching { creatorRepository.lookup(service, creatorId)?.name }.getOrNull()
        }
        if (!local.isNullOrBlank()) {
            CreatorNameCache.cacheCreatorName(service, creatorId, local)
            return local
        }
        // 线上兜底：拉一次作者资料并缓存（失败静默，界面继续显示 id）
        return withContext(Dispatchers.IO) {
            runCatching {
                creatorRepository.profileName(service, creatorId)?.also { name ->
                    CreatorNameCache.cacheCreatorName(service, creatorId, name)
                }
            }.getOrNull()
        }
    }
}
