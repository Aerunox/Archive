package com.archive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.core.model.FavoriteCreator
import com.archive.core.model.FavoritePost
import com.archive.data.repository.AuthRepository
import com.archive.app.data.SettingsRepository
import com.archive.app.data.remote.UrlProvider
import com.archive.app.data.repo.LocalStore
import com.archive.app.domain.Attachment
import com.archive.app.domain.PostSummary
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.archive.app.ui.components.CreatorAvatar
import com.archive.app.ui.components.ScrollToTopButton
import com.archive.app.ui.components.ScrollToTopState
import com.archive.app.ui.components.rememberListScrollToTopState
import com.archive.app.ui.components.AppTabRow
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.components.ThumbImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppErrorState
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppLoadingItem
import com.archive.app.ui.design.AppPullToRefresh
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.LocalBottomBarScrollState
import com.archive.app.ui.design.hideBottomBarOnScroll
import com.archive.app.ui.design.rememberAppBottomBarScrollState
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.navigation.Routes
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.Fmt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.archive.app.util.PostSortMode
import com.archive.app.util.sortedFor
import com.archive.app.util.CreatorSortMode
import com.archive.app.util.publishedEpoch
import com.archive.app.util.sortedForCreators
import com.archive.app.ui.components.CreatorSortMenu
import com.archive.app.ui.components.PostSortMenu
import com.archive.app.ui.components.isPostSelectionActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * 收藏：帖子收藏 / 作者收藏，**数据均来自账号云端收藏**（需登录）。
 *
 * 未登录时展示登录引导；无需登录的本地收藏仍保留在本地库中。
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val localStore: LocalStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    data class ListState<T>(
        val items: List<T> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
    )

    val loggedIn = MutableStateFlow(authRepository.isLoggedIn())
    val username = MutableStateFlow(authRepository.getUsername())

    val baseUrl = settingsRepository.settings
        .map { it.baseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UrlProvider.DEFAULT_BASE_URL)

    val posts = MutableStateFlow(ListState<FavoritePost>())
    val creators = MutableStateFlow(ListState<FavoriteCreator>())

    /** 排序方式：只作用于**已加载**的收藏内容（站点接口没有排序参数）。 */
    val sortMode = MutableStateFlow(PostSortMode.PUBLISHED_DESC)

    /** 已加载的收藏帖子按当前排序方式排好序（渲染用；「加载更多」拿到的仍是云端顺序）。 */
    val sortedPosts: StateFlow<List<FavoritePost>> =
        combine(posts, sortMode) { state, mode -> state.items.sortedFor(mode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortMode(mode: PostSortMode) {
        sortMode.value = mode
    }

    init {
        refreshAll()
    }

    fun refreshAll() {
        loggedIn.value = authRepository.isLoggedIn()
        val newUser = authRepository.getUsername()
        // 只有换了账号（或退出登录）才清空：上一个账号的收藏不能在刷新期间继续展示。
        // 同一账号重进页面保留已加载内容，后台刷新的进行状态由下拉指示器承载
        // （内置组件的 refresh-on-entry 设计，与发现页一致）。
        val accountChanged = newUser != username.value
        username.value = newUser
        if (!loggedIn.value || accountChanged) {
            posts.value = ListState()
            creators.value = ListState()
        }
        if (!loggedIn.value) return
        loadPosts(reset = true)
        loadCreators()
    }

    /** 请求代际号：reset 会清空列表，在途的 loadMore 回调晚于 reset 时
     *  若不校验，旧页数据会被 append 进刷新后的空列表；整页重复时还会把
     *  endReached 误判为 true 提前终止分页。回调时校验代际即可挡掉。 */
    private var requestGen = 0

    fun loadPosts(reset: Boolean) {
        val current = posts.value
        if (!reset && (current.loading || current.loadingMore || current.endReached)) return
        val gen = ++requestGen
        // 状态同步置位：自动续页与手动点击可能落在同一帧，靠它把重复请求挡掉。
        // reset **不清空已加载的列表**：下拉刷新时内容保持可见，刷新状态由
        // AppPullToRefresh 的内置指示器承载（与首页/发现页一致）；整屏加载占位
        // 只由 Tab 里「loading 且无内容」的分支负责（真·首次加载）。
        posts.value = if (reset) {
            current.copy(loading = true, error = null, endReached = false)
        } else {
            current.copy(loadingMore = true, error = null)
        }
        viewModelScope.launch {
            authRepository.syncFavoritePosts(
                offset = if (reset) null else current.items.size,
                forceRefresh = reset,
            ).onSuccess { page ->
                // 已被更新的请求取代（如 loadMore 在途时来了 reset），丢弃本次结果
                if (gen != requestGen) return@onSuccess
                val existing = posts.value.items
                // 站点按 offset 分页时会返回重复条目（同一帖两条 faved_seq），
                // 而 LazyColumn 的 key 重复会直接抛异常 —— 这里按「服务/作者/帖」去重
                val seen = existing.mapTo(HashSet(existing.size)) { it.identityKey() }
                val fresh = page.filter { seen.add(it.identityKey()) }
                posts.value = posts.value.copy(
                    items = if (reset) fresh else existing + fresh,
                    loading = false,
                    loadingMore = false,
                    // 空页，或整页都是已见过的重复 = 已到底
                    endReached = fresh.isEmpty(),
                    error = null,
                )
            }.onFailure { error ->
                if (gen != requestGen) return@onFailure
                posts.value = posts.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = error.message ?: "加载收藏帖子失败",
                )
            }
        }
    }

    fun loadCreators() {
        val current = creators.value
        if (current.loading) return
        viewModelScope.launch {
            creators.value = current.copy(loading = true, error = null)
            authRepository.syncFavoriteCreators(forceRefresh = true)
                .onSuccess { list ->
                    rawCreators = list
                    creators.value = ListState(items = sortCreators(list, creatorSortMode.value))
                }
                .onFailure { error ->
                    // 失败**不清空已有列表**（错误行内联在列表末尾展示）：
                    // 下拉重试时内容保持可见，与 posts 的失败路径一致
                    creators.value = creators.value.copy(
                        loading = false,
                        error = error.message ?: "加载收藏作者失败",
                    )
                }
        }
    }

    /** 云端收藏创作者的原始顺序（切换排序时重排用）。 */
    private var rawCreators: List<FavoriteCreator> = emptyList()

    /** 收藏作者的排序方式（收藏序号 / 更新时间）。 */
    val creatorSortMode = MutableStateFlow(CreatorSortMode.UPDATED_DESC)

    fun setCreatorSort(mode: CreatorSortMode) {
        creatorSortMode.value = mode
        creators.value = creators.value.copy(items = sortCreators(rawCreators, mode))
    }

    /** 云端 `updated` 是 ISO 字符串、`faved_seq` 是收藏序号，两者都是「越大越靠前/越新」。 */
    private fun sortCreators(list: List<FavoriteCreator>, mode: CreatorSortMode): List<FavoriteCreator> =
        list.sortedForCreators(
            mode,
            updatedOf = { publishedEpoch(it.updated) },
            favedOf = { it.favedSeq },
        )

    /** 取消收藏帖子（云端 + 本地归档索引 + 本地收藏一起清理）。 */
    fun unfavoritePost(post: FavoritePost) {
        viewModelScope.launch {
            authRepository.removePostFromFavorites(post.service, post.user, post.id)
                .onSuccess {
                    posts.value = posts.value.copy(
                        items = posts.value.items.filterNot { it.id == post.id && it.user == post.user },
                    )
                    localStore.removeFavorite(post.service, post.user, post.id)
                }
                .onFailure { posts.value = posts.value.copy(error = it.message ?: "取消收藏失败") }
        }
    }

    /** 取消收藏作者（云端）。 */
    fun unfavoriteCreator(creator: FavoriteCreator) {
        viewModelScope.launch {
            authRepository.removeCreatorFromFavorites(creator.service, creator.id)
                .onSuccess {
                    creators.value = creators.value.copy(
                        items = creators.value.items.filterNot {
                            it.id == creator.id && it.service == creator.service
                        },
                    )
                }
                .onFailure { creators.value = creators.value.copy(error = it.message ?: "取消收藏失败") }
        }
    }
}

