package com.archive.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.core.store.SettingsManager
import com.archive.data.repository.CacheRepository
import com.archive.work.AutoBackupWorker
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.AppScaffold
import com.archive.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 存储空间：应用本地数据的「统计 + 清理 + 备份」按类型集中在此页
 * （缓存 / 下载文件 / 自动策略），设置页只保留应用级偏好。
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val cacheRepository: CacheRepository,
    private val settingsManager: SettingsManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    data class State(
        val imageCache: Long = 0,
        val otherCache: Long = 0,
        val downloadFiles: Long = 0,
        val archiveCount: Int = 0,
        val archiveBytes: Long = 0,
        val lastCleanTime: Long = 0,
        val autoClean: Boolean = false,
        val autoBackup: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
    )

    val state = MutableStateFlow(State())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                State(
                    imageCache = runCatching { cacheRepository.getImageCacheSize() }.getOrDefault(0),
                    otherCache = runCatching { cacheRepository.getOtherCacheSize() }.getOrDefault(0),
                    downloadFiles = runCatching { cacheRepository.getDownloadFilesSize() }.getOrDefault(0),

                    lastCleanTime = runCatching { cacheRepository.getLastCleanTime() }.getOrDefault(0),
                    autoClean = settingsManager.isAutoCleanCacheEnabled(),
                    autoBackup = settingsManager.isAutoBackupEnabled(),
                )
            }
            state.value = state.value.copy(
                imageCache = loaded.imageCache,
                otherCache = loaded.otherCache,
                downloadFiles = loaded.downloadFiles,
                archiveCount = loaded.archiveCount,
                archiveBytes = loaded.archiveBytes,
                lastCleanTime = loaded.lastCleanTime,
                autoClean = loaded.autoClean,
                autoBackup = loaded.autoBackup,
            )
        }
    }

    fun clearImageCache() = runBusy("图片缓存已清除") { cacheRepository.clearImageCache() }

    fun clearOtherCache() = runBusy("其他缓存已清除") { cacheRepository.clearOtherCache() }

    fun deleteDownloadFiles() = runBusy("下载文件已删除") { cacheRepository.deleteDownloadFiles() }

    fun setAutoClean(enabled: Boolean) {
        settingsManager.setAutoCleanCacheEnabled(enabled)
        state.value = state.value.copy(autoClean = enabled)
    }

    fun setAutoBackup(enabled: Boolean) {
        settingsManager.setAutoBackupEnabled(enabled)
        AutoBackupWorker.schedule(appContext, enabled)
        state.value = state.value.copy(autoBackup = enabled, message = if (enabled) "已开启每日自动备份" else "已关闭自动备份")
    }

    private fun runBusy(done: String, block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, message = null)
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { state.value = state.value.copy(message = done) }
                .onFailure { state.value = state.value.copy(message = it.message ?: "操作失败") }
            state.value = state.value.copy(busy = false)
            refresh()
        }
    }
}

@Composable
fun StorageScreen(
    navController: NavHostController,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    var confirmClearArchive by remember { mutableStateOf(false) }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "存储空间",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { AppSmallTitle("缓存") }
            item {
                AppListItem(
                    title = "图片缓存",
                    summary = Fmt.bytes(state.imageCache) + " · 缩略图与原图缓存",
                    onClick = { viewModel.clearImageCache() },
                )
                AppListItem(
                    title = "其他缓存",
                    summary = Fmt.bytes(state.otherCache) + " · 接口响应与临时文件",
                    onClick = { viewModel.clearOtherCache() },
                )
                AppSwitchSetting(
                    title = "自动清理缓存",
                    summary = "超过容量阈值时后台自动清理" +
                        if (state.lastCleanTime > 0) {
                            " · 上次 " + Fmt.isoDate(java.time.Instant.ofEpochMilli(state.lastCleanTime).toString())
                        } else {
                            ""
                        },
                    checked = state.autoClean,
                    onCheckedChange = { viewModel.setAutoClean(it) },
                )
            }

            item { AppSmallTitle("下载文件") }
            item {
                AppListItem(
                    title = "已占用空间",
                    summary = Fmt.bytes(state.downloadFiles) + " · 下载目录中由本应用保存的文件",
                    onClick = { confirmDeleteDownloads = true },
                )
                AppText(
                    "下载目录与并发等下载参数在「我 → 设置 → 下载」中调整。",
                    style = AppTextStyle.SMALL,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            item { AppSmallTitle("备份") }
            item {
                AppSwitchSetting(
                    title = "每日自动备份",
                    summary = "自动导出收藏 / 订阅 / 账号等数据到下载目录",
                    checked = state.autoBackup,
                    onCheckedChange = { viewModel.setAutoBackup(it) },
                )
            }

            state.message?.let { message ->
                item {
                    AppText(
                        message,
                        style = AppTextStyle.SMALL,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }

        AppDialog(
            show = confirmDeleteDownloads,
            title = "删除下载文件",
            summary = "将删除下载目录中由本应用保存的全部文件（${Fmt.bytes(state.downloadFiles)}），下载记录会保留。",
            onDismiss = { confirmDeleteDownloads = false },
            confirmLabel = "删除",
            onConfirm = {
                viewModel.deleteDownloadFiles()
                confirmDeleteDownloads = false
            },
            dismissLabel = "取消",
        )
    }
}
