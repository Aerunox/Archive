package com.archive.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.archive.data.github.UpdateChecker
import com.archive.data.repository.CacheRepository
import com.archive.core.store.SettingsManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.app.BuildConfig
import com.archive.app.data.repo.CreatorRepository
import com.archive.app.data.repo.LocalStore
import com.archive.app.ui.AppViewModel
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextField
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.AppDropdownSetting
import com.archive.app.ui.design.AppSliderSetting
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.rememberAppScrollBehavior
import com.archive.app.ui.theme.AppColorMode
import com.archive.app.ui.theme.AppMonetStyle
import com.archive.app.ui.theme.isMonet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val creatorRepository: CreatorRepository,
    private val localStore: LocalStore,
    private val postRepository: com.archive.app.data.repo.PostRepository,
    private val settingsRepository: com.archive.app.data.SettingsRepository,
    private val settingsManager: com.archive.core.store.SettingsManager,
    private val cacheRepository: com.archive.data.repository.CacheRepository,
    private val updateChecker: com.archive.data.github.UpdateChecker,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    data class PrefsSnapshot(
        val dedupeByCreator: Boolean,
        val hideBookmarkedCreators: Boolean,
        val autoSubscribeOnBookmark: Boolean,
        val newPostNotification: Boolean,
        val syncIntervalMinutes: Int,
        val autoCleanCache: Boolean,
        val autoBackup: Boolean,
        val reduceAnimations: Boolean,
        val searchHistoryLimit: Int,
        val maxConcurrentDownloads: Int,
        val wifiOnlyDownloads: Boolean,
        val downloadMaxRetry: Int,
        val filenameFormat: String,
        val downloadLocationName: String,
    )

    val prefs = MutableStateFlow(refreshPrefs())

    private fun refreshPrefs(): PrefsSnapshot = PrefsSnapshot(
        dedupeByCreator = settingsManager.isDedupeByCreatorEnabled(),
        hideBookmarkedCreators = settingsManager.isHideBookmarkedCreatorsEnabled(),
        autoSubscribeOnBookmark = settingsManager.isAutoSubscribeOnBookmarkEnabled(),
        newPostNotification = settingsManager.isNewPostNotificationEnabled(),
        syncIntervalMinutes = settingsManager.getSyncIntervalMinutes(),
        autoCleanCache = settingsManager.isAutoCleanCacheEnabled(),
        autoBackup = settingsManager.isAutoBackupEnabled(),
        reduceAnimations = settingsManager.isReduceAnimationsEnabled(),
        searchHistoryLimit = settingsManager.getSearchHistoryLimit(),
        maxConcurrentDownloads = settingsManager.getMaxConcurrentDownloads(),
        wifiOnlyDownloads = settingsManager.isWifiOnlyDownloadEnabled(),
        downloadMaxRetry = settingsManager.getDownloadMaxRetry(),
        filenameFormat = settingsManager.getFilenameFormat().key,
        downloadLocationName = settingsManager.getDownloadLocationName(),
    )

    fun refreshPrefsState() { prefs.value = refreshPrefs() }

    val creatorCount = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    init {
        recount()
    }

    fun recount() {
        viewModelScope.launch {
            creatorCount.value = runCatching { creatorRepository.cachedCount() }.getOrDefault(0)
        }
    }

    fun syncCreators() {
        viewModelScope.launch {
            busy.value = true
            message.value = null
            runCatching { creatorRepository.refresh() }
                .onSuccess {
                    message.value = "已同步 $it 位作者"
                    recount()
                }
                .onFailure { message.value = "同步失败：${it.message ?: "网络错误"}" }
            busy.value = false
        }
    }

    fun clearCreators() {
        viewModelScope.launch {
            creatorRepository.clearCache()
            recount()
            message.value = "作者缓存已清除"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            localStore.clearHistory()
            message.value = "浏览历史已清除"
        }
    }

    fun setDedupeByCreator(v: Boolean) { settingsManager.setDedupeByCreatorEnabled(v); refreshPrefsState() }
    fun setHideBookmarkedCreators(v: Boolean) { settingsManager.setHideBookmarkedCreatorsEnabled(v); refreshPrefsState() }
    fun setAutoSubscribeOnBookmark(v: Boolean) { settingsManager.setAutoSubscribeOnBookmarkEnabled(v); refreshPrefsState() }
    fun setNewPostNotification(v: Boolean) { settingsManager.setNewPostNotificationEnabled(v); refreshPrefsState() }
    fun setSyncIntervalMinutes(v: Int) { settingsManager.setSyncIntervalMinutes(v); refreshPrefsState() }
    fun setAutoCleanCache(v: Boolean) { settingsManager.setAutoCleanCacheEnabled(v); refreshPrefsState() }
    fun setAutoBackup(v: Boolean) { settingsManager.setAutoBackupEnabled(v); com.archive.work.AutoBackupWorker.schedule(appContext, v); refreshPrefsState() }
    fun setReduceAnimations(v: Boolean) { settingsManager.setReduceAnimationsEnabled(v); refreshPrefsState() }
    fun setSearchHistoryLimit(v: Int) { settingsManager.setSearchHistoryLimit(v); refreshPrefsState() }
    fun setMaxConcurrentDownloads(v: Int) { settingsManager.setMaxConcurrentDownloads(v); refreshPrefsState() }
    fun setWifiOnlyDownloads(v: Boolean) { settingsManager.setWifiOnlyDownloadEnabled(v); refreshPrefsState() }
    fun setDownloadMaxRetry(v: Int) { settingsManager.setDownloadMaxRetry(v); refreshPrefsState() }
    fun setFilenameFormat(v: String) { settingsManager.setFilenameFormat(com.archive.core.store.SettingsManager.FilenameFormat.fromKey(v)); refreshPrefsState() }
    fun setDownloadDirectory(uri: android.net.Uri?, displayName: String) { settingsManager.setDownloadTreeUri(uri, displayName); refreshPrefsState() }

    fun clearImageCache(onDone: () -> Unit) {
        viewModelScope.launch {
            cacheRepository.clearCache()
            message.value = "图片缓存已清除"
            onDone()
        }
    }

    fun checkAppUpdate(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { updateChecker.check(BuildConfig.VERSION_NAME) }
                .getOrElse { com.archive.data.github.UpdateResult.Error }
            onResult(
                when (result) {
                    is com.archive.data.github.UpdateResult.UpdateAvailable -> "发现新版本 ${result.latestVersion}，请到 GitHub 下载"
                    com.archive.data.github.UpdateResult.UpToDate -> "已是最新版本"
                    com.archive.data.github.UpdateResult.NoReleases -> "仓库还没有发布版本"
                    com.archive.data.github.UpdateResult.Error -> "检查更新失败"
                },
            )
        }
    }

}

