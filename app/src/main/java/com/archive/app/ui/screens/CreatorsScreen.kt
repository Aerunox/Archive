package com.archive.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.archive.app.data.SettingsRepository
import com.archive.app.data.local.CreatorEntity
import com.archive.app.data.repo.CreatorRepository
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.components.CreatorRow
import com.archive.app.ui.components.ScrollToTopButton
import com.archive.app.ui.components.rememberListScrollToTopState
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppErrorState
import com.archive.app.ui.design.AppLoadingItem
import com.archive.app.ui.design.AppPullToRefresh
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.LocalBottomBarScrollState
import com.archive.app.ui.design.AppSearchField
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.hideBottomBarOnScroll
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.design.rememberAppBottomBarScrollState
import com.archive.app.ui.design.rememberAppScrollBehavior
import com.archive.app.ui.navigation.Routes
import com.archive.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import com.archive.app.ui.components.CreatorSortMenu
import com.archive.app.util.CreatorSortMode

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CreatorsViewModel @Inject constructor(
    private val creatorRepository: CreatorRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val service = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    /** 排序方式（创作者列表共用一套）：本地库直接按它 ORDER BY，翻页顺序也是对的。 */
    val sortMode = MutableStateFlow(CreatorSortMode.UPDATED_DESC)
    val refreshing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    /** 输入时防抖生效；按下输入法回车/搜索键时通过 [submittedQuery] 立即生效（跳过防抖）。 */
    private val submittedQuery = MutableStateFlow("")

    private val effectiveQuery: Flow<String> =
        merge(query.debounce(200), submittedQuery).distinctUntilChanged()

    val creators: Flow<PagingData<CreatorEntity>> = combine(
        service,
        effectiveQuery,
        sortMode,
    ) { s, q, mode -> Triple(s, q, mode) }
        .flatMapLatest { (s, q, mode) ->
            androidx.paging.Pager(
                PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false),
            ) {
                creatorRepository.paging(
                    service = s,
                    query = q,
                    sortKey = mode.localSortKey,
                    ascending = mode.ascending,
                    // 相关度由 SQL 里的命中质量分档实现，翻页顺序同样是正确的
                    relevance = mode.isRelevance,
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    /** 点键盘上的回车/搜索键：立即应用当前关键词。 */
    fun submitSearch(raw: String) {
        val trimmed = raw.trim()
        if (trimmed == query.value) {
            submittedQuery.value = trimmed
        } else {
            query.value = trimmed
            submittedQuery.value = trimmed
        }
        applyDefaultSortForKeyword(trimmed)
    }

    fun onServiceChange(value: String?) { service.value = value }

    fun onQueryChange(value: String) {
        val wasBlank = query.value.isBlank()
        query.value = value
        // 从「空」开始输入 = 一次新的搜索 → 默认排序切到相关度；清空 → 回到默认的最新更新。
        // 已经有关键词时继续改写它**不动排序**：用户手动挑的排序不该被后续打字冲掉。
        when {
            value.isBlank() && sortMode.value.isRelevance -> sortMode.value = CreatorSortMode.UPDATED_DESC
            wasBlank && value.isNotBlank() -> sortMode.value = CreatorSortMode.RELEVANCE
        }
    }

    /**
     * 搜索结果的默认排序：有关键词就是「相关度」（**每提交一次搜索都回到默认**，
     * 不继承上一次浏览/搜索时手动挑的排序）；清空关键词则回到「最新更新」——
     * 那时没有关键词，相关度无从计算，标签留着只会与实际顺序不符。
     */
    private fun applyDefaultSortForKeyword(keyword: String) {
        sortMode.value = if (keyword.isBlank()) {
            if (sortMode.value.isRelevance) CreatorSortMode.UPDATED_DESC else sortMode.value
        } else {
            CreatorSortMode.RELEVANCE
        }
    }

    fun onSortChange(mode: CreatorSortMode) { sortMode.value = mode }

    private var syncing = false

    /**
     * 进入页面时调用：本地作者库为空则自动同步一次（首次使用无需手动点按钮），
     * 已有数据则直接用本地缓存——全量同步是 ~15MB JSON，不宜每次进页都拉。
     */
    fun onEnter() {
        viewModelScope.launch {
            if (creatorRepository.cachedCount() == 0) refresh()
        }
    }

    /** 同步全量作者列表（单次 ~15MB JSON）到 Room；下拉刷新也走这里。 */
    fun refresh() {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            refreshing.value = true
            runCatching { creatorRepository.refresh() }
                .onSuccess { message.value = "已同步 $it 位作者" }
                .onFailure { message.value = "同步失败：${it.message ?: "网络错误"}" }
            refreshing.value = false
            syncing = false
        }
    }

    fun baseUrlOnce(): String = settingsRepository.baseUrlOnce()
}

@Composable
fun CreatorsScreen(
    navController: NavHostController,
    viewModel: CreatorsViewModel = hiltViewModel(),
) {
    val service by viewModel.service.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    var expandedCreatorKey by remember { mutableStateOf<String?>(null) }
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val items = viewModel.creators.collectAsLazyPagingItems()
    val scrollBehavior = rememberAppScrollBehavior()

    // 每次进入自动刷新：本地作者库为空时自动同步（否则仅用本地数据，避免每次进页下载全量）
    EnterRefreshEffect { viewModel.onEnter() }
    val listState = rememberLazyListState()
    val bottomBarState = LocalBottomBarScrollState.current
    val scrollToTop = rememberListScrollToTopState(listState, onReachTop = { bottomBarState?.show() })

    AppScaffold(
        topScrollBehavior = scrollBehavior,
        topBar = { AppTopBar(title = "发现") },
    ) { padding ->
        AppPullToRefresh(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }) {
            Box(Modifier.fillMaxSize()) {
                if (items.loadState.refresh is LoadState.Error && items.itemCount == 0) {
                    AppErrorState(
                        "本地作者库为空且加载失败，请点右上角同步",
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding()),
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding()),
                    ) {
                        val keyboard = LocalSoftwareKeyboardController.current
                        AppSearchField(
                            query = query,
                            onQueryChange = viewModel::onQueryChange,
                            onSearch = {
                                viewModel.submitSearch(it)
                                keyboard?.hide()
                            },
                            placeholder = "搜索创作者（本地库）",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip("全部", service == null) { viewModel.onServiceChange(null) }
                            FilterChip("Patreon", service == "patreon") { viewModel.onServiceChange("patreon") }
                            FilterChip("Fanbox", service == "fanbox") { viewModel.onServiceChange("fanbox") }
                            // 排序：本地作者库（9 万+ 条）直接按它 ORDER BY，翻页顺序也是对的
                            CreatorSortMenu(
                                current = sortMode,
                                onSelect = viewModel::onSortChange,
                                // 没有关键词时「相关度」无从计算，不列出来（列了点了也只是退化成时间序）
                                hiddenModes = if (query.isBlank()) {
                                    setOf(CreatorSortMode.RELEVANCE)
                                } else {
                                    emptySet()
                                },
                                note = "排序作用在本地作者库，翻页顺序同步正确；" +
                                    "「相关度」按名称命中质量排序（精确 > 前缀 > 包含）",
                            )
                        }
                        message?.let {
                            AppText(
                                it,
                                style = AppTextStyle.SMALL,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            )
                        }
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .appListModifier()
                                .hideBottomBarOnScroll(),
                            state = listState,
                            contentPadding = PaddingValues(
                                bottom = padding.calculateBottomPadding() + 12.dp,
                            ),
                        ) {
                            if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                                item { AppEmptyState("本地还没有作者数据，下拉或点右上角同步") }
                            }
                            items(
                                count = items.itemCount,
                                key = items.itemKey { it.service + it.id },
                            ) { index ->
                                val creator = items[index] ?: return@items
                                val itemKey = creator.service + "/" + creator.id
                                val expanded = expandedCreatorKey == itemKey
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (expanded) 180f else 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "chevron",
                                )
                                Column(Modifier.fillMaxWidth()) {
                                    CreatorRow(
                                        name = creator.name,
                                        service = creator.service,
                                        creatorId = creator.id,
                                        baseUrl = settings.baseUrl,
                                        favorited = creator.favorited,
                                        updatedText = Fmt.relative(creator.updated),
                                        extraSuffix = if (!creator.everImported) "未导入" else null,
                                        chevronRotation = chevronRotation,
                                        onClick = {
                                            expandedCreatorKey = if (expanded) null else itemKey
                                        },
                                    )
                                    // 原地展开详情：列表下移，无页面跳转（requirement 7）
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                AppText(
                                                    "ID：" + creator.id +
                                                        (creator.publicId?.let { " · " + it } ?: ""),
                                                    style = AppTextStyle.SMALL,
                                                )
                                                AppText(
                                                    "收藏 " + creator.favorited + " · 索引 " +
                                                        Fmt.relative(creator.indexed),
                                                    style = AppTextStyle.SMALL,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                )
                                            }
                                            AppButton(
                                                "查看主页",
                                                {
                                                    navController.navigate(
                                                        Routes.creator(creator.service, creator.id),
                                                    )
                                                },
                                                primary = false,
                                            )
                                        }
                                    }
                                }
                            }
                            when (items.loadState.append) {
                                is LoadState.Loading -> item { AppLoadingItem() }
                                else -> Unit
                            }
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
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = com.archive.app.ui.theme.LocalPurrColors.current
    AppCard(
        modifier = Modifier,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AppText(
                label,
                style = AppTextStyle.SMALL,
                color = if (selected) colors.primary else colors.textSecondary,
            )
        }
    }
}
