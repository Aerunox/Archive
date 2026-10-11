package com.archive.app.data

import com.archive.data.repository.BlockedCreatorManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 已屏蔽帖子的三元组键。 */
data class BlockedPostKey(val service: String, val creatorId: String, val postId: String)

/**
 * 「已屏蔽作者」与「已屏蔽帖子」的进程内快照，供列表数据源**同步**读取。
 *
 * 为什么要这一层：分页源（[com.archive.app.data.paging.PostPagingSource]）在后台线程同步取数，
 * 不能挂起；两个名单各自收集一份快照，判断彼此独立（屏蔽帖子不影响同一作者的其他帖子）。
 * 与本项目里 `CreatorNameCache` / `VideoResumePositions` 的做法一致。
 */
@Singleton
class BlockedContentFilter @Inject constructor(
    creators: BlockedCreatorManager,
    posts: BlockedPostManager,
) {
    init {
        scope.launch {
            creators.blockedCreatorsFlow.collect { creatorSnapshot.value = it.toSet() }
        }
        scope.launch {
            posts.blockedPostsFlow.collect { list ->
                postSnapshot.value = list
                    .map { BlockedPostKey(it.service, it.creatorId, it.postId) }
                    .toSet()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val creatorSnapshot = MutableStateFlow<Set<Pair<String, String>>>(emptySet())
        private val postSnapshot = MutableStateFlow<Set<BlockedPostKey>>(emptySet())

        /** 形如 `service to creatorId` 的已屏蔽作者集合（UI 可收集做即时过滤）。 */
        val blockedCreators: StateFlow<Set<Pair<String, String>>> = creatorSnapshot.asStateFlow()

        /** 已屏蔽帖子集合（UI 可收集做即时过滤）。 */
        val blockedPosts: StateFlow<Set<BlockedPostKey>> = postSnapshot.asStateFlow()

        fun isCreatorBlocked(service: String, creatorId: String): Boolean =
            (service to creatorId) in creatorSnapshot.value

        fun isPostBlocked(service: String, creatorId: String, postId: String): Boolean =
            BlockedPostKey(service, creatorId, postId) in postSnapshot.value
    }
}
