package com.archive.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.archive.core.model.FanCard
import com.archive.core.model.SimilarCreator
import com.archive.data.repository.CreatorSubscriptionRepository
import com.archive.data.repository.SimilarCreatorsRepository
import com.archive.app.data.remote.UrlProvider
import com.archive.app.data.repo.PostRepository
import com.archive.app.domain.Announcement
import com.archive.app.domain.CreatorProfile
import com.archive.app.domain.CreatorTag
import com.archive.app.domain.PostSummary
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.components.AppTabRow
import com.archive.app.ui.components.CreatorAvatar
import com.archive.app.ui.components.CreatorRow
import com.archive.app.ui.components.HtmlText
import com.archive.app.ui.components.MultiFallbackImage
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.components.ScrollToTopButton
import com.archive.app.ui.components.rememberListScrollToTopState
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppErrorState
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppLoadingItem
import com.archive.app.ui.design.AppSearchField
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.LocalBottomBarScrollState
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.navigation.Routes
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.ui.theme.onServiceBrandColor
import com.archive.app.ui.theme.serviceBrandColor
import com.archive.app.util.Fmt
import com.archive.app.util.FavedSeqIndex
import com.archive.app.util.PostSortMode
import com.archive.app.util.sortedFor
import com.archive.app.data.repo.FavoriteSeqRepository
import com.archive.app.ui.components.PostSortMenu
import com.archive.app.ui.components.isPostSelectionActive
import com.archive.app.ui.components.selectionChromeHidden
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CreatorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val postRepository: PostRepository,
    private val similarCreatorsRepository: SimilarCreatorsRepository,
    private val creatorSubscriptionRepository: CreatorSubscriptionRepository,
    private val creatorRepository: com.archive.app.data.repo.CreatorRepository,
    private val favoriteSeqRepository: FavoriteSeqRepository,
    private val authRepository: com.archive.data.repository.AuthRepository,
) : ViewModel() {

    /** 相似 / 关联标签共用的行数据（与「发现」页同一布局）。 */
    data class CreatorRowUi(
        val service: String,
        val id: String,
        val name: String,
        val favorited: Int?,
        val updatedText: String?,
    )

    val similarRows = MutableStateFlow<List<CreatorRowUi>>(emptyList())
    val linkRows = MutableStateFlow<List<CreatorRowUi>>(emptyList())

    val service: String = savedStateHandle.get<String>("service") ?: ""
    val creatorId: String = savedStateHandle.get<String>("creatorId") ?: ""

    val profile = MutableStateFlow<CreatorProfile?>(null)
    val profileError = MutableStateFlow<String?>(null)
    val announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val tags = MutableStateFlow<List<CreatorTag>>(emptyList())
    val links = MutableStateFlow<List<CreatorProfile>>(emptyList())
    val fancards = MutableStateFlow<List<FanCard>>(emptyList())
    val similar = MutableStateFlow<List<SimilarCreator>>(emptyList())
    val selectedTag = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    /**
     * 关注状态 —— 即「云端收藏里有这位作者」，与收藏页「作者」标签是同一份数据。
     *
     * 作者收藏是账号数据（本地没有对应表），未登录时既没有可展示的状态也无法写入，
     * 因此 [followEnabled] 为 false、按钮置灰并提示先登录。
     */
    val isFollowing = MutableStateFlow(false)
    val followEnabled = MutableStateFlow(false)
    private var followBusy = false

    /** 该作者的更新提醒设置（订阅行里的两个开关）；未订阅时都是关闭。 */
    val updateNotify = MutableStateFlow(false)
    val updatePush = MutableStateFlow(false)

    /** 一次性提示（Toast 用）：关注结果、未登录、保存失败等。 */
    val message = MutableStateFlow<String?>(null)

    fun consumeMessage() {
        message.value = null
    }

    /** 搜索触发（关键词 + 序号）：序号保证同一关键词重复提交也会重新查询，且无中间空查询。 */
    private data class SearchTrigger(val query: String, val seq: Int)

    private var searchSeq = 0
    private val searchTrigger = MutableStateFlow(SearchTrigger("", 0))

    /**
     * 发请求只在**提交**（键盘回车/搜索键）或切换标签时发生；
     * 站点限流很严，打字过程中不发请求（照 Pawchive 的提交式搜索）。
     */
    val posts: Flow<PagingData<PostSummary>> = combine(
        searchTrigger,
        selectedTag,
    ) { trigger, tag -> trigger.query to tag }
        .flatMapLatest { (q, t) ->
            androidx.paging.Pager(
                PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false),
            ) {
                postRepository.creatorPostsSource(service, creatorId, q.ifBlank { null }, t)
            }.flow
                // Paging 负责加载与预取；这里把加载出来的条目录下来，交给排序后的列表渲染
                .map { pagingData ->
                    loadedPosts.clear()
                    loadedTick.value++
                    pagingData.map { post ->
                        loadedPosts += post
                        loadedTick.value++
                        post
                    }
                }
        }
        .cachedIn(viewModelScope)

    /** 排序方式（四个列表页共用一套）；只作用于**已加载**的帖子，翻页仍走 Paging。 */
    val sortMode = MutableStateFlow(PostSortMode.PUBLISHED_DESC)

    /** 云端收藏序号索引（「按收藏时间排序」用）。 */
    private val favedIndex: StateFlow<FavedSeqIndex> = favoriteSeqRepository.index

    /** 已加载的帖子（按加载次序追加，切标签/换关键词时清空）——排序的数据源。
     *  flatMapLatest 重建 Pager 后旧加载协程仍可能写同一列表，而 combine 在主线程排序遍历；
     *  普通 MutableList 会 CME / 两批数据混合错乱，用 COW 列表保证读写一致。 */
    private val loadedPosts = java.util.concurrent.CopyOnWriteArrayList<PostSummary>()
    private val loadedTick = MutableStateFlow(0)

    /**
     * 已加载的帖子按当前排序方式排好序（渲染用它，翻页仍由 Paging 驱动）。
     *
     * 关键词取自 [searchTrigger]（**已提交**的搜索词）：相关度排序必须有它，
     * 否则 [PostSortMode.RELEVANCE] 只会退化成发布时间倒序 —— 表现就是「选了相关度没反应」。
     */
    val sortedPosts: StateFlow<List<PostSummary>> =
        combine(loadedTick, sortMode, favedIndex, searchTrigger) { _, mode, faved, trigger ->
            loadedPosts.sortedFor(mode, query = trigger.query, favedSeq = faved)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 切换排序；需要收藏序号时按需拉一次云端收藏索引。 */
    fun onSortChange(mode: PostSortMode) {
        sortMode.value = mode
        if (mode.needsFavedSeq) viewModelScope.launch { favoriteSeqRepository.ensureLoaded() }
    }

    init {
        loadAll()
    }

    /** 加载/刷新作者资料、公告、标签、关联账号与订阅状态（从二级页面返回时会再次调用）。 */
    fun loadAll() {
        viewModelScope.launch {
            runCatching { postRepository.profile(service, creatorId) }
                .onSuccess { profile.value = it }
                .onFailure { profileError.value = it.message ?: "加载作者资料失败" }
        }
        viewModelScope.launch {
            announcements.value = runCatching {
                postRepository.announcements(service, creatorId)
            }.getOrDefault(emptyList())
        }
        viewModelScope.launch {
            tags.value = runCatching {
                postRepository.tags(service, creatorId)
            }.getOrDefault(emptyList())
        }
        viewModelScope.launch {
            val loaded = runCatching {
                postRepository.creatorLinks(service, creatorId)
            }.getOrDefault(emptyList())
            links.value = loaded
            linkRows.value = loaded
                .distinctBy { it.service to it.id }
                .map { link ->
                val local = runCatching { creatorRepository.lookup(link.service, link.id) }.getOrNull()
                CreatorRowUi(
                    service = link.service,
                    id = link.id,
                    name = link.name,
                    favorited = local?.favorited,
                    updatedText = link.updated?.let { Fmt.relativeIso(it) }
                        ?: local?.updated?.let { Fmt.relative(it) },
                )
            }
        }
        viewModelScope.launch {
            val subscription = runCatching {
                creatorSubscriptionRepository.subscriptionOf(service, creatorId)
            }.getOrNull()
            updateNotify.value = subscription?.notifyEnabled ?: false
            updatePush.value = subscription?.pushEnabled ?: false
        }
        viewModelScope.launch {
            val loggedIn = authRepository.isLoggedIn()
            followEnabled.value = loggedIn
            if (!loggedIn) {
                isFollowing.value = false
                return@launch
            }
            // 关注状态取自账号收藏列表（API 自带 5 分钟缓存，这里不强制刷新）
            runCatching { authRepository.syncFavoriteCreators() }
                .getOrNull()
                ?.onSuccess { favorites ->
                    isFollowing.value = favorites.any { it.service == service && it.id == creatorId }
                }
        }
    }

    /** 相似作者（网页版 recommended 页解析，按需加载）。 */
    fun loadSimilar() {
        if (similar.value.isNotEmpty()) return
        viewModelScope.launch {
            val loaded = runCatching {
                similarCreatorsRepository.fetchSimilarCreators(service, creatorId)
            }.getOrDefault(emptyList())
            similar.value = loaded
            similarRows.value = loaded
                .distinctBy { it.service to it.id }
                .map { item ->
                val local = runCatching { creatorRepository.lookup(item.service, item.id) }.getOrNull()
                CreatorRowUi(
                    service = item.service,
                    id = item.id,
                    name = item.name,
                    favorited = local?.favorited,
                    updatedText = local?.updated?.let { Fmt.relative(it) },
                )
            }
        }
    }

    /** 输入变化：只更新文本框；清空时回到未筛选状态（不带关键词重新查询）。 */
    fun onQueryChange(text: String) {
        query.value = text
        if (text.isBlank() && searchTrigger.value.query.isNotEmpty()) {
            searchSeq += 1
            searchTrigger.value = SearchTrigger("", searchSeq)
            // 清空关键词 = 回到「浏览该作者的帖子」：此刻没有关键词，相关度无从计算，
            // 把排序还给默认的发布时间倒序；用户自己挑过的其它排序不动
            if (sortMode.value == PostSortMode.RELEVANCE) {
                sortMode.value = PostSortMode.PUBLISHED_DESC
            }
        }
    }

    /** 点键盘回车/搜索键：立即按关键词搜索该作者的帖子。 */
    fun submitSearch(raw: String) {
        val trimmed = raw.trim()
        query.value = trimmed
        searchSeq += 1
        searchTrigger.value = SearchTrigger(trimmed, searchSeq)
        // 搜索结果的默认排序是「相关度」：**每提交一次搜索都回到默认**，
        // 不继承上一次浏览或上一次搜索时手动挑的排序方式
        sortMode.value = PostSortMode.RELEVANCE
    }

    /**
     * 关注 / 取消关注：写云端的作者收藏（与收藏页同一个接口）。
     *
     * 离线时由仓库进待补写队列，UI 保持乐观状态；在线失败原样上报，
     * 不做「假装成功」——那会让用户以为关注上了，其实没有。
     */
    fun toggleFollow() {
        if (!followEnabled.value) {
            message.value = "登录后才能关注作者"
            return
        }
        if (followBusy) return
        val target = !isFollowing.value
        viewModelScope.launch {
            followBusy = true
            val result = if (target) {
                authRepository.addCreatorToFavorites(service, creatorId)
            } else {
                authRepository.removeCreatorFromFavorites(service, creatorId)
            }
            result
                .onSuccess {
                    isFollowing.value = target
                    message.value = if (target) "已关注，长按可设置更新提醒" else "已取消关注"
                }
                .onFailure { message.value = it.message ?: if (target) "关注失败" else "取消关注失败" }
            followBusy = false
        }
    }

    /**
     * 保存更新提醒设置（长按关注按钮打开的小窗口）。
     *
     * 打开提醒 = 订阅该作者：提醒本来就是订阅机制产出的，没订阅就收不到新帖。
     * 关闭提醒只把开关落库、**不删订阅行** —— 用户只是不想被打扰，并非要取消关注，
     * 且重新打开时不会补推关闭期间的积压帖（同步只在开着提醒时才写更新行）。
     */
    fun saveReminderSettings(notifyEnabled: Boolean, pushEnabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                creatorSubscriptionRepository.setReminderSettings(
                    service = service,
                    creatorId = creatorId,
                    name = profile.value?.name,
                    notifyEnabled = notifyEnabled,
                    pushEnabled = notifyEnabled && pushEnabled,
                )
            }.onSuccess {
                updateNotify.value = notifyEnabled
                updatePush.value = notifyEnabled && pushEnabled
                message.value = if (notifyEnabled) "已开启更新提醒" else "已关闭更新提醒"
            }.onFailure { message.value = it.message ?: "保存失败" }
        }
    }
}

