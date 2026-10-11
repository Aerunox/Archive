package com.archive.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.data.repository.BlockedCreatorManager
import com.archive.app.data.BlockedPost
import com.archive.app.data.BlockedPostManager
import com.archive.app.data.repo.DisplayNames
import com.archive.app.domain.Attachment
import com.archive.app.domain.PostSummary
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.components.AppTabRow
import com.archive.app.ui.components.CreatorRow
import com.archive.app.ui.components.CreatorRowAction
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 屏蔽管理页里的一行作者。 */
data class BlockedCreatorRow(val service: String, val creatorId: String, val name: String)

/**
 * 屏蔽管理：集中查看 / 取消两类屏蔽名单 ——
 *
 * - **已屏蔽帖子**：用主页同款卡片（[PostCard]）展示，点击可进帖子详情；
 * - **已屏蔽作者**：用发现页同款行（[CreatorRow]）展示，带圆形头像，点击可进作者主页。
 *
 * 两套名单互不干扰，取消其中任意一条后，对应内容会立即恢复显示
 * （列表卡片直接读 `BlockedContentFilter` 的快照，见 PostCard）。
 * 页面结构与「收藏」一致：两个子标签页 + Scaffold 下发的内边距。
 */
@HiltViewModel
class BlockedContentViewModel @Inject constructor(
    private val blockedCreators: BlockedCreatorManager,
    private val blockedPosts: BlockedPostManager,
    private val displayNames: DisplayNames,
) : ViewModel() {

    val creators = MutableStateFlow<List<BlockedCreatorRow>>(emptyList())
    val posts = MutableStateFlow<List<BlockedPost>>(emptyList())

    init {
        viewModelScope.launch {
            blockedCreators.blockedCreatorsFlow.collect { list ->
                creators.value = list.map { (service, creatorId) ->
                    BlockedCreatorRow(
                        service = service,
                        creatorId = creatorId,
                        // 帖子接口只给作者 id，这里解析成昵称（解析不出时回退 id）
                        name = runCatching { displayNames.resolve(service, creatorId) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: creatorId,
                    )
                }
            }
        }
        viewModelScope.launch {
            blockedPosts.blockedPostsFlow.collect { list ->
                posts.value = list.sortedWith(compareBy({ it.creatorId }, { it.postId }))
            }
        }
    }

    fun unblockCreator(service: String, creatorId: String) {
        viewModelScope.launch {
            // 作者名单的实现在 IO 线程之外会阻塞主线程（runBlocking 写 DataStore）
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                blockedCreators.unblockCreator(service, creatorId)
            }
        }
    }

    fun unblockPost(post: BlockedPost) {
        viewModelScope.launch {
            blockedPosts.unblockPost(post.service, post.creatorId, post.postId)
        }
    }
}

@Composable
fun BlockedContentScreen(
    navController: NavHostController,
    viewModel: BlockedContentViewModel = hiltViewModel(),
) {
    val creators by viewModel.creators.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val baseUrl = LocalAppSettings.current.baseUrl
    // 与「收藏」页一致：两个子标签页（帖子 / 作者）
    var tab by rememberSaveable { mutableIntStateOf(0) }

    AppScaffold(topBar = {
        AppTopBar(title = "屏蔽管理", onBack = { navController.popBackStack() })
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                // 顶栏占的高度由 Scaffold 下发，不自己扣的话内容会顶到顶栏底下（子标签会被吃掉）
                .padding(top = padding.calculateTopPadding()),
        ) {
            AppTabRow(
                tabs = listOf("帖子（${posts.size}）", "作者（${creators.size}）"),
                selected = tab,
                modifier = Modifier.padding(top = 4.dp),
            ) { tab = it }

            when (tab) {
                0 -> BlockedPostsTab(
                    posts = posts,
                    bottomPadding = padding.calculateBottomPadding(),
                    onUnblock = viewModel::unblockPost,
                    onOpen = { post ->
                        navController.navigate(Routes.post(post.service, post.creatorId, post.postId))
                    },
                )

                else -> BlockedCreatorsTab(
                    creators = creators,
                    baseUrl = baseUrl,
                    bottomPadding = padding.calculateBottomPadding(),
                    onUnblock = viewModel::unblockCreator,
                    onOpen = { row ->
                        navController.navigate(Routes.creator(row.service, row.creatorId))
                    },
                )
            }
        }
    }
}

/** 已屏蔽帖子：主页同款卡片 + 右下角「取消屏蔽」。 */
@Composable
private fun BlockedPostsTab(
    posts: List<BlockedPost>,
    bottomPadding: Dp,
    onUnblock: (BlockedPost) -> Unit,
    onOpen: (BlockedPost) -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (posts.isEmpty()) {
            item { AppEmptyState("没有屏蔽任何帖子") }
        }
        items(posts.size, key = { i -> posts[i].service + "/" + posts[i].creatorId + "/" + posts[i].postId }) { i ->
            val record = posts[i]
            Box {
                PostCard(
                    post = record.toSummary(),
                    // 管理页就是要看被屏蔽的内容，别再把自己隐藏掉
                    hideWhenBlocked = false,
                    onClick = { onOpen(record) },
                )
                AppButton(
                    text = "取消屏蔽",
                    onClick = { onUnblock(record) },
                    primary = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** 已屏蔽作者：发现页同款行（圆形头像 + 名称 + 服务）+ 行尾「取消屏蔽」。 */
@Composable
private fun BlockedCreatorsTab(
    creators: List<BlockedCreatorRow>,
    baseUrl: String,
    bottomPadding: Dp,
    onUnblock: (String, String) -> Unit,
    onOpen: (BlockedCreatorRow) -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (creators.isEmpty()) {
            item { AppEmptyState("没有屏蔽任何作者") }
        }
        items(creators.size, key = { i -> creators[i].service + "/" + creators[i].creatorId }) { i ->
            val row = creators[i]
            CreatorRow(
                name = row.name,
                service = row.service,
                creatorId = row.creatorId,
                baseUrl = baseUrl,
                end = {
                    CreatorRowAction {
                        AppButton(
                            text = "取消屏蔽",
                            onClick = { onUnblock(row.service, row.creatorId) },
                            primary = false,
                        )
                    }
                },
                onClick = { onOpen(row) },
            )
        }
    }
}

/** 把名单记录还原成卡片需要的帖子摘要（封面就是当时存下的缩略图路径）。 */
private fun BlockedPost.toSummary(): PostSummary = PostSummary(
    service = service,
    creatorId = creatorId,
    postId = postId,
    title = title?.takeIf { it.isNotBlank() } ?: "帖子 $postId",
    excerpt = null,
    published = published,
    cover = coverPath?.let { Attachment(name = null, path = it, previewOnly = previewOnly) },
    previewOnly = previewOnly,
)
