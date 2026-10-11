package com.archive.app.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.archive.app.data.toDetail
import com.archive.app.data.toDomain
import com.archive.app.data.toLinkDomain
import com.archive.app.data.toSummary
import com.archive.app.data.toSummaryForSearch
import com.archive.app.data.paging.PostPagingSource
import com.archive.app.data.remote.PawchiveApi
import com.archive.app.domain.Announcement
import com.archive.app.domain.CreatorProfile
import com.archive.app.domain.CreatorTag
import com.archive.app.domain.PostComment
import com.archive.app.domain.PostDetail
import com.archive.app.domain.PostSummary
import com.archive.app.domain.SessionFavorite
import com.archive.app.domain.DiscordHit
import com.archive.app.domain.HashSearchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(private val api: PawchiveApi) {

    /**
     * 最近一次成功获取的帖子详情（内存，仅一份）。
     *
     * 用途是让查看器「秒开」：帖子页刚取到这份详情，点开媒体时先用它立刻渲染，
     * 同时后台再刷新一次；不改变任何调用方拿到的数据新鲜度。
     */
    @Volatile
    var lastDetail: PostDetail? = null
        private set

    private fun pager(loader: suspend (Int) -> List<com.archive.app.data.remote.PostDto>): Flow<PagingData<PostSummary>> =
        Pager(PagingConfig(pageSize = PostPagingSource.PAGE_SIZE, prefetchDistance = 25)) {
            PostPagingSource(loader)
        }.flow

    fun recentPager(): Flow<PagingData<PostSummary>> =
        Pager(PagingConfig(pageSize = PostPagingSource.PAGE_SIZE, prefetchDistance = 25)) {
            recentSource()
        }.flow

    fun recentSource(): PagingSource<Int, PostSummary> = PostPagingSource { offset ->
        api.posts(offset = offset)
    }

    fun globalSearchSource(query: String): PagingSource<Int, PostSummary> = PostPagingSource { offset ->
        api.posts(q = query.takeIf { it.length >= 3 }, offset = offset)
    }

    fun creatorPostsSource(
        service: String,
        creatorId: String,
        query: String?,
        tag: String?,
    ): PagingSource<Int, PostSummary> = PostPagingSource { offset ->
        api.creatorPosts(
            service = service,
            creatorId = creatorId,
            q = query?.takeIf { it.length >= 3 },
            tag = tag?.takeIf { it.isNotBlank() },
            offset = offset,
        )
    }

    fun globalSearchPager(query: String): Flow<PagingData<PostSummary>> =
        Pager(PagingConfig(pageSize = PostPagingSource.PAGE_SIZE, prefetchDistance = 25)) {
            globalSearchSource(query)
        }.flow

    fun creatorPostsPager(
        service: String,
        creatorId: String,
        query: String?,
        tag: String?,
    ): Flow<PagingData<PostSummary>> =
        Pager(PagingConfig(pageSize = PostPagingSource.PAGE_SIZE, prefetchDistance = 25)) {
            creatorPostsSource(service, creatorId, query, tag)
        }.flow

    /**
     * 关键词搜索帖子（照 Pawchive：**单次请求**返回一页结果，不做实时/分页轮询——
     * 站点限流较严，连续请求会返回 429）。
     */
    suspend fun searchPosts(query: String): List<PostSummary> =
        // 同分页源：这里不做屏蔽过滤，交给展示层，避免取消屏蔽后结果里缺条目
        api.posts(q = query, offset = 0).mapNotNull { it.toSummary() }

    /** 文件哈希反查：站点帖子 + Discord 帖子（分开返回，UI 分区展示）。 */
    suspend fun searchByHash(hash: String): HashSearchResult {
        val result = com.archive.core.api.ApiClient.publicApi.searchByHash(hash)
        val sitePosts = result.posts.orEmpty().mapNotNull { hit ->
            hit.toSummaryForSearch()
        }
        val discord = result.discordPosts.orEmpty().map { d ->
            DiscordHit(
                id = d.id,
                server = d.server,
                channel = d.channel,
                excerpt = d.substring,
                published = d.published,
            )
        }
        return HashSearchResult(sitePosts, discord)
    }

    suspend fun detail(service: String, creatorId: String, postId: String): PostDetail =
        api.postDetail(service, creatorId, postId)
            .toDetail(service, creatorId, postId)
            .also { lastDetail = it }

    suspend fun profile(service: String, creatorId: String): CreatorProfile =
        api.profile(service, creatorId).toDomain(service, creatorId)

    suspend fun announcements(service: String, creatorId: String): List<Announcement> =
        api.announcements(service, creatorId).mapNotNull { it.toDomain() }

    suspend fun tags(service: String, creatorId: String): List<CreatorTag> =
        api.creatorTags(service, creatorId).map { it.toDomain() }

    suspend fun creatorLinks(service: String, creatorId: String): List<CreatorProfile> =
        api.creatorLinks(service, creatorId)
            .mapNotNull { it.toLinkDomain() }
            // 去掉指向自身与重复条目（Lazy 列表要求 key 唯一）
            .filterNot { it.service == service && it.id == creatorId }
            .distinctBy { it.service to it.id }

    suspend fun comments(service: String, creatorId: String, postId: String): List<PostComment> =
        api.comments(service, creatorId, postId).mapNotNull { it.toDomain() }

    suspend fun accountFavorites(type: String): List<SessionFavorite> =
        api.accountFavorites(type).mapNotNull { it.toDomain() }
}