@Composable
fun CreatorDetailScreen(
    navController: NavHostController,
    service: String,
    creatorId: String,
    viewModel: CreatorDetailViewModel = hiltViewModel(),
) {
    val settings = LocalAppSettings.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val profileError by viewModel.profileError.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val fancards by viewModel.fancards.collectAsStateWithLifecycle()
    val similarRows by viewModel.similarRows.collectAsStateWithLifecycle()
    val linkRows by viewModel.linkRows.collectAsStateWithLifecycle()
    val isFollowing by viewModel.isFollowing.collectAsStateWithLifecycle()
    val updateNotify by viewModel.updateNotify.collectAsStateWithLifecycle()
    val updatePush by viewModel.updatePush.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val items = viewModel.posts.collectAsLazyPagingItems()
    val sortedPosts by viewModel.sortedPosts.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    var topTab by rememberSaveable { mutableIntStateOf(0) }

    // 每次进入本页（含从帖子详情返回）自动刷新资料与帖子流
    EnterRefreshEffect(active = true) {
        viewModel.loadAll()
        items.refresh()
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                // 顶部不再重复显示作者名（下方头像行已有），仅保留返回
                title = "",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        // 信息栏滚动显隐：上滑（往帖子深处滚）收起、回滚立即展开，纵向空间让给帖子流
        val infoBarScroll = rememberCreatorInfoBarScrollState()
        // 多选模式：信息栏、搜索、排序、筛选标签都让位（退出多选自动恢复）
        val selectionScoped = isPostSelectionActive()
        // 换标签等于「重新开始看」，顺手把信息栏带回来
        LaunchedEffect(topTab) { infoBarScroll.show() }
        // 长按关注按钮打开的小窗口
        var showReminder by remember { mutableStateOf(false) }
        val context = LocalContext.current
        LaunchedEffect(message) {
            message?.let {
                android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // 多选时信息栏整块隐去（保留占位）：多选条压在这一带上，
            // 关注按钮、头像这些与多选无关，留着既会被压住、也仍然点得着
            Box(Modifier.fillMaxWidth().selectionChromeHidden(selectionScoped)) {
                CreatorInfoBar(
                    state = infoBarScroll,
                    iconUrl = UrlProvider.iconUrl(settings.baseUrl, service, creatorId),
                    name = profile?.name ?: creatorId,
                    platform = profile?.service ?: service,
                    updatedText = profile?.updated?.let { Fmt.relativeIso(it) },
                    errorText = profileError,
                    following = isFollowing,
                    onToggleFollow = viewModel::toggleFollow,
                    onLongPressFollow = { showReminder = true },
                )
            }

            AppTabRow(
                tabs = listOf("帖子", "公告", "标签", "粉丝卡", "相似", "关联"),
                selected = topTab,
                modifier = Modifier.padding(top = 8.dp),
            ) { picked ->
                topTab = picked
                if (picked == 4) viewModel.loadSimilar()
            }

            AnimatedContent(
                targetState = topTab,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 24 })
                        .togetherWith(fadeOut())
                },
                modifier = Modifier
                    .weight(1f)
                    // 只观察滚动（不消费）：判定交给信息栏自己的 nestedScroll 连接
                    .nestedScroll(infoBarScroll.connection),
                label = "creatorTab",
            ) { tab ->
                when (tab) {
                    0 -> PostsPane(
                        viewModel = viewModel,
                        items = items,
                        sortedPosts = sortedPosts,
                        sortMode = sortMode,
                        query = query,
                        selectedTag = selectedTag,
                        tags = tags,
                        bottomPadding = padding.calculateBottomPadding(),
                        navController = navController,
                    )
                    1 -> AnnouncementsPane(
                        announcements = announcements,
                        baseUrl = settings.baseUrl,
                        bottomPadding = padding.calculateBottomPadding(),
                    )
                    2 -> TagsPane(
                        tags = tags,
                        selectedTag = selectedTag,
                        bottomPadding = padding.calculateBottomPadding(),
                        onSelect = { viewModel.selectedTag.value = it },
                        onBackToPosts = { topTab = 0 },
                    )
                    3 -> FancardsPane(
                        fancards = fancards,
                        baseUrl = settings.baseUrl,
                        bottomPadding = padding.calculateBottomPadding(),
                    )
                    4 -> SimilarPane(
                        rows = similarRows,
                        baseUrl = settings.baseUrl,
                        navController = navController,
                        bottomPadding = padding.calculateBottomPadding(),
                    )
                    else -> LinksPane(
                        rows = linkRows,
                        baseUrl = settings.baseUrl,
                        navController = navController,
                        bottomPadding = padding.calculateBottomPadding(),
                    )
                }
            }

            // 长按关注按钮打开的小窗口：是否接收该作者的更新提醒 + 提醒方式
            if (showReminder) {
                ReminderDialog(
                    creatorName = profile?.name ?: creatorId,
                    notify = updateNotify,
                    push = updatePush,
                    onDismiss = { showReminder = false },
                    onSave = { notify, push ->
                        viewModel.saveReminderSettings(notify, push)
                        showReminder = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PostsPane(
    viewModel: CreatorDetailViewModel,
    items: androidx.paging.compose.LazyPagingItems<PostSummary>,
    sortedPosts: List<PostSummary>,
    sortMode: PostSortMode,
    query: String,
    selectedTag: String?,
    tags: List<CreatorTag>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    navController: NavHostController,
) {
    // 回到顶部：与主页同一套组件与判定（滚过若干条目后右下角浮出，点击滚回顶部）。
    // 顺带在回到顶部时让底部导航栏重新出现 —— 与主页的 onReachTop 行为一致。
    val listState = rememberLazyListState()
    val bottomBarState = LocalBottomBarScrollState.current
    val scrollToTop = rememberListScrollToTopState(
        state = listState,
        onReachTop = { bottomBarState?.show() },
    )
    // 多选模式：本页与多选无关的控件（搜索、排序、筛选标签）一律让位
    val selectionScoped = isPostSelectionActive()
    Column(Modifier.fillMaxSize()) {
        val keyboard = LocalSoftwareKeyboardController.current
        AnimatedVisibility(visible = !selectionScoped) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSearchField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {
                        keyboard?.hide()
                        viewModel.submitSearch(it)
                    },
                    placeholder = "在此作者内搜索（≥3 字符）",
                    modifier = Modifier.weight(1f),
                )
                // 排序：只作用于已加载的帖子（站点接口没有排序参数）
                PostSortMenu(
                    current = sortMode,
                    onSelect = viewModel::onSortChange,
                    modifier = Modifier.padding(start = 8.dp),
                    note = "排序作用于已加载的帖子；「收藏时间」需登录后才有云端收藏序号",
                )
            }
        }
        if (tags.isNotEmpty() && !selectionScoped) {
            // 标签芯片底色随作者所在平台（Patreon 珊瑚橙 / Fanbox 蓝）
            val brand = serviceBrandColor(viewModel.service)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                item {
                    TagChip("全部", selectedTag == null, brand) {
                        viewModel.selectedTag.value = null
                    }
                }
                items(tags.size) { i ->
                    val tag = tags[i]
                    TagChip(tag.tag + " (" + tag.postCount + ")", selectedTag == tag.tag, brand) {
                        viewModel.selectedTag.value =
                            if (selectedTag == tag.tag) null else tag.tag
                    }
                }
            }
        }
        // 帖子流单独包一层 Box：回到顶部按钮浮在列表右下角（不占列表高度、不随内容滚动）
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .appListModifier(),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
            ) {
                if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) {
                    item { AppLoading() }
                } else if (items.loadState.refresh is LoadState.Error && items.itemCount == 0) {
                    item { AppErrorState("帖子加载失败", onRetry = { items.retry() }) }
                } else if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                    item { AppEmptyState("该作者暂无帖子") }
                }
                items(count = items.itemCount, key = items.itemKey { it.postId }) { index ->
                    // 触碰 Paging 的条目只为让它继续预取下一页；渲染顺序用排序后的列表
                    items[index]
                    val post = sortedPosts.getOrNull(index) ?: items[index] ?: return@items
                    PostCard(
                        post = post,
                        showCreator = false,
                        preferOriginal = true,
                    ) {
                        navController.navigate(Routes.post(post.service, post.creatorId, post.postId))
                    }
                }
                when (items.loadState.append) {
                    is LoadState.Loading -> item { AppLoadingItem() }
                    else -> Unit
                }
            }
            ScrollToTopButton(
                state = scrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = bottomPadding + 12.dp),
            )
        }
    }
}