/** 收藏帖的稳定标识：云端可能把同一帖返回两次（两条 faved_seq），用它去重。 */
private fun FavoritePost.identityKey(): String = "$service/$user/$id"

private fun FavoritePost.fileOrNull(): com.archive.core.model.PostFile? = file

private fun FavoritePost.postCover(): Attachment? {
    val f = fileOrNull()
    val path = f?.path
    if (path != null) return Attachment(f.name, path)
    val attachment = attachments?.firstOrNull { !it.path.isNullOrBlank() } ?: return null
    return Attachment(attachment.name, attachment.path.orEmpty())
}

private fun FavoritePost.toSummary(): PostSummary = PostSummary(
    service = service,
    creatorId = user,
    postId = id,
    title = title?.ifBlank { null } ?: "(无标题)",
    excerpt = com.archive.app.util.HtmlParser.htmlToPlainText(content)
        .take(160)
        .takeIf { it.isNotBlank() },
    published = published,
    cover = postCover(),
    previewOnly = fileOrNull()?.previewOnly ?: false,
)

@Composable
fun FavoritesScreen(
    navController: NavHostController,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val postsState by viewModel.posts.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creators.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val sortedPosts by viewModel.sortedPosts.collectAsStateWithLifecycle()
    val creatorSortMode by viewModel.creatorSortMode.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 每次进入自动刷新账号收藏
    EnterRefreshEffect { viewModel.refreshAll() }

    // 多选模式：与多选无关的排序控件让位（退出多选自动恢复）
    val selectionScoped = isPostSelectionActive()
    val postsListState = rememberLazyListState()
    val creatorsListState = rememberLazyListState()
    val bottomBarState = LocalBottomBarScrollState.current
        ?: rememberAppBottomBarScrollState()
    val postsScrollToTop = rememberListScrollToTopState(postsListState, onReachTop = { bottomBarState?.show() })
    val creatorsScrollToTop = rememberListScrollToTopState(creatorsListState, onReachTop = { bottomBarState?.show() })
    AppScaffold(
        topBar = {
            AppTopBar(title = "收藏")
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            if (!loggedIn) {
                AppEmptyState(
                    "收藏来自账号云端收藏\n登录后可查看收藏的帖子与作者",
                )
                AppButton(
                    text = "去登录",
                    onClick = { navController.navigate(Routes.LOGIN) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = padding.calculateBottomPadding() + 24.dp),
                )
                return@Column
            }

            AppTabRow(
                tabs = listOf(
                    "帖子（${postsState.items.size}）",
                    "作者（${creatorsState.items.size}）",
                ),
                selected = tab,
                modifier = Modifier.padding(top = 4.dp),
            ) { tab = it }

            // 排序：只影响展示顺序（云端「加载更多」拿到的仍是接口顺序）。
            // 多选期间收起：它不在多选条底下，留着就是纯粹的可误触控件（退出多选自动回来）
            AnimatedVisibility(visible = !selectionScoped) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (tab == 0) {
                        PostSortMenu(
                            current = sortMode,
                            onSelect = viewModel::setSortMode,
                            // 收藏页没有搜索关键词，「相关度」没有意义，不列出来
                            hiddenModes = setOf(PostSortMode.RELEVANCE),
                            note = "排序作用于已加载的收藏；「收藏时间」用的是云端收藏序号",
                        )
                    } else {
                        CreatorSortMenu(
                            current = creatorSortMode,
                            onSelect = viewModel::setCreatorSort,
                            // 收藏作者列表没有搜索关键词，「相关度」在这里无从计算
                            hiddenModes = setOf(CreatorSortMode.RELEVANCE),
                            note = "排序作用于已加载的收藏作者",
                        )
                    }
                }
            }

            AppPullToRefresh(
                isRefreshing = if (tab == 0) postsState.loading else creatorsState.loading,
                onRefresh = {
                    if (tab == 0) viewModel.loadPosts(reset = true) else viewModel.loadCreators()
                },
            ) {
            when (tab) {
                0 -> PostsTab(
                    bottomBarState = bottomBarState,
                    listState = postsListState,
                    scrollToTop = postsScrollToTop,
                    state = postsState.copy(items = sortedPosts),
                    username = username,
                    bottomPadding = padding.calculateBottomPadding(),
                    onOpen = { post ->
                        navController.navigate(Routes.post(post.service, post.user, post.id))
                    },
                    onRemove = { viewModel.unfavoritePost(it) },
                    onRetry = { viewModel.loadPosts(reset = true) },
                    onLoadMore = { viewModel.loadPosts(reset = false) },
                )
                else -> CreatorsTab(
                    bottomBarState = bottomBarState,
                    listState = creatorsListState,
                    scrollToTop = creatorsScrollToTop,
                    state = creatorsState,
                    baseUrl = baseUrl,
                    bottomPadding = padding.calculateBottomPadding(),
                    onOpen = { creator ->
                        navController.navigate(Routes.creator(creator.service, creator.id))
                    },
                    onRemove = { viewModel.unfavoriteCreator(it) },
                    onRetry = { viewModel.loadCreators() },
                )
            }
            }
        }

        // 回到顶部：绑定当前标签的列表
        if (loggedIn) {
            ScrollToTopButton(
                state = if (tab == 0) postsScrollToTop else creatorsScrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            )
        }
        }
    }
}

