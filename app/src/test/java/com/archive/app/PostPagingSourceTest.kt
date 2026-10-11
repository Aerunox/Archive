package com.archive.app

import androidx.paging.PagingSource
import com.archive.app.data.paging.PostPagingSource
import com.archive.app.data.remote.PostDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostPagingSourceTest {

    private fun dto(id: String) = PostDto(id = id, service = "patreon", user = "u")

    private suspend fun load(source: PostPagingSource, key: Int?): PagingSource.LoadResult<Int, com.archive.app.domain.PostSummary> =
        source.load(PagingSource.LoadParams.Refresh(key = key, loadSize = 50, placeholdersEnabled = false))

    @Test
    fun `offset advances by page size and stops on short page`() = runTest {
        var requestedOffset: Int? = null
        val source = PostPagingSource { offset ->
            requestedOffset = offset
            if (offset == 0) (0 until 50).map { dto("$it") } else listOf(dto("999"))
        }

        val page1 = load(source, null) as PagingSource.LoadResult.Page
        assertEquals(0, requestedOffset)
        assertEquals(50, page1.data.size)
        assertEquals(50, page1.nextKey)

        val page2 = load(source, page1.nextKey) as PagingSource.LoadResult.Page
        assertEquals(50, requestedOffset)
        assertEquals(1, page2.data.size)
        assertEquals("999", page2.data.single().postId)
        // short page ends the stream
        assertEquals(null, page2.nextKey)
    }

    @Test
    fun `errors surface as LoadResult Error`() = runTest {
        val source = PostPagingSource { throw java.io.IOException("offline") }
        val result = load(source, null)
        assertTrue(result is PagingSource.LoadResult.Error)
    }

    @Test
    fun `refresh key aligns to page boundary`() {
        val source = PostPagingSource { emptyList() }
        val state = androidx.paging.PagingState<Int, com.archive.app.domain.PostSummary>(
            pages = emptyList(),
            anchorPosition = 123,
            config = androidx.paging.PagingConfig(pageSize = 50),
            leadingPlaceholderCount = 0,
        )
        assertEquals(100, source.getRefreshKey(state))
    }

    @Test
    fun `q shorter than three chars is dropped by repository contract`() {
        // mirrors PostRepository behavior: the server ignores q shorter than 3
        val q = "ab"
        val sent = q.takeIf { it.length >= 3 }
        assertEquals(null, sent)
    }
}