@Composable
private fun AnnouncementsPane(
    announcements: List<Announcement>,
    baseUrl: String,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (announcements.isEmpty()) {
            item { AppEmptyState("暂无公告") }
        }
        items(announcements.size) { i ->
            val announcement = announcements[i]
            AppCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                // 与其余卡片一致的内边距：AppCard 不自带，漏掉内容会直接顶到卡片边缘
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    AppText(Fmt.isoDate(announcement.added), style = AppTextStyle.SMALL)
                    HtmlText(
                        html = announcement.content,
                        baseUrl = baseUrl,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagsPane(
    tags: List<CreatorTag>,
    selectedTag: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit,
    onBackToPosts: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (tags.isEmpty()) {
            item { AppEmptyState("暂无标签") }
        }
        items(tags.size) { i ->
            val tag = tags[i]
            AppListItem(
                title = tag.tag,
                summary = tag.postCount.toString() + " 篇帖子",
                onClick = {
                    onSelect(tag.tag)
                    onBackToPosts()
                },
            )
        }
    }
}

@Composable
private fun FancardsPane(
    fancards: List<FanCard>,
    baseUrl: String,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (fancards.isEmpty()) {
            item { AppEmptyState("暂无粉丝卡") }
        }
        items(fancards.size) { i ->
            val card = fancards[i]
            val h = card.hash
            val ext = if (card.ext.startsWith(".")) card.ext else "." + card.ext
            val path = if (h.length >= 4) {
                "/" + h.take(2) + "/" + h.substring(2, 4) + "/" + h + ext
            } else {
                "/$h$ext"
            }
            // 粉丝卡图源三级回退（抄自 Pawchive CreatorProfileFragment）；
            // 域名必须从 baseUrl 派生（UrlProvider），否则自定义 Base URL 时图片仍打旧域。
            val urls = listOf(
                com.archive.app.data.remote.UrlProvider.imgBase(baseUrl).trimEnd('/') + "/data$path",
                com.archive.app.data.remote.UrlProvider.fileBase(baseUrl).trimEnd('/') + "/data$path",
                com.archive.app.data.remote.UrlProvider.imgBase(baseUrl).trimEnd('/') + "/thumbnail/data$path",
            )
            MultiFallbackImage(
                urls = urls,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .aspectRatio(3f / 2f),
                contentDescription = "粉丝卡 " + card.id,
            )
        }
    }
}

@Composable
private fun SimilarPane(
    rows: List<CreatorDetailViewModel.CreatorRowUi>,
    baseUrl: String,
    navController: NavHostController,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (rows.isEmpty()) {
            item { AppEmptyState("暂无相似作者") }
        }
        items(rows.size, key = { i -> rows[i].service + "/" + rows[i].id + "#" + i }) { i ->
            val row = rows[i]
            CreatorRow(
                name = row.name,
                service = row.service,
                creatorId = row.id,
                baseUrl = baseUrl,
                favorited = row.favorited,
                updatedText = row.updatedText,
                // 行点击是跳转到该作者，行尾用「查看」文字按钮提示语义，
                // 不用展开箭头（那是发现页原地展开的样式）
                trailingLabel = "查看",
                onClick = { navController.navigate(Routes.creator(row.service, row.id)) },
            )
        }
    }
}

