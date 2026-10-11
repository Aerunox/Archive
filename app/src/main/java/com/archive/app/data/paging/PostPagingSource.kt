package com.archive.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.archive.app.data.toSummary
import com.archive.app.data.remote.PostDto
import com.archive.app.domain.PostSummary

/**
 * Offset based network paging. The API has no total count and ignores `q`
 * shorter than 3 characters (both verified live), so pages load until a short
 * page ends the stream.
 */
class PostPagingSource(
    private val loader: suspend (offset: Int) -> List<PostDto>,
) : PagingSource<Int, PostSummary>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PostSummary> =
        try {
            val offset = params.key ?: 0
            val items = loader(offset)
            LoadResult.Page(
                // 屏蔽**不在这里**过滤：分页一旦把条目丢掉，取消屏蔽后已加载的页里就再也找不回来，
                // 刷新也无济于事。过滤放在展示层（PostCard 按两份名单决定是否渲染），
                // 这样屏蔽/取消屏蔽都是即时生效，也不丢数据。
                data = items.mapNotNull { it.toSummary() },
                prevKey = null,
                nextKey = if (items.size < PAGE_SIZE) null else offset + PAGE_SIZE,
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }

    override fun getRefreshKey(state: PagingState<Int, PostSummary>): Int? =
        state.anchorPosition?.let { anchor -> (anchor / PAGE_SIZE) * PAGE_SIZE }

    companion object {
        const val PAGE_SIZE = 50
    }
}
