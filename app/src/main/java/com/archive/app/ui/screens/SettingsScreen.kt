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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
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
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppSearchField
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
        val cacheCleanFrequency: String,
        val cacheSizeLimit: String,
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
        cacheCleanFrequency = settingsManager.getCacheCleanFrequency().key,
        cacheSizeLimit = settingsManager.getCacheSizeLimit().key,
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
                    // 0 行时本地库不会被清空（见 replaceAllInChunks 的空列表保护），文案要如实说明
                    message.value = if (it > 0) {
                        "已同步 $it 位作者"
                    } else {
                        "服务端返回 0 位作者，本地数据保持不变"
                    }
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
    fun setAutoCleanCache(v: Boolean) { settingsManager.setAutoCleanCacheEnabled(v); com.archive.work.CacheCleanWorker.schedule(appContext, v, settingsManager.getCacheCleanFrequency()); refreshPrefsState() }
    fun setCacheCleanFrequency(key: String) { val f = SettingsManager.CacheCleanFrequency.fromKey(key); settingsManager.setCacheCleanFrequency(f); com.archive.work.CacheCleanWorker.schedule(appContext, settingsManager.isAutoCleanCacheEnabled(), f); refreshPrefsState() }
    fun setCacheSizeLimit(key: String) { settingsManager.setCacheSizeLimit(com.archive.core.store.SettingsManager.CacheSizeLimit.fromKey(key)); refreshPrefsState() }
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
                .getOrElse { com.archive.data.github.UpdateResult.Error(detail = it.javaClass.simpleName) }
            onResult(
                when (result) {
                    is com.archive.data.github.UpdateResult.UpdateAvailable -> "发现新版本 ${result.latestVersion}，请到 GitHub 下载"
                    com.archive.data.github.UpdateResult.UpToDate -> "已是最新版本"
                    com.archive.data.github.UpdateResult.NoReleases -> "仓库还没有发布版本"
                    is com.archive.data.github.UpdateResult.Error ->
                        if (result.rateLimited) "GitHub 限流了，稍后再试" else "检查更新失败"
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
// 与 SettingsManager.CacheCleanFrequency 的 key 一一对应（顺序 = every_launch/daily/weekly/monthly）
private val CACHE_CLEAN_FREQUENCY_ITEMS = listOf("每次开启", "每天", "每周", "每月")
private val SEED_SWATCHES = listOf(
    0xFF3482FF, 0xFF7C4DFF, 0xFF00B383, 0xFFFF7043, 0xFFE53935, 0xFF26A69A,
)

/**
 * 设置页的分类入口（对照参考图：首页不平铺长列表，而是「图标 + 标题 + 一行摘要 + ›」）。
 *
 * 分类详情用**同一屏内的状态**切换（[openSection]），不新增 NavHost 路由：
 * 这样既保住现有的返回手势 / 预测式返回动画与「我 → 设置」的入口语义，也让
 * SettingsViewModel 与各设置项的接线一行都不用改。
 */
private enum class SettingsCategory(
    val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    APPEARANCE(com.archive.app.R.string.settings_cat_appearance, com.archive.app.ui.design.lucide.LucideIcons.palette),
    PRIVACY(com.archive.app.R.string.settings_cat_privacy, com.archive.app.ui.design.lucide.LucideIcons.shield),
    NETWORK(com.archive.app.R.string.settings_cat_network, com.archive.app.ui.design.lucide.LucideIcons.globe),
    BROWSE(com.archive.app.R.string.settings_cat_browse, com.archive.app.ui.design.lucide.LucideIcons.search),
    DOWNLOAD(com.archive.app.R.string.settings_cat_download, com.archive.app.ui.design.lucide.LucideIcons.download),
    STORAGE(com.archive.app.R.string.settings_cat_storage, com.archive.app.ui.design.lucide.LucideIcons.hardDrive),
    AI(com.archive.app.R.string.settings_cat_ai, com.archive.app.ui.design.lucide.LucideIcons.sparkles),
    BLOCKED(com.archive.app.R.string.settings_cat_blocked, com.archive.app.ui.design.lucide.LucideIcons.ban),
}

/** 一条设置项：标题同时用于「渲染」与「搜索命中」，摘要由所属分类的标题列表拼出来。 */
private class SettingItem(
    val title: String,
    val content: @Composable () -> Unit,
)

private data class SettingsGroup(
    val category: SettingsCategory,
    val items: List<SettingItem>,
)

@Composable
private fun buildSettingsGroups(
    settings: com.archive.app.data.AppSettings,
    prefs: SettingsViewModel.PrefsSnapshot,
    appViewModel: AppViewModel,
    viewModel: SettingsViewModel,
    aiViewModel: TranslationSettingsViewModel,
    onEditBaseUrl: () -> Unit,
    onPickDownloadDir: () -> Unit,
    openRoute: (String) -> Unit,
): List<SettingsGroup> {
    val groups = SettingsCategory.entries.map { SettingsGroup(it, emptyList()) }.toMutableList()

    fun add(category: SettingsCategory, item: SettingItem) {
        val index = groups.indexOfFirst { it.category == category }
        groups[index] = groups[index].copy(items = groups[index].items + item)
    }

    // ── 外观 ──
    add(
        SettingsCategory.APPEARANCE,
        SettingItem("配色模式") {
            AppDropdownSetting(
                title = "配色模式",
                items = COLOR_MODE_ITEMS,
                selectedIndex = settings.colorMode.ordinal,
                onSelect = { appViewModel.setColorMode(AppColorMode.entries[it]) },
            )
        },
    )
    if (settings.colorMode.isMonet()) {
        add(
            SettingsCategory.APPEARANCE,
            SettingItem("动态取色风格") {
                AppDropdownSetting(
                    title = "动态取色风格",
                    items = MONET_ITEMS,
                    selectedIndex = settings.monetStyle.ordinal,
                    onSelect = { appViewModel.setMonetStyle(AppMonetStyle.entries[it]) },
                )
            },
        )
        add(
            SettingsCategory.APPEARANCE,
            SettingItem("种子色") { SeedColorRow(settings.seedColor) { appViewModel.setSeedColor(it) } },
        )
    }
    val languageTitle = androidx.compose.ui.res.stringResource(com.archive.app.R.string.language_title)
    add(
        SettingsCategory.APPEARANCE,
        SettingItem(languageTitle) { com.archive.app.ui.components.LanguageSettingCard() },
    )
    add(
        SettingsCategory.APPEARANCE,
        SettingItem("减少动画") {
            AppSwitchSetting(
                title = "减少动画",
                summary = "关闭列表与骨架屏的过渡动画，旧设备更流畅",
                checked = prefs.reduceAnimations,
                onCheckedChange = { viewModel.setReduceAnimations(it) },
            )
        },
    )

    // ── 隐私与安全 ──
    add(
        SettingsCategory.PRIVACY,
        SettingItem("模糊缩略图") {
            AppSwitchSetting(
                title = "模糊缩略图",
                summary = "默认关闭（一级界面直出原图）；开启后所有媒体缩略图模糊显示以保护隐私，" +
                    "GIF 等动图同样模糊（会退化为静帧）",
                checked = settings.blurThumbnails,
                onCheckedChange = { appViewModel.setBlurThumbnails(it) },
            )
        },
    )
    add(
        SettingsCategory.PRIVACY,
        SettingItem("禁止截屏") {
            AppSwitchSetting(
                title = "禁止截屏（FLAG_SECURE）",
                summary = "开启后应用界面不会出现在截屏与最近任务缩略图中",
                checked = settings.flagSecure,
                onCheckedChange = { appViewModel.setFlagSecure(it) },
            )
        },
    )

    // ── 网络 ──
    add(
        SettingsCategory.NETWORK,
        SettingItem("Base URL") {
            AppListItem(
                title = "Base URL",
                summary = settings.baseUrl,
                onClick = onEditBaseUrl,
            )
        },
    )

    // ── 浏览与搜索 ──
    add(
        SettingsCategory.BROWSE,
        SettingItem("同作者仅显示一条") {
            AppSwitchSetting(
                title = "同作者仅显示一条",
                summary = "信息流里同一创作者的连续更新只保留最新一条",
                checked = prefs.dedupeByCreator,
                onCheckedChange = { viewModel.setDedupeByCreator(it) },
            )
        },
    )
    add(
        SettingsCategory.BROWSE,
        SettingItem("隐藏已收藏创作者") {
            AppSwitchSetting(
                title = "隐藏已收藏创作者",
                summary = "信息流不再显示已收藏创作者的新帖",
                checked = prefs.hideBookmarkedCreators,
                onCheckedChange = { viewModel.setHideBookmarkedCreators(it) },
            )
        },
    )
    add(
        SettingsCategory.BROWSE,
        SettingItem("搜索历史保留条数") {
            AppSliderSetting(
                title = "搜索历史保留条数（5–50）",
                value = prefs.searchHistoryLimit.toFloat(),
                onValueChange = { viewModel.setSearchHistoryLimit(it.toInt().coerceIn(5, 50)) },
                valueRange = 5f..50f,
                valueText = prefs.searchHistoryLimit.toString(),
            )
        },
    )

    // ── 下载 ──
    add(
        SettingsCategory.DOWNLOAD,
        SettingItem("下载目录") {
            AppListItem(
                title = "下载目录",
                summary = prefs.downloadLocationName,
                onClick = onPickDownloadDir,
            )
        },
    )
    add(
        SettingsCategory.DOWNLOAD,
        SettingItem("最大并发数") {
            AppSliderSetting(
                title = "最大并发数（1–5）",
                value = prefs.maxConcurrentDownloads.toFloat(),
                onValueChange = { viewModel.setMaxConcurrentDownloads(it.toInt().coerceIn(1, 5)) },
                valueRange = 1f..5f,
                steps = 3,
                valueText = prefs.maxConcurrentDownloads.toString(),
            )
        },
    )
    add(
        SettingsCategory.DOWNLOAD,
        SettingItem("失败重试次数") {
            AppSliderSetting(
                title = "失败重试次数（1–3）",
                value = prefs.downloadMaxRetry.toFloat(),
                onValueChange = { viewModel.setDownloadMaxRetry(it.toInt().coerceIn(1, 3)) },
                valueRange = 1f..3f,
                steps = 1,
                valueText = prefs.downloadMaxRetry.toString(),
            )
        },
    )
    add(
        SettingsCategory.DOWNLOAD,
        SettingItem("仅 Wi-Fi 下载") {
            AppSwitchSetting(
                title = "仅 Wi-Fi 下载",
                summary = "移动网络下暂停下载，回到 Wi-Fi 自动继续",
                checked = prefs.wifiOnlyDownloads,
                onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
            )
        },
    )
    add(
        SettingsCategory.DOWNLOAD,
        SettingItem("文件命名格式") {
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
        },
    )

    // ── 存储与清理 ──
    add(
        SettingsCategory.STORAGE,
        SettingItem("自动清理临时缓存") {
            AppSwitchSetting(
                title = "自动清理临时缓存",
                summary = "默认关闭。开启后按所选频率在后台清理图片缓存与接口响应等临时文件，" +
                    "不会删除已下载的文件",
                checked = prefs.autoCleanCache,
                onCheckedChange = { viewModel.setAutoCleanCache(it) },
            )
        },
    )
    add(
        SettingsCategory.STORAGE,
        SettingItem("自动清理频率") {
            AppDropdownSetting(
                title = "自动清理频率",
                items = CACHE_CLEAN_FREQUENCY_ITEMS,
                selectedIndex = when (prefs.cacheCleanFrequency) {
                    "every_launch" -> 0
                    "weekly" -> 2
                    "monthly" -> 3
                    else -> 1
                },
                onSelect = { idx ->
                    viewModel.setCacheCleanFrequency(
                        listOf("every_launch", "daily", "weekly", "monthly")[idx],
                    )
                },
                summary = if (prefs.autoCleanCache) {
                    "按所选间隔后台自动清理"
                } else {
                    "需要先开启「自动清理临时缓存」"
                },
            )
        },
    )
    add(
        SettingsCategory.STORAGE,
        SettingItem("缓存容量上限") {
            val limits = com.archive.core.store.SettingsManager.CacheSizeLimit.SLIDER_ORDER
            val currentIndex = limits.indexOfFirst { it.key == prefs.cacheSizeLimit }
                .let { if (it >= 0) it else limits.lastIndex }
            AppSliderSetting(
                title = "缓存容量上限",
                summary = "缓存目录超过上限时回收临时文件（不含已下载的文件）。默认无限制。",
                value = currentIndex.toFloat(),
                onValueChange = { v ->
                    // steps = 2 把 0..3 切成 4 档，取整后落到枚举下标
                    val idx = v.toInt().coerceIn(0, limits.lastIndex)
                    if (limits[idx].key != prefs.cacheSizeLimit) {
                        viewModel.setCacheSizeLimit(limits[idx].key)
                    }
                },
                valueRange = 0f..limits.lastIndex.toFloat(),
                steps = limits.size - 2,
                valueText = when (limits.getOrNull(currentIndex)) {
                    com.archive.core.store.SettingsManager.CacheSizeLimit.MB100 -> "100 MB"
                    com.archive.core.store.SettingsManager.CacheSizeLimit.MB500 -> "500 MB"
                    com.archive.core.store.SettingsManager.CacheSizeLimit.GB1 -> "1 GB"
                    else -> "无限制"
                },
            )
        },
    )
    add(
        SettingsCategory.STORAGE,
        SettingItem("缓存与备份位置") {
            AppText(
                "缓存与备份在「我 → 存储空间」，下载参数在「下载中心」，通知与订阅在「消息中心」，版本与更新在「关于」。",
                style = AppTextStyle.SMALL,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        },
    )

    // ── AI 功能 ──
    // 帖子翻译的全部配置（概览、自动翻译、档案、用途指派、测试、推理联网、提示词、采样）
    // 直接平铺在本分类下，不再跳三级页面。
    //
    // 整块作为一个设置项：分类详情每帧都会重建分组列表，若拆成多个 item，
    // item 身份每帧都变会让列表不断重新布局、滚动位置被拉回顶部（实测滚不动）。
    val aiEditorState = rememberAiSettingsEditorState()
    add(
        SettingsCategory.AI,
        SettingItem("AI 设置") {
            AiSettingsColumn(aiViewModel, aiEditorState)
        },
    )

    // ── 内容屏蔽 ──
    add(
        SettingsCategory.BLOCKED,
        SettingItem("屏蔽管理") {
            AppListItem(
                title = "屏蔽管理",
                summary = "查看或取消已屏蔽的作者与帖子",
                onClick = { openRoute(com.archive.app.ui.navigation.Routes.BLOCKED) },
            )
        },
    )

    return groups.filter { it.items.isNotEmpty() }
}

/** 分类入口一行：图标 + 加粗标题 + 一行摘要（该类下各设置项标题）+ ›。 */
@Composable
private fun SettingsCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val colors = com.archive.app.ui.theme.LocalPurrColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            AppText(
                title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            AppText(
                summary,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        androidx.compose.material3.Icon(
            imageVector = com.archive.app.ui.design.lucide.LucideIcons.chevronForward,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }
}

@Composable
fun SettingsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel(),
    aiViewModel: TranslationSettingsViewModel = hiltViewModel(),
) {
    val settings by appViewModel.state.collectAsStateWithLifecycle()
    val creatorCount by viewModel.creatorCount.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val message by viewModel.message.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    var editBaseUrl by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    // null = 分类列表；非 null = 该分类的设置项。搜索词与它互斥（有词就平铺命中项）。
    var openSection by remember { mutableStateOf<SettingsCategory?>(null) }
    var query by remember { mutableStateOf("") }

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
        // 不接可折叠大标题：顶栏是单行的（返回箭头与标题同行）。折叠栏会让顶栏高度
        // 随滚动变化、contentPadding 跟着变，LazyColumn 因此反复重新测量，滚动位置
        // 被打回顶部（分类详情里内容较多时表现为「滚不动」）。
        topBar = {
            AppTopBar(
                title = openSection?.let { androidx.compose.ui.res.stringResource(it.titleRes) }
                    ?: androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_settings),
                onBack = {
                    // 分类详情内返回＝回到分类列表；列表内返回才真正出设置页
                    if (openSection != null) openSection = null else navController.popBackStack()
                },
            )
        },
    ) { padding ->
        // 设置项与分类的归组：摘要由各类下设置项标题拼接，这样新增一条设置项不用
        // 另写一句摘要文案，也不会出现两套翻译不一致。构建本身不耗时（列表只有二十几项），
        // 但内部要取 stringResource，所以不能在 remember 里做，直接随重组构建。
        val groups = buildSettingsGroups(
            settings = settings,
            prefs = prefs,
            appViewModel = appViewModel,
            viewModel = viewModel,
            aiViewModel = aiViewModel,
            onEditBaseUrl = { editBaseUrl = true },
            onPickDownloadDir = { downloadDirPicker.launch(null) },
            openRoute = { route -> navController.navigate(route) },
        )
        val trimmed = query.trim()
        val hits = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            groups.flatMap { group ->
                group.items.filter { it.title.contains(trimmed, ignoreCase = true) }
            }
        }
        val listPadding = PaddingValues(
            bottom = padding.calculateBottomPadding() + 24.dp,
        )

        // 详情态 / 搜索态里的返回键由这里接回上一层；两级都退出后，系统返回才交给 NavHost。
        androidx.activity.compose.BackHandler(
            enabled = openSection != null || trimmed.isNotEmpty(),
        ) {
            if (trimmed.isNotEmpty()) query = "" else openSection = null
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // 搜索框必须留在 `when` 之外：放进各分支的列表里时，输入第一个字会让
            // 分支切换、这个输入框被销毁重建，焦点与输入法连接断开，后面的字符全丢
            // （表现为「只能输入一个字，且退格也删不掉」）。
            AppSearchField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { },
                placeholder = androidx.compose.ui.res.stringResource(
                    com.archive.app.R.string.settings_search_hint,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                onCancel = { query = "" },
            )

            when {
                // ── 搜索态：跨分类平铺命中项 ──
                trimmed.isNotEmpty() -> LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .appListModifier(),
                    contentPadding = listPadding,
                ) {
                    if (hits.isEmpty()) {
                        item {
                            AppText(
                                androidx.compose.ui.res.stringResource(com.archive.app.R.string.settings_no_results),
                                style = AppTextStyle.SECONDARY,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            )
                        }
                    } else {
                        hits.forEach { hit -> item { hit.content() } }
                    }
                }

                // ── 分类列表（默认态，对照参考图的入口形态）──
                openSection == null -> LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .appListModifier(),
                    contentPadding = listPadding,
                ) {
                    groups.forEach { group ->
                        item {
                            SettingsCategoryRow(
                                icon = group.category.icon,
                                title = androidx.compose.ui.res.stringResource(group.category.titleRes),
                                summary = group.items.joinToString(" · ") { it.title },
                                onClick = { openSection = group.category },
                            )
                            AppDivider(Modifier.padding(start = 66.dp))
                        }
                    }
                    message?.let { text ->
                        item {
                            AppText(
                                text,
                                style = AppTextStyle.SMALL,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // ── 某个分类的设置项 ──
                else -> LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .appListModifier(),
                    contentPadding = listPadding,
                ) {
                    // openSection 在这一帧可能已被置空：LazyColumn 的 interval content 会延迟执行
                    // 上一帧捕获的 lambda，此时读到 null 再去 first{} 就会抛 NoSuchElementException
                    // 直接崩溃退回桌面。这里用 firstOrNull 取，取不到就不渲染设置项。
                    val section = groups.firstOrNull { it.category == openSection }
                    section?.items?.forEachIndexed { index, setting ->
                        // 给每个设置项一个稳定 key：数据流每发一次值都会重建 groups，
                        // 没有 key 时 LazyColumn 会把这些项当成新内容，滚动位置被重置回顶部。
                        item(key = "${openSection?.name}-$index") { setting.content() }
                    }
                    message?.let { text ->
                        item(key = "${openSection?.name}-message") {
                            AppText(
                                text,
                                style = AppTextStyle.SMALL,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        TextFieldDialog(
            show = editBaseUrl,
            title = "Base URL",
            hint = "站点域名变更时可修改，默认 https://pawchive.pw。仅 API 与媒体地址跟随此设置；" +
                "Cloudflare 过盾、哈希反查与订阅仍走 pawchive.pw",
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
