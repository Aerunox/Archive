package com.archive.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.data.repository.SearchHistoryManager
import com.archive.app.data.local.CreatorEntity
import com.archive.app.data.repo.CreatorRepository
import com.archive.app.data.repo.PostRepository
import com.archive.app.domain.DiscordHit
import com.archive.app.domain.PostSummary
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.components.AppTabRow
import com.archive.app.ui.components.CreatorRow
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppErrorState
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.AppSearchField
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.navigation.Routes
import com.archive.app.util.Fmt
import com.archive.app.util.SearchRelevance
import com.archive.app.util.FavedSeqIndex
import com.archive.app.util.PostSortMode
import com.archive.app.util.sortedFor
import com.archive.app.data.repo.FavoriteSeqRepository
import com.archive.app.ui.components.PostSortMenu
import com.archive.app.ui.components.isPostSelectionActive
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 搜索页（行为对照 Pawchive 的 SearchViewModel / SearchFragment）：
 *
 * - **只在提交时发网络请求**（键盘回车/搜索键、点历史记录、切标签）——站点限流很严，
 *   连续请求会直接返回 429，所以不做“边打边搜”的远程搜索；输入过程中只做**本地**创作者过滤。
 * - 查询为 32/64 位十六进制（可带 `0x` 前缀）时**自动路由到文件哈希反查**，
 *   结果含站点帖子与 Discord 帖子。
 * - 创作者搜索匹配**名称或 ID**；纯数字（≥3 位）或 pixiv 主页链接里的用户 id 视为精确 pid，
 *   命中的创作者排到最前。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val creatorRepository: CreatorRepository,
    private val searchHistoryManager: SearchHistoryManager,
    private val favoriteSeqRepository: FavoriteSeqRepository,
) : ViewModel() {

    enum class Tab { POSTS, CREATORS }

    /** 创作者排序（对照 Pawchive 的 CreatorSortOption，另加相关度）。 */
    enum class CreatorSort(val label: String) {
        RELEVANCE("相关度"),
        NAME_ASC("名称 A–Z"),
        NAME_DESC("名称 Z–A"),
    }

    data class UiState(
        val query: String = "",
        val tab: Tab = Tab.POSTS,
        val postResults: List<PostSummary> = emptyList(),
        val discordHits: List<DiscordHit> = emptyList(),
        val creatorResults: List<CreatorEntity> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val hint: String? = null,
        /** 结果来自文件哈希反查（UI 上给出来源提示）。 */
        val hashMode: Boolean = false,
        /** 帖子排序：与首页/收藏/作者页共用同一套排序方式。 */
        val postSort: PostSortMode = PostSortMode.RELEVANCE,
        val creatorSort: CreatorSort = CreatorSort.RELEVANCE,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val history: MutableStateFlow<List<String>> = MutableStateFlow(searchHistoryManager.getHistory())

    private var searchJob: Job? = null

    /** 最近一次搜索的原始结果（未排序），切换排序时重新计算。 */
    private var rawPosts: List<PostSummary> = emptyList()
    private var rawCreators: List<CreatorEntity> = emptyList()

    /** 云端收藏序号索引（「按收藏时间排序」用）。 */
    private var favedIndex: FavedSeqIndex = FavedSeqIndex.EMPTY

    init {
        // 索引是异步拉取的：到手后按当前排序重排一次，用户不必再点一次排序
        viewModelScope.launch {
            favoriteSeqRepository.index.collect { index ->
                favedIndex = index
                if (rawPosts.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        postResults = sortPosts(rawPosts, _state.value.postSort, _state.value.query),
                    )
                }
            }
        }
    }

    fun setPostSort(sort: PostSortMode) {
        if (sort.needsFavedSeq) viewModelScope.launch { favoriteSeqRepository.ensureLoaded() }
        _state.value = _state.value.copy(postSort = sort, postResults = sortPosts(rawPosts, sort, _state.value.query))
    }

    fun setCreatorSort(sort: CreatorSort) {
        _state.value = _state.value.copy(
            creatorSort = sort,
            creatorResults = sortCreators(rawCreators, sort, _state.value.query),
        )
    }

    /**
     * 帖子排序：客户端排序（接口不提供排序参数），作用范围是**本次搜索返回的结果**。
     * 相关度用 [SearchRelevance]，关键词为空（哈希反查）时退化为发布时间倒序。
     */
    private fun sortPosts(list: List<PostSummary>, sort: PostSortMode, query: String): List<PostSummary> =
        list.sortedFor(sort, query, favedIndex)

    private fun sortCreators(
        list: List<CreatorEntity>,
        sort: CreatorSort,
        query: String,
    ): List<CreatorEntity> = when (sort) {
        CreatorSort.RELEVANCE -> {
            val pid = extractPixivPid(query)
            list.sortedWith(
                compareByDescending<CreatorEntity> { pid != null && it.id == pid }
                    .thenByDescending { it.favorited },
            )
        }
        CreatorSort.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        CreatorSort.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
    }

    /** 输入变化：只更新关键词并做本地创作者过滤（不发网络请求）。 */
    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text)
        if (text.isBlank()) {
            rawPosts = emptyList()
            rawCreators = emptyList()
            _state.value = _state.value.copy(
                postResults = emptyList(),
                discordHits = emptyList(),
                creatorResults = emptyList(),
                error = null,
                hint = null,
                hashMode = false,
            )
            return
        }
        // 输入时实时过滤本地创作者（照 Pawchive 的 onQueryTextChange）
        viewModelScope.launch { filterCreators(text) }
    }

    fun onTabSelected(tab: Tab) {
        _state.value = _state.value.copy(tab = tab)
        val query = _state.value.query
        if (query.isNotBlank()) submit(query)
    }

    /** 提交搜索：加历史 + 按当前标签执行（网络请求只在这里发生）。 */
    fun submit(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        searchHistoryManager.addHistory(query)
        history.value = searchHistoryManager.getHistory()
        searchJob?.cancel()
        // 换了关键词就是「新的一次搜索」：排序回到默认的「相关度」，
        // 不把上一次结果里手动挑的排序方式带过来（同一关键词重新提交则保留用户的选择）
        val isNewQuery = query != _state.value.query
        _state.value = _state.value.copy(
            query = query,
            loading = true,
            error = null,
            hint = null,
            postSort = if (isNewQuery) PostSortMode.RELEVANCE else _state.value.postSort,
            creatorSort = if (isNewQuery) CreatorSort.RELEVANCE else _state.value.creatorSort,
        )
        searchJob = viewModelScope.launch {
            when (_state.value.tab) {
                Tab.POSTS -> runPostsSearch(query)
                Tab.CREATORS -> filterCreators(query, finishLoading = true)
            }
        }
    }

    fun removeHistory(entry: String) {
        searchHistoryManager.removeHistory(entry)
        history.value = searchHistoryManager.getHistory()
    }

    fun clearHistory() {
        searchHistoryManager.clearAll()
        history.value = searchHistoryManager.getHistory()
    }

    private suspend fun runPostsSearch(query: String) {
        val hash = extractFileHash(query)
        if (hash != null) {
            runCatching { postRepository.searchByHash(hash) }
                .onSuccess { result ->
                    rawPosts = result.sitePosts
                    _state.value = _state.value.copy(
                        postResults = sortPosts(rawPosts, _state.value.postSort, query),
                        discordHits = result.discordHits,
                        loading = false,
                        hashMode = true,
                        hint = if (result.sitePosts.isEmpty() && result.discordHits.isEmpty()) {
                            "没有帖子使用该文件"
                        } else {
                            null
                        },
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = friendlyError(error),
                        hint = "哈希反查失败",
                    )
                }
            return
        }
        runCatching { postRepository.searchPosts(query) }
            .onSuccess { posts ->
                rawPosts = posts
                _state.value = _state.value.copy(
                    postResults = sortPosts(rawPosts, _state.value.postSort, query),
                    discordHits = emptyList(),
                    loading = false,
                    hashMode = false,
                    hint = if (posts.isEmpty()) "没有找到相关帖子" else null,
                )
            }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = friendlyError(error),
                    hint = "搜索失败",
                )
            }
    }

    private suspend fun filterCreators(query: String, finishLoading: Boolean = false) {
        val pid = extractPixivPid(query)
        val result = runCatching {
            if (pid != null) {
                creatorRepository.byId(pid) + creatorRepository.searchByNameOrId(query)
            } else {
                creatorRepository.searchByNameOrId(query)
            }
        }.getOrDefault(emptyList())
            .distinctBy { it.service to it.id }
            // 精确 id 命中排最前（照 Pawchive 的 exactId 处理）
            .sortedWith(
                compareByDescending<CreatorEntity> { pid != null && it.id == pid }
                    .thenByDescending { it.favorited },
            )
        rawCreators = result
        _state.value = _state.value.copy(
            creatorResults = sortCreators(rawCreators, _state.value.creatorSort, query),
            loading = if (finishLoading) false else _state.value.loading,
            error = null,
            hint = if (result.isEmpty()) "没有匹配的创作者（可在「发现」页同步作者库后再搜）" else null,
        )
    }

    /** 429（站点限流）与常见网络错误给出可读提示。 */
    private fun friendlyError(error: Throwable): String {
        val raw = error.message ?: ""
        return when {
            raw.contains("429") -> "请求过于频繁，请稍后再试（站点限流）"
            raw.contains("timeout", ignoreCase = true) -> "请求超时，请检查网络"
            raw.contains("Unable to resolve host", ignoreCase = true) -> "网络不可用"
            else -> raw.ifBlank { "搜索失败，请稍后重试" }
        }
    }

    private companion object {
        /** pixiv 主页链接中的用户 id（/users/12345、/u/12345、user.php?id=12345）。 */
        val PIXIV_URL_PID = Regex(
            """pixiv\.net/(?:users/|u/|user\.php\?id=)(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        /** 文件哈希：32（MD5）或 64（SHA-256）位十六进制。 */
        val FILE_HASH = Regex("""^[\da-fA-F]{32}$|^[\da-fA-F]{64}$""")

        fun extractFileHash(query: String): String? {
            val trimmed = query.trim().removePrefix("0x").removePrefix("0X")
            return if (FILE_HASH.matches(trimmed)) trimmed.lowercase() else null
        }

        /** 纯数字（≥3 位）或 pixiv 链接 → pid；其他返回 null（按普通关键词处理）。 */
        fun extractPixivPid(query: String): String? {
            PIXIV_URL_PID.find(query)?.let { return it.groupValues[1] }
            return if (query.trim().matches(Regex("""\d{3,}"""))) query.trim() else null
        }
    }
}

@Composable
fun GlobalSearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 多选模式：搜索框、搜索历史、结果子标签、排序都让位（退出多选自动恢复）
    val selectionScoped = isPostSelectionActive()

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "搜索",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // 多选期间整块收起（搜索框 / 搜索历史 / 结果子标签 / 排序）：
            // 多选时唯一该在场的操作在多选条里，这些不在它底下、留着就是可误触控件。
            // 退出多选（选中清空）后自动恢复，无需本页记忆状态。
            AnimatedVisibility(visible = !selectionScoped) {
                AppSearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {
                        // 键盘回车/搜索键：提交搜索并收起键盘（与 Pawchive 的 onQueryTextSubmit 一致）
                        keyboard?.hide()
                        viewModel.submit(it)
                    },
                    placeholder = "关键词 / MD5 / SHA-256",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    trailing = {
                        if (state.query.isNotEmpty()) {
                            AppIconButton(
                                onClick = { viewModel.onQueryChange("") },
                                icon = LucideIcons.close,
                                contentDescription = "清空",
                            )
                        }
                    },
                )

                // 搜索历史（提交式搜索才写入历史）
                if (state.query.isEmpty() && history.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        item {
                            AppIconButton(
                                onClick = { viewModel.clearHistory() },
                                icon = LucideIcons.delete,
                                contentDescription = "清空搜索历史",
                            )
                        }
                        items(history.size) { i ->
                            val entry = history[i]
                            AppCard(onClick = {
                                keyboard?.hide()
                                viewModel.submit(entry)
                            }) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppText(entry, style = AppTextStyle.SMALL)
                                    AppIconButton(
                                        onClick = { viewModel.removeHistory(entry) },
                                        icon = LucideIcons.close,
                                        contentDescription = "删除 $entry",
                                    )
                                }
                            }
                        }
                    }
                }

                AppTabRow(
                    tabs = listOf("帖子", "创作者"),
                    selected = state.tab.ordinal,
                    modifier = Modifier.padding(top = 4.dp),
                ) { index -> viewModel.onTabSelected(SearchViewModel.Tab.entries[index]) }

                // 排序：帖子用四个列表页共用的排序菜单（发布时间 / 收藏时间 / 相关度）
                when (state.tab) {
                    SearchViewModel.Tab.POSTS -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PostSortMenu(
                            current = state.postSort,
                            onSelect = viewModel::setPostSort,
                            note = "排序作用于本次搜索返回的结果；「收藏时间」需登录后才有云端收藏序号",
                        )
                    }
                    SearchViewModel.Tab.CREATORS -> SortChips(
                        options = SearchViewModel.CreatorSort.entries.map { it.label },
                        selected = state.creatorSort.ordinal,
                    ) { index -> viewModel.setCreatorSort(SearchViewModel.CreatorSort.entries[index]) }
                }
            }

            if (state.hashMode) {
                AppText(
                    "文件哈希反查结果",
                    style = AppTextStyle.SMALL,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            when {
                state.loading -> AppLoading()
                state.error != null -> AppErrorState(
                    message = state.error ?: "",
                    onRetry = { viewModel.submit(state.query) },
                )
                state.query.isBlank() -> AppEmptyState(
                    "输入关键词开始搜索\n· 帖子 / 创作者关键词（≥3 字符）\n· 文件哈希反查：MD5 / SHA-256",
                )
                else -> when (state.tab) {
                    SearchViewModel.Tab.POSTS -> PostsResults(state, navController)
                    SearchViewModel.Tab.CREATORS -> CreatorsResults(state, settings.baseUrl, navController)
                    }
            }
        }
    }
}

@Composable
private fun PostsResults(state: SearchViewModel.UiState, navController: NavHostController) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.postResults.isEmpty() && state.discordHits.isEmpty()) {
            item { AppEmptyState(state.hint ?: "没有找到相关帖子") }
        }
        items(
            state.postResults.size,
            key = { i ->
                val p = state.postResults[i]
                p.service + "/" + p.creatorId + "/" + p.postId + "#" + i
            },
        ) { i ->
            val post = state.postResults[i]
            PostCard(post = post) {
                navController.navigate(Routes.post(post.service, post.creatorId, post.postId))
            }
        }
        if (state.discordHits.isNotEmpty()) {
            item {
                AppText(
                    "Discord 结果（${state.discordHits.size}）",
                    style = AppTextStyle.TITLE,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.discordHits.size, key = { i -> "discord-" + state.discordHits[i].id + "#" + i }) { i ->
                val hit = state.discordHits[i]
                AppListItem(
                    title = hit.excerpt ?: hit.id,
                    summary = buildString {
                        append("discord · ").append(hit.server).append(" / ").append(hit.channel)
                        hit.published?.let { append(" · ").append(Fmt.relativeIso(it)) }
                    },
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun CreatorsResults(
    state: SearchViewModel.UiState,
    baseUrl: String,
    navController: NavHostController,
) {
    LazyColumn(
        Modifier.fillMaxSize().appListModifier(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.creatorResults.isEmpty()) {
            item { AppEmptyState(state.hint ?: "没有匹配的创作者") }
        }
        items(
            state.creatorResults.size,
            key = { i -> state.creatorResults[i].service + "/" + state.creatorResults[i].id + "#" + i },
        ) { i ->
            val creator = state.creatorResults[i]
            CreatorRow(
                name = creator.name,
                service = creator.service,
                creatorId = creator.id,
                baseUrl = baseUrl,
                favorited = creator.favorited,
                updatedText = Fmt.relative(creator.updated),
                onClick = { navController.navigate(Routes.creator(creator.service, creator.id)) },
            )
        }
    }
}

/** 结果排序选项（横向 chip，与发现页的筛选 chip 同款观感）。 */
@Composable
private fun SortChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(options.size) { i ->
            val colors = com.archive.app.ui.theme.LocalPurrColors.current
            AppCard(onClick = { onSelect(i) }) {
                AppText(
                    options[i],
                    style = AppTextStyle.SMALL,
                    color = if (i == selected) colors.primary else colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
