package com.archive.app.data.repo

import androidx.paging.PagingSource
import com.archive.app.data.HistoryRecord
import com.archive.app.data.toEntity
import com.archive.app.data.FavoriteRecord
import com.archive.app.data.local.AppDatabase
import com.archive.app.data.local.FavoritePostEntity
import com.archive.app.data.local.HistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Local (login-independent) favorites and browsing history. */
@Singleton
class LocalStore @Inject constructor(db: AppDatabase) {

    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()

    fun favoritePaging(): PagingSource<Int, FavoritePostEntity> = favoriteDao.observeAll()

    fun isFavorite(service: String, creatorId: String, postId: String): Flow<Boolean> =
        favoriteDao.isFavorite(service, creatorId, postId)

    /** Returns true if the post is favorited after the call. */
    suspend fun toggleFavorite(record: FavoriteRecord): Boolean {
        val exists = favoriteDao.existsOnce(record.service, record.creatorId, record.postId)
        return if (exists) {
            favoriteDao.remove(record.service, record.creatorId, record.postId)
            false
        } else {
            favoriteDao.add(record.toEntity())
            true
        }
    }

    /** 观察某条帖子的本地收藏状态（列表卡片用）。 */
    fun observeFavorite(service: String, creatorId: String, postId: String): Flow<Boolean> =
        favoriteDao.isFavorite(service, creatorId, postId)

    /** 直接移除本地收藏（云端取消收藏时同步清理本地副本）。 */
    suspend fun removeFavorite(service: String, creatorId: String, postId: String) =
        favoriteDao.remove(service, creatorId, postId)

    fun historyPaging(): PagingSource<Int, HistoryEntity> = historyDao.observeAll()

    suspend fun recordHistory(record: HistoryRecord) = historyDao.record(record.toEntity())

    suspend fun clearHistory() = historyDao.clear()

    suspend fun clearFavorites() = favoriteDao.clear()
}
