package com.archive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.archive.core.store.SettingsManager
import com.archive.data.repository.BookmarkManager
import com.archive.data.repository.CreatorSubscriptionRepository
import com.archive.app.data.repo.FavoriteSeqRepository
import com.archive.app.data.repo.PostRepository
import com.archive.app.domain.PostSummary
import com.archive.app.ui.components.PostSortMenu
import com.archive.app.util.FavedSeqIndex
import com.archive.app.util.PostSortMode
import com.archive.app.util.sortedFor
import com.archive.app.ui.components.LocalAppMenuController
import com.archive.app.ui.components.ExpandableSearchField
import com.archive.app.ui.components.isPostSelectionActive
import com.archive.app.ui.components.selectionChromeHidden
import com.archive.app.ui.components.MenuCloseIcon
import com.archive.app.ui.components.ScrollToTopButton
import com.archive.app.ui.components.rememberGridScrollToTopState
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppErrorState
import com.archive.app.ui.design.AppLoadingItem
import com.archive.app.ui.design.AppPullToRefresh
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.LocalBottomBarScrollState
import com.archive.app.ui.design.ProvideBottomBarScrollState
import com.archive.app.ui.design.hideBottomBarOnScroll
import com.archive.app.ui.design.rememberAppBottomBarScrollState
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.navigation.Routes
import com.archive.app.ui.theme.LocalPurrColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val bookmarkManager: BookmarkManager,
    private val settingsManager: SettingsManager,
    private val favoriteSeqRepository: FavoriteSeqRepository,
    creatorSubscriptionRepository: CreatorSubscriptionRepository,
) : ViewModel() {

    val unreadNotifications: StateFlow<Int> =
        creatorSubscriptionRepository.observeUnreadCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 排序方式（四个列表页共用一套）；只作用于**已加载**的内容，翻页仍走 Paging。 */
    val sortMode = MutableStateFlow(PostSortMode.PUBLISHED_DESC)

    /** 云端收藏序号索引（「按收藏时间排序」用）。 */
    private val favedIndex: StateFlow<FavedSeqIndex> = favoriteSeqRepository.index

    /** 已加载的帖子（按加载次序追加，刷新时清空）——排序的数据源。
     *  写方（Paging 的 map，可能在后台协程）与读方（combine 里排序遍历）并发：
     *  同一把锁保护的可变列表 + 读侧取快照。此前用 CopyOnWriteArrayList——每次
     *  append 都复制整个底层数组，列表越长一页的写放大越大（500 条时每条复制
     *  ~4KB、一页 50 条白拷 200KB）；普通列表加锁是 O(1) 摊还写入，安全性等价。 */
    private val postsLock = Any()
    private val loadedPosts = ArrayList<PostSummary>()
    private fun snapshotPosts(): List<PostSummary> = synchronized(postsLock) { loadedPosts.toList() }
    private val loadedTick = MutableStateFlow(0)

    /** 首页信息流选项（Pawchive 对齐）：同作者仅显示一条 / 隐藏已收藏创作者。 */
    val posts: Flow<PagingData<PostSummary>> = postRepository
        .recentPager()
        .cachedIn(viewModelScope)
        .map { pagingData ->
            synchronized(postsLock) { loadedPosts.clear() }
            loadedTick.value++
            val hideBookmarked = settingsManager.isHideBookmarkedCreatorsEnabled()
            val dedupe = settingsManager.isDedupeByCreatorEnabled()
            val bookmarked = if (hideBookmarked) bookmarkManager.getBookmarkedCreators() else emptySet()
            val seenCreators = mutableSetOf<String>()
            pagingData
                .filter { post ->
                    if (hideBookmarked && (post.service to post.creatorId) in bookmarked) {
                        return@filter false
                    }
                    if (dedupe && !seenCreators.add(post.service + "/" + post.creatorId)) {
                        return@filter false
                    }
                    true
                }
                // Paging 负责加载与预取；这里把加载出来的条目录下来，交给排序后的列表渲染
                .map { post ->
                    synchronized(postsLock) { loadedPosts += post }
                    loadedTick.value++
                    post
                }
        }

    /** 已加载的首页内容按当前排序方式排好序的结果（渲染用它，翻页仍由 Paging 驱动）。 */
    val sortedPosts: StateFlow<List<PostSummary>> =
        combine(loadedTick, sortMode, favedIndex) { _, mode, faved ->
            snapshotPosts().sortedFor(mode, favedSeq = faved)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val query = MutableStateFlow("")
    val currentQuery: StateFlow<String> = query

    /** 站内搜索的已加载结果（同样支持排序；相关度需要关键词，所以排序时带上 query）。
     *  同 loadedPosts：锁保护列表 + 读侧快照（写方与 combine 读方并发）。 */
    private val searchLock = Any()
    private val loadedSearch = ArrayList<PostSummary>()
    private fun snapshotSearch(): List<PostSummary> = synchronized(searchLock) { loadedSearch.toList() }
    private val searchTick = MutableStateFlow(0)

    val searchResults: Flow<PagingData<PostSummary>> = query
        .debounce(500)
        .filter { it.trim().length >= 3 }
        .flatMapLatest { q ->
            postRepository.globalSearchPager(q.trim())
                .cachedIn(viewModelScope)
                .map { pagingData ->
                    synchronized(searchLock) { loadedSearch.clear() }
                    searchTick.value++
                    pagingData.map { post ->
                        synchronized(searchLock) { loadedSearch += post }
                        searchTick.value++
                        post
                    }
                }
        }

    val sortedSearchResults: StateFlow<List<PostSummary>> =
        combine(searchTick, sortMode, favedIndex, query) { _, mode, faved, q ->
            snapshotSearch().sortedFor(mode, q, faved)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** 切换排序；需要收藏序号时按需拉一次云端收藏索引。 */
    fun onSortChange(mode: PostSortMode) {
        sortMode.value = mode
        if (mode.needsFavedSeq) viewModelScope.launch { favoriteSeqRepository.ensureLoaded() }
    }
}

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val items = viewModel.posts.collectAsLazyPagingItems()
    val sortedPosts by viewModel.sortedPosts.collectAsStateWithLifecycle()
    val sortedSearch by viewModel.sortedSearchResults.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    // 每次进入（切回本标签 / 从帖子详情返回）自动刷新
    EnterRefreshEffect { items.refresh() }
    val query by viewModel.currentQuery.collectAsStateWithLifecycle()
    val menuController = LocalAppMenuController.current

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var isGrid by rememberSaveable { mutableStateOf(false) }
    var menuCenter by remember { mutableStateOf(Offset.Zero) }

    // 多选模式：顶栏与多选无关的控件一律让位（搜索、通知、视图切换、全屏菜单、排序）。
    // 状态取自根组件，退出多选即自动恢复。
    val selectionScoped = isPostSelectionActive()

    val gridState = rememberLazyGridState()
    val bottomBarState = LocalBottomBarScrollState.current
        ?: rememberAppBottomBarScrollState()
    val scrollToTop = rememberGridScrollToTopState(gridState, onReachTop = { bottomBarState?.show() })
    AppScaffold(
    ) { padding ->
        ProvideBottomBarScrollState(bottomBarState) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    // Status-bar inset: the header must sit below the clock,
                    // not underneath it (the scaffold reports it via
                    // PaddingValues).
                    .padding(top = padding.calculateTopPadding()),
            ) {
                // In-place expanding search (requirement 1); grid/list view
                // toggle (requirement 8); fullscreen menu trigger with
                // hamburger→close morph (requirements 10 + 4).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        // 多选时整行隐去并停止响应：多选条正压在这一带上，
                        // 留下半截按钮既难看，也仍然点得着（误触的来源之一）
                        .selectionChromeHidden(selectionScoped),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 搜索展开时让出整行：标题、通知与视图切换一起淡出，
                    // 只留搜索框（占满剩余宽度）与全屏菜单按钮
                    AnimatedVisibility(
                        visible = !searchActive,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        AppText("Archive", style = AppTextStyle.TITLE)
                    }
                    // 展开搜索时撤掉这个占位，把整行剩余宽度让给搜索框
                    if (!searchActive) {
                        Box(Modifier.weight(1f))
                    }
                    AnimatedVisibility(
                        visible = !searchActive,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconButton(
                                onClick = { navController.navigate(Routes.NOTIFICATIONS) },
                                icon = LucideIcons.alarm,
                                contentDescription = "通知中心",
                            )
                            androidx.compose.material3.IconButton(
                                onClick = { isGrid = !isGrid },
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = if (isGrid) LucideIcons.listView else LucideIcons.gridView,
                                    contentDescription = if (isGrid) "切换为列表视图" else "切换为网格视图",
                                    tint = LocalPurrColors.current.textSecondary,
                                )
                            }
                        }
                    }
                    ExpandableSearchField(
                        expanded = searchActive,
                        onExpandedChange = {
                            searchActive = it
                            if (!it) viewModel.onQueryChange("")
                        },
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { },
                        // 收起时只占图标一格，展开时吃满剩余宽度
                        modifier = if (searchActive) Modifier.weight(1f) else Modifier,
                    )
                    Box(
                        Modifier
                            .clickable { menuController.open(menuCenter) }
                            .onGloballyPositioned { coords ->
                                menuCenter = coords.positionInRoot() +
                                    Offset(coords.size.width / 2f, coords.size.height / 2f)
                            }
                            .padding(10.dp),
                    ) {
                        MenuCloseIcon(checked = menuController.visible)
                    }
                }

                val searching = searchActive && query.trim().length >= 3
                // 排序：只作用于**已加载**的内容（站点接口没有排序参数）；站内搜索的结果同样按它排列。
                // 多选期间收起：它不在多选条底下，留着就是纯粹的可误触控件（退出多选自动回来）
                AnimatedVisibility(visible = !selectionScoped) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PostSortMenu(
                            current = sortMode,
                            onSelect = viewModel::onSortChange,
                            note = "排序作用于已加载的帖子；「收藏时间」需登录后才有云端收藏序号",
                        )
                    }
                }
                if (searching) {
                    val results = viewModel.searchResults.collectAsLazyPagingItems()
                    PostsGridOrList(
                        gridState = gridState,
                        items = results,
                        sorted = sortedSearch,
                        isGrid = isGrid,
                        bottomPadding = padding.calculateBottomPadding(),
                        modifier = Modifier.weight(1f),
                        onOpen = { post ->
                            navController.navigate(Routes.post(post.service, post.creatorId, post.postId))
                        },
                        onOpenCreator = { post ->
                            navController.navigate(Routes.creator(post.service, post.creatorId))
                        },
                        emptyHint = "没有匹配的帖子",
                        errorHint = "搜索失败，请重试",
                    )
                } else {
                    AppPullToRefresh(
                        isRefreshing = items.loadState.refresh is LoadState.Loading,
                        onRefresh = { items.retry() },
                        modifier = Modifier.weight(1f),
                    ) {
                        PostsGridOrList(
                            gridState = gridState,
                            items = items,
                            sorted = sortedPosts,
                            isGrid = isGrid,
                            bottomPadding = padding.calculateBottomPadding(),
                            modifier = Modifier.fillMaxSize(),
                            onOpen = { post ->
                                navController.navigate(Routes.post(post.service, post.creatorId, post.postId))
                            },
                            onOpenCreator = { post ->
                                navController.navigate(Routes.creator(post.service, post.creatorId))
                            },
                            emptyHint = "暂无内容",
                            errorHint = "加载失败，请检查网络或 Base URL",
                        )
                    }
                }
            }

            // 回到顶部：向下滚过若干行后出现在右下角（底栏之上）
            ScrollToTopButton(
                state = scrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            )
        }
        }
    }
}

