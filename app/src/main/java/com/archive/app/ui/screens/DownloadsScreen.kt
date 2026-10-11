package com.archive.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.core.model.DownloadRecord
import com.archive.core.model.DownloadStatus
import com.archive.core.store.SettingsManager
import com.archive.data.repository.DownloadCenter
import com.archive.app.ui.design.AppDropdownSetting
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.AppSliderSetting
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 下载中心：任务列表（进度 / 暂停 / 继续 / 取消 / 重试 / 删除，通知栏动作与
 * 断点续传由 Pawchive 的下载引擎承担）。
 *
 * 下载参数（目录 / 并发 / 重试 / 仅 Wi-Fi / 命名格式）已统一移到「我 → 设置 → 下载」。
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadCenter: DownloadCenter,
) : ViewModel() {

    val records: StateFlow<List<DownloadRecord>> =
        downloadCenter.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { runCatching { downloadCenter.selfHealPending() } }
    }

    fun pause(id: String) = downloadCenter.pause(id)
    fun resume(id: String) = downloadCenter.requestResume(id)
    fun cancel(id: String) = downloadCenter.cancel(id)
    fun retry(id: String) = viewModelScope.launch { runCatching { downloadCenter.retry(id) } }
    fun remove(id: String) = viewModelScope.launch { downloadCenter.removeHistory(id) }
    fun clearAll() = viewModelScope.launch { downloadCenter.clearAllHistory() }
}

private fun DownloadRecord.statusLabel(): String = when (status) {
    DownloadStatus.PENDING -> "排队中"
    DownloadStatus.RUNNING -> if (progress > 0) "下载中 $progress%" else "下载中"
    DownloadStatus.PAUSED -> "已暂停（${Fmt.bytes(tempOffset)} 已下载）"
    DownloadStatus.COMPLETED -> "已完成 · ${Fmt.bytes(fileSize)}"
    DownloadStatus.FAILED -> "失败：${errorMessage ?: "未知错误"}"
    DownloadStatus.CANCELLED -> "已取消"
}

private fun DownloadRecord.isRunning() =
    status == DownloadStatus.RUNNING || status == DownloadStatus.PENDING

@Composable
fun DownloadsScreen(
    navController: NavHostController,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "下载中心",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                AppEmptyState("没有下载任务\n在帖子详情或图片查看器中点下载按钮添加")
            }
            return@AppScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText("共 ${records.size} 条", style = AppTextStyle.SMALL, modifier = Modifier.weight(1f))
                AppText(
                    "清空记录",
                    style = AppTextStyle.SECONDARY,
                    modifier = Modifier
                        .clickable { viewModel.clearAll() }
                        .padding(8.dp),
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().appListModifier(),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 12.dp),
            ) {
                items(records.size, key = { records[it].id }) { i ->
                    val record = records[i]
                    AppListItem(
                        title = record.fileName,
                        summary = record.statusLabel() +
                            (record.createdAt.takeIf { it > 0 }
                                ?.let { " · " + Fmt.isoDate(java.time.Instant.ofEpochMilli(it).toString()) }
                                ?: ""),
                        onClick = null,
                        end = {
                            when (record.status) {
                                DownloadStatus.RUNNING, DownloadStatus.PENDING ->
                                    AppIconButton(
                                        onClick = { viewModel.pause(record.id) },
                                        icon = LucideIcons.pause,
                                        contentDescription = "暂停",
                                    )
                                DownloadStatus.PAUSED ->
                                    AppIconButton(
                                        onClick = { viewModel.resume(record.id) },
                                        icon = LucideIcons.play,
                                        contentDescription = "继续",
                                    )
                                DownloadStatus.FAILED ->
                                    AppIconButton(
                                        onClick = { viewModel.retry(record.id) },
                                        icon = LucideIcons.refresh,
                                        contentDescription = "重试",
                                    )
                                else -> Unit
                            }
                            if (record.isRunning() || record.status == DownloadStatus.PAUSED) {
                                AppIconButton(
                                    onClick = { viewModel.cancel(record.id) },
                                    icon = LucideIcons.close,
                                    contentDescription = "取消",
                                )
                            } else {
                                AppIconButton(
                                    onClick = { viewModel.remove(record.id) },
                                    icon = LucideIcons.delete,
                                    contentDescription = "删除记录",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}