@Composable
private fun PostsTab(
    bottomBarState: com.archive.app.ui.design.AppBottomBarScrollState,
    listState: LazyListState,
    scrollToTop: ScrollToTopState,
    state: FavoritesViewModel.ListState<FavoritePost>,
    username: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (FavoritePost) -> Unit,
    onRemove: (FavoritePost) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 「加载更多」：滚到已加载内容的末尾就自动续页；按钮只在**没法靠滚动再触发**时才出现
    // （内容不足一屏，或上一次续页失败需要手动重试）——那才是「界面已加载到上限」的状态
    val itemCount = state.items.size
    val atLoadedEnd by remember(itemCount) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            itemCount > 0 && lastVisible >= itemCount - 1
        }
    }
    LaunchedEffect(atLoadedEnd, state.loadingMore, state.endReached, state.error, itemCount) {
        // 失败时不再自动重试，交给下面的按钮
        if (atLoadedEnd && !state.loadingMore && !state.endReached && state.error == null) onLoadMore()
    }
    when {
        // 整屏加载占位只用于真·首次加载（无内容可显示）；下拉刷新/重进页面时
        // 列表保持可见，刷新状态由 AppPullToRefresh 的内置指示器承载
        state.loading && state.items.isEmpty() -> AppLoading()
        state.error != null && state.items.isEmpty() -> PullableState {
            AppErrorState(
                message = "加载失败：${state.error}",
                onRetry = onRetry,
            )
        }
        state.items.isEmpty() -> PullableState {
            AppEmptyState("账号「${username ?: ""}」还没有收藏的帖子")
        }
        else -> LazyColumn(
            bottomBarState.attach(Modifier.fillMaxSize().appListModifier()),
            state = listState,
                contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
            ) {
            items(
                state.items.size,
                key = { state.items[it].service + state.items[it].user + state.items[it].id },
            ) { i ->
                val post = state.items[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PostCard(
                        post = post.toSummary(),
                        preferOriginal = true,
                        isFavorite = true,
                        onFavorite = { onRemove(post) },
                        onClick = { onOpen(post) },
                    )
                }
            }
            if (state.loadingMore) {
                item { AppLoadingItem() }
            } else if (!state.endReached && (!listState.canScrollForward || state.error != null)) {
                item {
                    AppButton(
                        text = "加载更多",
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        primary = false,
                    )
                }
            }
            state.error?.let { message ->
                item {
                    AppText(
                        message,
                        style = AppTextStyle.SMALL,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatorsTab(
    bottomBarState: com.archive.app.ui.design.AppBottomBarScrollState,
    listState: LazyListState,
    scrollToTop: ScrollToTopState,
    state: FavoritesViewModel.ListState<FavoriteCreator>,
    baseUrl: String,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (FavoriteCreator) -> Unit,
    onRemove: (FavoriteCreator) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        // 与 PostsTab 同一套约定：整屏占位仅限「loading 且无内容」
        state.loading && state.items.isEmpty() -> AppLoading()
        state.error != null && state.items.isEmpty() -> PullableState {
            AppErrorState(
                message = "加载失败：${state.error}",
                onRetry = onRetry,
            )
        }
        state.items.isEmpty() -> PullableState {
            AppEmptyState("还没有收藏的作者")
        }
        else -> LazyColumn(
            bottomBarState.attach(Modifier.fillMaxSize().appListModifier()),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
        ) {
            items(state.items.size, key = { state.items[it].service + state.items[it].id }) { i ->
                val creator = state.items[i]
                FavoriteCreatorCard(
                    creator = creator,
                    baseUrl = baseUrl,
                    onOpen = { onOpen(creator) },
                    onRemove = { onRemove(creator) },
                )
            }
            state.error?.let { message ->
                item {
                    AppText(
                        message,
                        style = AppTextStyle.SMALL,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * 作者收藏卡片：横幅 + 右上平台徽标 + 左下悬浮头像 + 名称 / ID / 收藏序号 / 更新时间。
 * 颜色与圆角全部取自当前主题令牌（LocalPurrColors + AppCard），不写死品牌色。
 */
@Composable
private fun FavoriteCreatorCard(
    creator: FavoriteCreator,
    baseUrl: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalPurrColors.current
    val avatarShape = RoundedCornerShape(18.dp)
    AppCard(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onOpen,
    ) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                ThumbImage(
                    url = UrlProvider.bannerUrl(baseUrl, creator.service, creator.id),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    contentDescription = null,
                    blurredOverride = false,
                    cornerRadius = 0,
                )
                // 平台徽标（右上）：文字与底色取**该平台品牌色** ——
                // Patreon 红字红底、Fanbox 蓝字蓝底；未知平台才回退主题色。
                // 之前两者都用主题蓝，Patreon 作者会顶着一个「蓝色标签」，
                // 看上去就像平台标错了（数据本身是对的，见 FavoriteCreator.service）。
                val brand = com.archive.app.ui.theme.serviceBrandColor(creator.service)
                    ?: colors.primary
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(brand.copy(alpha = 0.20f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        com.archive.app.ui.theme.servicePlatformLabel(creator.service),
                        color = brand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // 头像（左下，压住横幅底边）：CreatorAvatar 固定不模糊
                CreatorAvatar(
                    url = UrlProvider.iconUrl(baseUrl, creator.service, creator.id),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp)
                        .offset(y = 34.dp)
                        .size(76.dp)
                        .clip(avatarShape)
                        .border(3.dp, colors.surfaceContainer, avatarShape),
                    contentDescription = creator.name,
                    cornerRadius = 18,
                )
            }
            Column(
                Modifier.padding(start = 16.dp, end = 8.dp, top = 42.dp, bottom = 12.dp),
            ) {
                Text(
                    creator.name,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ID: ${creator.id}", color = colors.textSecondary, fontSize = 13.sp)
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(colors.textTertiary),
                    )
                    Text(
                        "收藏序号: ${creator.favedSeq ?: "-"}",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = LucideIcons.favoritesFill,
                            contentDescription = "取消收藏",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                creator.updated?.let { updated ->
                    Text(
                        "更新于 ${Fmt.isoDate(updated).take(10)}",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** 空 / 错误状态放进一个可滚动列表：列表为空时下拉刷新依然可用（与首页一致）。 */
@Composable
private fun PullableState(content: @Composable () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(
                Modifier
                    .fillParentMaxHeight(0.85f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
