package com.archive.app.data.repo

import com.archive.core.model.FavoritePost
import com.archive.data.repository.AuthRepository
import com.archive.app.util.FavedSeqIndex
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 云端收藏序号（`faved_seq`）索引。
 *
 * 站点不返回「收藏时间」，只给一个随收藏时间单调递增的收藏序号，所以各列表页的
 * 「按收藏时间排序」用它。只有登录后才有数据：未登录或请求失败时索引为空，
 * 此时按收藏时间排序不会改变列表顺序（排序菜单里对应项也会标为需登录）。
 *
 * 索引在进程内只拉一次，避免每切一次排序都打站点（站点限流很严）。
 */
@Singleton
class FavoriteSeqRepository @Inject constructor(
    private val authRepository: AuthRepository,
) {

    private val _index = MutableStateFlow(FavedSeqIndex.EMPTY)
    val index: StateFlow<FavedSeqIndex> = _index

    @Volatile
    private var loaded = false

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    /** 需要时拉取账号收藏并建成索引；重复调用是幂等的（已加载过就直接返回）。 */
    suspend fun ensureLoaded() {
        if (loaded || !isLoggedIn()) return
        val all = mutableListOf<FavoritePost>()
        var offset: Int? = null
        var pageSize = 0
        for (page in 0 until MAX_PAGES) {
            val items = authRepository.syncFavoritePosts(offset = offset).getOrElse { return }
            if (items.isEmpty()) break
            if (pageSize == 0) pageSize = items.size
            all += items
            offset = all.size
            // 站点分页没有总数：取到「不足一页」就是末尾
            if (items.size < pageSize) break
        }
        if (all.isNotEmpty()) {
            _index.value = FavedSeqIndex.from(all)
            loaded = true
        }
    }

    private companion object {
        const val MAX_PAGES = 5
    }
}