@Composable
private fun LinksPane(
    rows: List<CreatorDetailViewModel.CreatorRowUi>,
    baseUrl: String,
    navController: NavHostController,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
    ) {
        if (rows.isEmpty()) {
            item { AppEmptyState("暂无关联账号") }
        }
        items(rows.size, key = { i -> rows[i].service + "/" + rows[i].id + "#" + i }) { i ->
            val row = rows[i]
            CreatorRow(
                name = row.name,
                service = row.service,
                creatorId = row.id,
                baseUrl = baseUrl,
                favorited = row.favorited,
                updatedText = row.updatedText,
                onClick = { navController.navigate(Routes.creator(row.service, row.id)) },
            )
        }
    }
}

/**
 * 作者标签芯片：底色用该作者所在平台的品牌色 —— 选中是实色底 + 反差文字，
 * 未选是同色淡底 + 常规文字（淡底上仍用主题文字色，保证浅色主题下也读得清）；
 * 没有约定色的服务维持原来的中性卡片。
 */
@Composable
private fun TagChip(label: String, selected: Boolean, brand: Color?, onClick: () -> Unit) {
    val colors = LocalPurrColors.current
    if (brand == null) {
        AppCard(onClick = onClick) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                AppText(
                    label,
                    style = AppTextStyle.SMALL,
                    color = if (selected) colors.primary else colors.textSecondary,
                )
            }
        }
        return
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) brand else brand.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AppText(
            label,
            style = AppTextStyle.SMALL,
            color = if (selected) onServiceBrandColor(brand) else colors.textPrimary,
        )
    }
}

