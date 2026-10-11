package com.archive.app.data.repo

import com.archive.core.model.Post
import com.archive.core.store.SettingsManager
import com.archive.data.repository.AuthRepository
import com.archive.data.repository.BookmarkManager
import com.archive.data.repository.CreatorSubscriptionRepository
import com.archive.app.data.FavoriteRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一的「收藏 / 取消收藏」动作——列表卡片与帖子详情页共用，避免两处行为不一致：
 *
 * 1. **本地收藏**（Room，无需登录，列表卡片的状态来源）；
 * 2. **云端同步**（已登录时写入账号收藏；离线时由 await 队列在联网后补写）；
 * 3. **收藏即订阅**（设置开启时自动订阅该作者）。
 *
 * 列表卡片只有摘要数据（[PostSummary]），拿不到完整正文，因此归档索引在打开帖子详情时补建；
 * 云端同步与本地收藏在卡片点击时立即执行。
 */
@Singleton
class FavoriteActions @Inject constructor(
    private val localStore: LocalStore,
    private val authRepository: AuthRepository,
    private val bookmarkManager: BookmarkManager,
    private val settingsManager: SettingsManager,
    private val creatorSubscriptionRepository: CreatorSubscriptionRepository,
) {

    /** @return 切换后的收藏状态（true = 已收藏）。 */
    suspend fun toggle(record: FavoriteRecord, fullPost: Post? = null): Boolean {
        val added = localStore.toggleFavorite(record)
        if (added) {
            fullPost?.let { post ->
                runCatching { bookmarkManager.bookmarkPost(post) }
            }
            if (authRepository.isLoggedIn()) {
                // 云端收藏（离线时进待补写队列）
                authRepository.addPostToFavorites(record.service, record.creatorId, record.postId)
                if (settingsManager.isAutoSubscribeOnBookmarkEnabled()) {
                    creatorSubscriptionRepository.subscribe(
                        record.service,
                        record.creatorId,
                        record.creatorName,
                        record.postId,
                    )
                }
            }
        } else {
            runCatching { bookmarkManager.unbookmarkPost(record.service, record.creatorId, record.postId) }
            if (authRepository.isLoggedIn()) {
                authRepository.removePostFromFavorites(record.service, record.creatorId, record.postId)
            }
        }
        return added
    }

    /** 打开详情页时用完整正文补写本地收藏（卡片收藏时拿不到正文）。 */
    suspend fun ensureArchived(record: FavoriteRecord, fullPost: Post) {
        val isFav = runCatching {
            localStore.observeFavorite(record.service, record.creatorId, record.postId)
        }.isSuccess
        if (!isFav) return
        runCatching { bookmarkManager.bookmarkPost(fullPost) }
    }
}
