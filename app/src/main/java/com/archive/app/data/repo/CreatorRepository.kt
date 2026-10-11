package com.archive.app.data.repo

import androidx.paging.PagingSource
import com.archive.app.data.toEntity
import com.archive.app.data.local.AppDatabase
import com.archive.app.data.local.CreatorEntity
import com.archive.app.data.remote.PawchiveApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creators are ~96k rows (15MB JSON) — fetched wholesale and cached in Room;
 * search/sort/filter/paging all run locally.
 */
@Singleton
class CreatorRepository @Inject constructor(
    private val api: PawchiveApi,
    db: AppDatabase,
) {
    private val dao = db.creatorDao()

    suspend fun refresh(): Int {
        val entities = api.creators().map { it.toEntity() }
        entities.chunked(5000).forEach { dao.upsertAll(it) }
        return entities.size
    }

    suspend fun cachedCount(): Int = dao.count()

    /** 按 service+id 查本地作者记录（相似/关联列表补全收藏数与更新时间）。 */
    suspend fun lookup(service: String, id: String): CreatorEntity? = dao.find(service, id)

    /** 线上兜底：读取作者昵称（帖子接口不返回昵称，只有 id）。 */
    suspend fun profileName(service: String, creatorId: String): String? =
        runCatching { api.profile(service, creatorId).name?.takeIf { it.isNotBlank() } }.getOrNull()

    /** 名称或 ID 匹配（搜索页创作者 Tab）。 */
    suspend fun searchByNameOrId(query: String): List<CreatorEntity> = dao.searchByNameOrId(query)

    /** 按 ID 精确匹配（pixiv pid / 纯数字查询时把精确命中排最前）。 */
    suspend fun byId(id: String): List<CreatorEntity> = dao.byId(id)

    /** 本地作者库关键词搜索（搜索页创作者 Tab 用）。 */
    suspend fun searchAll(query: String): List<CreatorEntity> = dao.searchAll(query)

    suspend fun clearCache() = dao.clear()

    /** 发现页本地作者库分页（排序键 + 升降序，见 [com.archive.app.util.CreatorSortMode]）。 */
    fun paging(
        service: String?,
        query: String,
        sortKey: String,
        ascending: Boolean,
        relevance: Boolean = false,
    ): PagingSource<Int, CreatorEntity> = dao.paging(service, query, sortKey, ascending, relevance)
}