/** 信息栏收起前需要累计的滚动距离（太小会让轻微抖动就触发）。 */
private val INFO_BAR_HIDE_THRESHOLD = 16.dp

/** 惯性滑动的一次性判定速度阈值（与底部导航栏一致）。 */
private const val INFO_BAR_FLING_VELOCITY = 120f

/**
 * 作者信息栏的滚动显隐状态。
 *
 * 与底部导航栏（[com.archive.app.ui.design.AppBottomBarScrollState]）同一套判定：
 * - 只在「确实向下滚过一小段」（阈值）后收起，避免手指轻微抖动让信息栏来回抽动；
 * - 反向一旦出现立即展开，列表到顶仍在下拉也展开；
 * - 惯性滑动在 [NestedScrollConnection.onPreFling] 里按速度方向一次判定，不逐帧切换。
 *
 * 与底栏的差别：信息栏收起时**真的让出高度**（列表顺势铺满），而不是平移出屏 ——
 * 这一处正是需求要的「增加内容展示空间」。
 */
private class CreatorInfoBarScrollState(
    val visible: MutableState<Boolean>,
    val connection: NestedScrollConnection,
) {
    /** 立即展开（换标签、回到顶部时用）。 */
    fun show() {
        visible.value = true
    }
}

@Composable
private fun rememberCreatorInfoBarScrollState(): CreatorInfoBarScrollState {
    val visible = remember { mutableStateOf(true) }
    val thresholdPx = with(LocalDensity.current) { INFO_BAR_HIDE_THRESHOLD.toPx() }
    val accumulatedDown = remember { mutableFloatStateOf(0f) }

    val connection = remember(thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Fling) return Offset.Zero
                val dy = available.y
                if (dy < 0f) {
                    // 手指上滑 = 往帖子深处看 → 累计到阈值后收起信息栏
                    accumulatedDown.floatValue += -dy
                    if (visible.value && accumulatedDown.floatValue > thresholdPx) {
                        visible.value = false
                    }
                } else if (dy > 0f) {
                    // 回滚 → 立即展开
                    accumulatedDown.floatValue = 0f
                    if (!visible.value) visible.value = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 列表已到顶还有下拉余量 → 保证信息栏在场
                if (available.y > 0f) {
                    accumulatedDown.floatValue = 0f
                    if (!visible.value) visible.value = true
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                when {
                    available.y < -INFO_BAR_FLING_VELOCITY -> visible.value = false
                    available.y > INFO_BAR_FLING_VELOCITY -> visible.value = true
                }
                return Velocity.Zero
            }
        }
    }
    return remember(connection) { CreatorInfoBarScrollState(visible, connection) }
}