private val COLOR_MODE_ITEMS = listOf(
    "跟随系统", "浅色", "深色",
    "动态取色（跟随系统）", "动态取色（浅色）", "动态取色（深色）",
)
private val MONET_ITEMS = listOf("TonalSpot（默认）", "Neutral", "Vibrant", "Expressive")
private val SEED_SWATCHES = listOf(
    0xFF3482FF, 0xFF7C4DFF, 0xFF00B383, 0xFFFF7043, 0xFFE53935, 0xFF26A69A,
)

@Composable
fun SettingsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by appViewModel.state.collectAsStateWithLifecycle()
    val creatorCount by viewModel.creatorCount.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val message by viewModel.message.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    var editBaseUrl by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    // 下载目录选择（SAF）：设置项与「下载中心」共用同一个存储键，搬过来只是换了入口
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadDirPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment
                ?: "已选择"
            viewModel.setDownloadDirectory(uri, name)
        }
    }

    AppScaffold(
        topScrollBehavior = rememberAppScrollBehavior(),
        topBar = {
            AppTopBar(
                title = "设置",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().appListModifier(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item { AppSmallTitle("主题") }
            item {
                AppDropdownSetting(
                    title = "配色模式",
                    items = COLOR_MODE_ITEMS,
                    selectedIndex = settings.colorMode.ordinal,
                    onSelect = { appViewModel.setColorMode(AppColorMode.entries[it]) },
                )
                if (settings.colorMode.isMonet()) {
                    AppDropdownSetting(
                        title = "动态取色风格",
                        items = MONET_ITEMS,
                        selectedIndex = settings.monetStyle.ordinal,
                        onSelect = { appViewModel.setMonetStyle(AppMonetStyle.entries[it]) },
                    )
                    SeedColorRow(settings.seedColor) { appViewModel.setSeedColor(it) }
                }
            }

            // ── 界面语言：只影响应用文案（菜单 / 按钮 / 设置项），不翻译帖子内容 ──
            item {
                AppSmallTitle(
                    androidx.compose.ui.res.stringResource(com.archive.app.R.string.language_section),
                )
            }
            item {
                com.archive.app.ui.components.LanguageSettingCard()
            }

            item { AppSmallTitle("内容与隐私") }
            item {
                AppSwitchSetting(
                    title = "模糊缩略图",
                    summary = "默认关闭（一级界面直出原图）；开启后所有媒体缩略图模糊显示以保护隐私，" +
                        "GIF 等动图同样模糊（会退化为静帧）",
                    checked = settings.blurThumbnails,
                    onCheckedChange = { appViewModel.setBlurThumbnails(it) },
                )
                AppSwitchSetting(
                    title = "禁止截屏（FLAG_SECURE）",
                    summary = "开启后应用界面不会出现在截屏与最近任务缩略图中",
                    checked = settings.flagSecure,
                    onCheckedChange = { appViewModel.setFlagSecure(it) },
                )
            }

            item { AppSmallTitle("网络") }
            item {
                AppListItem(
                    title = "Base URL",
                    summary = settings.baseUrl,
                    onClick = { editBaseUrl = true },
                )
            }

            // ── 下载：参数原先散在「下载中心」页底部，现统一收进设置 ──
            item { AppSmallTitle("下载") }
            item {
                AppListItem(
                    title = "下载目录",
                    summary = prefs.downloadLocationName,
                    onClick = { downloadDirPicker.launch(null) },
                )
                AppSliderSetting(
                    title = "最大并发数（1–5）",
                    value = prefs.maxConcurrentDownloads.toFloat(),
                    onValueChange = { viewModel.setMaxConcurrentDownloads(it.toInt().coerceIn(1, 5)) },
                    valueRange = 1f..5f,
                    steps = 3,
                    valueText = prefs.maxConcurrentDownloads.toString(),
                )
                AppSliderSetting(
                    title = "失败重试次数（1–3）",
                    value = prefs.downloadMaxRetry.toFloat(),
                    onValueChange = { viewModel.setDownloadMaxRetry(it.toInt().coerceIn(1, 3)) },
                    valueRange = 1f..3f,
                    steps = 1,
                    valueText = prefs.downloadMaxRetry.toString(),
                )
                AppSwitchSetting(
                    title = "仅 Wi-Fi 下载",
                    summary = "移动网络下暂停下载，回到 Wi-Fi 自动继续",
                    checked = prefs.wifiOnlyDownloads,
                    onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                )
                AppDropdownSetting(
                    title = "文件命名格式",
                    items = listOf("原始文件名", "时间戳", "标题_时间"),
                    selectedIndex = when (prefs.filenameFormat) {
                        "original" -> 0
                        "title_time" -> 2
                        else -> 1
                    },
                    onSelect = { idx ->
                        viewModel.setFilenameFormat(listOf("original", "timestamp", "title_time")[idx])
                    },
                )
            }

            item { AppSmallTitle("首页信息流") }
            item {
                AppSwitchSetting(
                    title = "同作者仅显示一条",
                    summary = "信息流里同一创作者的连续更新只保留最新一条",
                    checked = prefs.dedupeByCreator,
                    onCheckedChange = { viewModel.setDedupeByCreator(it) },
                )
                AppSwitchSetting(
                    title = "隐藏已收藏创作者",
                    summary = "信息流不再显示已收藏创作者的新帖",
                    checked = prefs.hideBookmarkedCreators,
                    onCheckedChange = { viewModel.setHideBookmarkedCreators(it) },
                )
            }

            item { AppSmallTitle("翻译") }
            item {
                com.archive.app.ui.design.AppListItem(
                    title = "帖子翻译",
                    summary = "自定义 AI 接口、Key、提示词与温度等参数",
                    onClick = {
                        navController.navigate(com.archive.app.ui.navigation.Routes.TRANSLATION)
                    },
                )
            }

            item { AppSmallTitle("屏蔽") }
            item {
                com.archive.app.ui.design.AppListItem(
                    title = "屏蔽管理",
                    summary = "查看或取消已屏蔽的作者与帖子",
                    onClick = {
                        navController.navigate(com.archive.app.ui.navigation.Routes.BLOCKED)
                    },
                )
            }

            item { AppSmallTitle("通用") }
            item {
                AppSliderSetting(
                    title = "搜索历史保留条数（5–50）",
                    value = prefs.searchHistoryLimit.toFloat(),
                    onValueChange = { viewModel.setSearchHistoryLimit(it.toInt().coerceIn(5, 50)) },
                    valueRange = 5f..50f,
                    valueText = prefs.searchHistoryLimit.toString(),
                )
                AppSwitchSetting(
                    title = "减少动画",
                    summary = "关闭列表与骨架屏的过渡动画，旧设备更流畅",
                    checked = prefs.reduceAnimations,
                    onCheckedChange = { viewModel.setReduceAnimations(it) },
                )
                AppText(
                    "缓存与备份在「我 → 存储空间」，下载参数在「下载中心」，通知与订阅在「消息中心」，版本与更新在「关于」。",
                    style = AppTextStyle.SMALL,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

        }

        TextFieldDialog(
            show = editBaseUrl,
            title = "Base URL",
            hint = "站点域名变更时可修改，默认 https://pawchive.pw",
            initial = settings.baseUrl,
            onDismiss = { editBaseUrl = false },
            onSave = {
                appViewModel.setBaseUrl(it.ifBlank { "https://pawchive.pw" })
                editBaseUrl = false
            },
        )
    }
}

@Composable
private fun SeedColorRow(current: Long, onSelect: (Long) -> Unit) {
    AppCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            AppText("种子色（Monet 模式下生效，跟随取色可留空）", style = AppTextStyle.SECONDARY)
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SEED_SWATCHES.forEach { colorLong ->
                    val selected = current == colorLong
                    Box(
                        Modifier
                            .size(if (selected) 36.dp else 30.dp)
                            .clip(CircleShape)
                            .background(Color(colorLong.toInt()))
                            .clickable { onSelect(colorLong) },
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable { onSelect(0L) },
                )
            }
        }
    }
}

@Composable
private fun TextFieldDialog(
    show: Boolean,
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(show) { mutableStateOf(initial) }
    AppDialog(
        show = show,
        title = title,
        summary = hint,
        onDismiss = onDismiss,
        confirmLabel = "保存",
        onConfirm = { onSave(text) },
        dismissLabel = "取消",
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            singleLine = title == "Base URL",
        )
    }
}
