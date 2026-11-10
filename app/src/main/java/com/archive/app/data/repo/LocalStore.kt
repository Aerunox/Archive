package com.archive.app.data.repo

import androidx.paging.PagingSource
import com.archive.app.data.HistoryRecord
import com.archive.app.data.toEntity
import com.archive.app.data.FavoriteRecord
import com.archive.app.data.local.AppDatabase
import com.archive.app.data.local.FavoritePostEntity
import com.archive.app.data.local.HistoryEntity
import com.archive.data.repository.BookmarkManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Local (login-independent) favorites and browsing history. */
@Singleton
class LocalStore @Inject constructor(
    db: AppDatabase,
    private val bookmarkManager: BookmarkManager,
) {

    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()

    /**
     * 离线归档条数（存储页统计）：来自 BookmarkManager 的收藏全文缓存。
     * 旧的 Room offline_archives 统计（v9 已 DROP）从未有写入方，恒为 0。
     */
    fun archiveCount(): Int = bookmarkManager.archivedPostCount()

    /** 离线归档占用字节（收藏全文 JSON 长度之和，近似）。 */
    fun archiveBytes(): Long = bookmarkManager.archivedBytes()

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

    /** 同步读取某条帖子的本地收藏状态（单次查询，冷判定用）。 */
    suspend fun isFavoriteOnce(service: String, creatorId: String, postId: String): Boolean =
        favoriteDao.existsOnce(service, creatorId, postId)

    /** 直接移除本地收藏（云端取消收藏时同步清理本地副本）。 */
    suspend fun removeFavorite(service: String, creatorId: String, postId: String) =
        favoriteDao.remove(service, creatorId, postId)

    fun historyPaging(): PagingSource<Int, HistoryEntity> = historyDao.observeAll()

    suspend fun recordHistory(record: HistoryRecord) = historyDao.record(record.toEntity())

    suspend fun clearHistory() = historyDao.clear()

    suspend fun clearFavorites() = favoriteDao.clear()
}