/**
 * 作者信息栏：头像 + 名称 + 「平台 · 最新更新」+ 关注按钮。
 *
 * 不显示横幅大图（保持信息紧凑）；名称下方那一行**只留平台名与最新更新时间** ——
 * 原来还跟着平台内的公开 id，与上面的名字重复，去掉后更清爽。
 */
@Composable
private fun CreatorInfoBar(
    state: CreatorInfoBarScrollState,
    iconUrl: String,
    name: String,
    platform: String,
    updatedText: String?,
    errorText: String?,
    following: Boolean,
    onToggleFollow: () -> Unit,
    onLongPressFollow: () -> Unit,
) {
    AnimatedVisibility(
        visible = state.visible.value,
        enter = expandVertically(tween(220), expandFrom = Alignment.Top) +
            slideInVertically(tween(220)) { -it } +
            fadeIn(tween(160)),
        exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) +
            slideOutVertically(tween(220)) { -it } +
            fadeOut(tween(160)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreatorAvatar(
                    url = iconUrl,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentDescription = "作者头像",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    AppText(
                        name,
                        style = AppTextStyle.TITLE,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        platform + (updatedText?.let { " · 更新 " + it } ?: ""),
                        style = AppTextStyle.SECONDARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FollowPill(
                    following = following,
                    onClick = onToggleFollow,
                    onLongClick = onLongPressFollow,
                )
            }
            errorText?.let {
                AppText(it, style = AppTextStyle.SMALL, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * 关注 / 已关注切换按钮（替代原来的「订阅」按钮）。
 *
 * 单击 = 关注或取消关注（写账号收藏里的作者，与收藏页同一个数据）；
 * 长按 = 打开更新提醒设置的小窗口。
 *
 * 用 [combinedClickable] 而不是在按钮上叠一层点击：同一个手势节点才能保证
 * 长按不会顺带触发单击 —— 否则「长按设提醒」会同时把关注状态也翻过来。
 *
 * 未登录时按钮**照常可点**（不做置灰）：点下去由 ViewModel 提示「登录后才能关注」，
 * 置灰会让用户对着一个毫无反应的按钮发懵。长按设提醒同样不要求登录 ——
 * 提醒是本地订阅数据，没账号也能用。
 */
@Composable
private fun FollowPill(
    following: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (following) colors.secondaryContainer else colors.primary)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AppText(
            text = if (following) "已关注" else "关注",
            style = AppTextStyle.SECONDARY,
            color = if (following) colors.onSecondaryContainer else colors.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 更新提醒小窗口（长按关注按钮打开）。
 *
 * 两个层次：
 * - 总开关「接收内容更新提醒」—— 关掉后周期同步仍推进基线，只是不再写更新行，
 *   所以重新打开不会补推关闭期间积压的帖子；
 * - 「提醒方式」—— 只想在应用内看、不想被通知栏打扰的人可以选「仅在应用内」。
 *
 * 打开总开关等于订阅该作者（提醒由订阅机制产出）；若还没订阅，保存时仓库会补一行订阅，
 * 且首次同步只建立基线，不会把历史帖一次性推出来。
 */
@Composable
private fun ReminderDialog(
    creatorName: String,
    notify: Boolean,
    push: Boolean,
    onDismiss: () -> Unit,
    onSave: (notify: Boolean, push: Boolean) -> Unit,
) {
    var notifyOn by remember { mutableStateOf(notify) }
    var pushOn by remember { mutableStateOf(push) }
    AppDialog(
        show = true,
        title = "更新提醒",
        summary = creatorName,
        onDismiss = onDismiss,
        confirmLabel = "保存",
        onConfirm = { onSave(notifyOn, pushOn) },
        dismissLabel = "取消",
    ) {
        AppSwitchSetting(
            title = "接收内容更新提醒",
            summary = "新帖会出现在「消息中心」",
            checked = notifyOn,
            onCheckedChange = {
                notifyOn = it
                // 重新开启时默认把系统推送也带上（关提醒时提醒方式会一并置为「仅应用内」）
                if (it) pushOn = true
            },
        )
        if (notifyOn) {
            AppSmallTitle("提醒方式", Modifier.padding(top = 8.dp))
            ReminderStyleRow(
                label = "仅在应用内",
                selected = !pushOn,
                onClick = { pushOn = false },
            )
            ReminderStyleRow(
                label = "应用内 + 系统通知",
                selected = pushOn,
                onClick = { pushOn = true },
            )
        }
    }
}

/** 提醒方式的一行（右侧打勾即当前选中）。 */
@Composable
private fun ReminderStyleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(label, style = AppTextStyle.SECONDARY, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = LucideIcons.check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