@Composable
private fun PostsGridOrList(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    items: LazyPagingItems<PostSummary>,
    /** 已加载内容按当前排序方式排好序的结果；为空时退回 Paging 原始顺序。 */
    sorted: List<PostSummary> = emptyList(),
    isGrid: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onOpen: (PostSummary) -> Unit,
    onOpenCreator: (PostSummary) -> Unit,
    emptyHint: String,
    errorHint: String,
) {
    if (items.loadState.refresh is LoadState.Error && items.itemCount == 0) {
        AppErrorState(message = errorHint, onRetry = { items.retry() }, modifier = modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isGrid) 2 else 1),
        state = gridState,
        modifier = modifier.appListModifier().hideBottomBarOnScroll(),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = bottomPadding + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(if (isGrid) 2 else 1) }) {
                AppEmptyState(emptyHint)
            }
        }
        items(
            count = items.itemCount,
            key = items.itemKey { it.service + it.creatorId + it.postId },
        ) { index ->
            // 触碰 Paging 的条目只为让它继续预取下一页；渲染顺序用排序后的列表
            items[index]
            val post = sorted.getOrNull(index) ?: items[index] ?: return@items
            Box(Modifier.animateItem()) {
                PostCard(
                    post = post,
                    compact = isGrid,
                    onOpenCreator = { onOpenCreator(post) },
                ) { onOpen(post) }
            }
        }
        when (items.loadState.append) {
            is LoadState.Loading -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(if (isGrid) 2 else 1) }) {
                AppLoadingItem()
            }
            is LoadState.Error -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(if (isGrid) 2 else 1) }) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText("加载更多失败", style = AppTextStyle.SECONDARY)
                        AppButton("重试", { items.retry() }, Modifier.padding(top = 8.dp), primary = false)
                    }
                }
            }
            else -> Unit
        }
    }
}
