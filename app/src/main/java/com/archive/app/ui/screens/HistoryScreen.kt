package com.archive.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.map
import com.archive.app.data.local.HistoryEntity
import com.archive.app.data.repo.LocalStore
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.components.isPostSelectionActive
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppLoadingItem
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.rememberAppScrollBehavior
import com.archive.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class HistoryItem(val entity: HistoryEntity)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val localStore: LocalStore,
) : ViewModel() {

    val items: Flow<PagingData<HistoryItem>> =
        androidx.paging.Pager(PagingConfig(pageSize = 50)) { localStore.historyPaging() }
            .flow
            .map { paging -> paging.map { HistoryItem(it) } }
            .cachedIn(viewModelScope)

    fun clear() {
        viewModelScope.launch { localStore.clearHistory() }
    }
}

@Composable
fun HistoryScreen(
    navController: NavHostController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    var confirmClear by remember { mutableStateOf(false) }
    // 多选模式：这类「清空」入口要收起来 —— 它跟着列表滚动，滚上去就不在多选条底下，
    // 留着就是实实在在的误触（清空历史不可撤销）
    val selectionScoped = isPostSelectionActive()

    AppScaffold(
        topScrollBehavior = rememberAppScrollBehavior(),
        topBar = {
            AppTopBar(
                title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_history),
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        // Dialogs must be composed inside the scaffold (the Miuix popup
        // host is provided by the scaffold).
        AppDialog(
            show = confirmClear,
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.history_clear),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.history_clear_summary),
            onDismiss = { confirmClear = false },
            confirmLabel = androidx.compose.ui.res.stringResource(com.archive.app.R.string.history_clear_confirm),
            onConfirm = {
                viewModel.clear()
                confirmClear = false
            },
            dismissLabel = androidx.compose.ui.res.stringResource(com.archive.app.R.string.action_cancel),
        )

        if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) {
            AppLoading()
            return@AppScaffold
        }
        if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
            AppEmptyState(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.history_empty),
            )
            return@AppScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().appListModifier(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 12.dp),
        ) {
            // 多选期间不渲染这行：它的「清空」是不可撤销操作，而它跟着列表滚动，
            // 一旦滚出多选条的遮挡范围就随时可能被误触
            if (!selectionScoped) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            androidx.compose.ui.res.stringResource(com.archive.app.R.string.history_local_only),
                            style = AppTextStyle.SMALL,
                            modifier = Modifier.weight(1f),
                        )
                        AppText(
                            androidx.compose.ui.res.stringResource(com.archive.app.R.string.history_clear_confirm),
                            style = AppTextStyle.SECONDARY,
                            modifier = Modifier
                                .clickable { confirmClear = true }
                                .padding(8.dp),
                        )
                    }
                }
            }
            items(
                count = items.itemCount,
                key = items.itemKey { it.entity.service + it.entity.creatorId + it.entity.postId },
            ) { index ->
                val entry = items[index]?.entity ?: return@items
                PostCard(
                    post = com.archive.app.domain.PostSummary(
                        service = entry.service,
                        creatorId = entry.creatorId,
                        postId = entry.postId,
                        title = entry.title,
                        excerpt = null,
                        published = null,
                        cover = entry.thumbPath?.let { com.archive.app.domain.Attachment(null, it) },
                        previewOnly = false,
                    ),
                    showCreator = true,
                    onClick = {
                        navController.navigate(Routes.post(entry.service, entry.creatorId, entry.postId))
                    },
                )
            }
            when (items.loadState.append) {
                is LoadState.Loading -> item { AppLoadingItem() }
                else -> Unit
            }
        }
    }
}
