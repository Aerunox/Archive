package com.archive.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.app.BuildConfig
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.LocalSettingsManager
import com.archive.app.ui.components.UpdateAvailableDialog
import com.archive.app.ui.components.UpdateHistoryDialog
import com.archive.app.ui.components.openUrl
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppDropdownSetting
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.AppSliderSetting
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.Fmt
import com.archive.core.store.SettingsManager
import com.archive.data.github.UpdateChecker
import com.archive.data.github.UpdateReleaseInfo
import com.archive.data.github.UpdateResult
import com.archive.data.repository.CacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 关于页：版本与**更新设置**（自动检查、检查频率、更新通道、更新记录）、许可与来源声明。
 *
 * 更新设置参照 Windows「Windows 更新」那一页的组织方式：顶部一块「当前状态 + 检查」的总览，
 * 下面一组带说明的开关 / 下拉 / 滑块，最后是「更新记录」入口。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val cacheRepository: CacheRepository,
) : ViewModel() {

    /** 「检查更新」的界面状态。 */
    sealed interface UpdateUiState {
        data object Idle : UpdateUiState
        data object Checking : UpdateUiState
        data object UpToDate : UpdateUiState
        data object NoReleases : UpdateUiState
        data object Failed : UpdateUiState

        data class Available(
            val latestVersion: String,
            val publishedAt: String,
            val notes: String,
            val url: String,
        ) : UpdateUiState
    }

    val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

    /** 上次检查时间（0 = 从未检查）。 */
    val lastCheckAt: StateFlow<Long> = updateChecker.lastCheckAt

    /** 启动时那次自动检查的结论：进本页之前就已经查过，顶部状态要能反映它。 */
    val lastAutoResult: StateFlow<UpdateResult?> = updateChecker.lastAutoResult

    val history = MutableStateFlow<List<UpdateReleaseInfo>>(emptyList())
    val historyLoading = MutableStateFlow(false)
    val historyFailed = MutableStateFlow(false)

    init {
        // 进本页时按当前频率做一次自动检查（关了自动检查 / 未到间隔时它会自己跳过）：
        // 顶部状态因此反映的是「最近一次真实检查」的结论，而不是一个恒定的初始态。
        viewModelScope.launch { updateChecker.autoCheck(BuildConfig.VERSION_NAME) }
    }

    /** 手动检查：**不受**自动检查开关与间隔限制（用户主动问了就如实回答）。 */
    fun checkUpdate() {
        if (updateState.value is UpdateUiState.Checking) return
        viewModelScope.launch {
            updateState.value = UpdateUiState.Checking
            updateState.value = updateChecker.check(BuildConfig.VERSION_NAME).toUiState()
        }
    }

    /** 更新记录：按需拉取（打开对话框时）；已拉到过就不重复请求。 */
    fun loadHistory() {
        if (historyLoading.value || history.value.isNotEmpty()) return
        viewModelScope.launch {
            historyLoading.value = true
            val releases = updateChecker.recentReleases()
            history.value = releases
            historyFailed.value = releases.isEmpty()
            historyLoading.value = false
        }
    }

    fun clearImageCache() {
        viewModelScope.launch { cacheRepository.clearCache() }
    }
}

/** 检查频率选项（顺序必须与 [SettingsManager.UpdateFrequency] 的枚举顺序一致）。 */
private val FREQUENCY_LABELS = listOf("每次启动", "每天", "每周", "自定义周期")

/** 更新通道选项（顺序必须与 [SettingsManager.UpdateChannel] 的枚举顺序一致）。 */
private val CHANNEL_LABELS = listOf("稳定版", "测试版（含预发布）")

@Composable
fun AboutScreen(
    navController: NavHostController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val colors = LocalPurrColors.current
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val settingsManager = LocalSettingsManager.current
    val manualState by viewModel.updateState.collectAsStateWithLifecycle()
    val autoResult by viewModel.lastAutoResult.collectAsStateWithLifecycle()
    // 顶部状态：手动检查优先；没手动查过就用启动时自动检查的结论 ——
    // 否则刚启动查过一轮，页面上还写着「检查更新」，等于白查。
    val updateState = if (manualState is AboutViewModel.UpdateUiState.Idle) {
        autoResult.toUiState()
    } else {
        manualState
    }
    val lastCheckAt by viewModel.lastCheckAt.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val historyLoading by viewModel.historyLoading.collectAsStateWithLifecycle()
    val historyFailed by viewModel.historyFailed.collectAsStateWithLifecycle()

    // 更新设置：本地状态用于即时反馈，改动直接落到 SettingsManager（DataStore）
    var autoCheck by remember { mutableStateOf(settingsManager?.isAutoCheckUpdateEnabled() ?: true) }
    var frequency by remember {
        mutableStateOf(settingsManager?.getUpdateFrequency() ?: SettingsManager.UpdateFrequency.DAILY)
    }
    var customDays by remember { mutableIntStateOf(settingsManager?.getUpdateCustomDays() ?: 7) }
    var channel by remember {
        mutableStateOf(settingsManager?.getUpdateChannel() ?: SettingsManager.UpdateChannel.STABLE)
    }
    var showHistory by remember { mutableStateOf(false) }
    var detailsOf by remember { mutableStateOf<AboutViewModel.UpdateUiState.Available?>(null) }

    // 拖动滑块时不要每一帧都写 DataStore：停手 400ms 再落库
    LaunchedEffect(settingsManager) {
        snapshotFlow { customDays }
            .debounce(400)
            .collect { settingsManager?.setUpdateCustomDays(it) }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "关于",
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
            // ── 总览：当前状态 + 检查按钮（对应 Windows 更新页顶部那块）──
            item {
                AppCard(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // AppCard 自己不带内边距（仓库里其余卡片都由调用方补 12dp），
                    // 漏掉它内容会顶到卡片边缘：右侧「检查更新」被卡片边界裁掉圆角，
                    // 看着就像按钮旁边多出一块白底。
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LucideIcons.refresh,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            AppText(
                                statusTitle(updateState),
                                style = AppTextStyle.MAIN,
                                fontWeight = FontWeight.SemiBold,
                            )
                            AppText(
                                statusSubtitle(lastCheckAt),
                                style = AppTextStyle.SMALL,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        AppButton(
                            text = if (updateState is AboutViewModel.UpdateUiState.Checking) "检查中…" else "检查更新",
                            onClick = { viewModel.checkUpdate() },
                            primary = false,
                            enabled = updateState !is AboutViewModel.UpdateUiState.Checking,
                        )
                    }
                    // 有新版本时给一个常驻的详情入口（弹窗之外也能翻看更新内容）
                    (updateState as? AboutViewModel.UpdateUiState.Available)?.let { available ->
                        AppText(
                            "查看更新内容 ›",
                            style = AppTextStyle.SMALL,
                            color = colors.primary,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                .clickable { detailsOf = available },
                        )
                    }
                }
            }

            // ── 更新设置 ──
            item { AppSmallTitle("更新设置") }
            item {
                AppSwitchSetting(
                    title = "自动检查更新",
                    summary = "启动时按下面的频率检查；关闭后仍可手动点「检查更新」",
                    checked = autoCheck,
                    onCheckedChange = {
                        autoCheck = it
                        settingsManager?.setAutoCheckUpdateEnabled(it)
                    },
                )
            }
            item {
                AppDropdownSetting(
                    title = "检查频率",
                    summary = "距上次检查未到间隔时不再重复检查",
                    items = FREQUENCY_LABELS,
                    selectedIndex = frequency.ordinal,
                    onSelect = { index ->
                        val picked = SettingsManager.UpdateFrequency.entries[index]
                        frequency = picked
                        settingsManager?.setUpdateFrequency(picked)
                    },
                )
            }
            if (frequency == SettingsManager.UpdateFrequency.CUSTOM) {
                item {
                    AppSliderSetting(
                        title = "自定义周期",
                        summary = "每 N 天检查一次（1–30 天）",
                        value = customDays.toFloat(),
                        onValueChange = { customDays = it.toInt() },
                        valueRange = 1f..30f,
                        steps = 28,
                        valueText = "每 $customDays 天",
                    )
                }
            }
            item {
                AppDropdownSetting(
                    title = "更新通道",
                    summary = "测试版包含预发布版本，可能不够稳定",
                    items = CHANNEL_LABELS,
                    selectedIndex = channel.ordinal,
                    onSelect = { index ->
                        val picked = SettingsManager.UpdateChannel.entries[index]
                        channel = picked
                        settingsManager?.setUpdateChannel(picked)
                    },
                )
            }
            item {
                AppListItem(
                    title = "更新记录",
                    summary = "历史版本的版本号、更新日志与发布时间",
                    end = {
                        Icon(
                            imageVector = LucideIcons.chevronForward,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        viewModel.loadHistory()
                        showHistory = true
                    },
                )
            }

            // ── 来源与许可 ──
            item { AppSmallTitle("来源与许可") }
            item {
                AppText(
                    "本应用是 Pawchive 的第三方客户端：数据与业务层复用自 Pawchive 项目，" +
                        "界面基于 Miuix（HyperOS 风格）实现；两者均为 Apache-2.0。\n\n" +
                        "内容通过站点公开 API 获取，不内置、不转存内容，不上传用户数据，不含统计 SDK；" +
                        "站点内容版权归原作者所有。其余依赖（Coil、Retrofit、OkHttp、Room、Media3 等）" +
                        "遵循各自许可。",
                    style = AppTextStyle.SMALL,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        // 弹窗必须写在 AppScaffold **内部**：Miuix 的 OverlayDialog 依赖 Scaffold 提供的
        // popup 宿主（写在它外面会静默不显示，看起来就像「点了没反应」）。
        if (showHistory) {
            UpdateHistoryDialog(
                loading = historyLoading,
                failed = historyFailed,
                releases = history,
                baseUrl = settings.baseUrl,
                onDismiss = { showHistory = false },
            )
        }

        detailsOf?.let { available ->
            UpdateAvailableDialog(
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = available.latestVersion,
                publishedAt = available.publishedAt,
                notes = available.notes,
                baseUrl = settings.baseUrl,
                onUpdate = {
                    openUrl(context, available.url)
                    detailsOf = null
                },
                onDismiss = { detailsOf = null },
            )
        }
    }
}

/** 检查结果 → 界面状态（手动检查与启动自动检查的结论共用同一条映射）。 */
private fun UpdateResult?.toUiState(): AboutViewModel.UpdateUiState = when (this) {
    null -> AboutViewModel.UpdateUiState.Idle
    is UpdateResult.UpdateAvailable -> AboutViewModel.UpdateUiState.Available(
        latestVersion = latestVersion,
        publishedAt = publishedAt,
        notes = releaseNotes,
        url = downloadUrl,
    )
    UpdateResult.UpToDate -> AboutViewModel.UpdateUiState.UpToDate
    UpdateResult.NoReleases -> AboutViewModel.UpdateUiState.NoReleases
    UpdateResult.Error -> AboutViewModel.UpdateUiState.Failed
}

/** 总览标题：一眼看出当前处于什么状态。 */
private fun statusTitle(state: AboutViewModel.UpdateUiState): String = when (state) {
    // 还没查到结果时给一句「邀请」而不是重复按钮上的字，免得看着像没反应
    AboutViewModel.UpdateUiState.Idle -> "检查一下有没有新版本"
    AboutViewModel.UpdateUiState.Checking -> "正在检查更新…"
    AboutViewModel.UpdateUiState.UpToDate -> "你使用的是最新版本"
    AboutViewModel.UpdateUiState.NoReleases -> "仓库还没有发布版本"
    AboutViewModel.UpdateUiState.Failed -> "检查更新失败"
    is AboutViewModel.UpdateUiState.Available -> "发现新版本 ${state.latestVersion}"
}

/** 总览副标题：当前版本 + 上次检查时间（Windows 那句「上次检查时间」的位置）。 */
private fun statusSubtitle(lastCheckAt: Long): String {
    val version = "Archive ${BuildConfig.VERSION_NAME}"
    val last = if (lastCheckAt <= 0L) "从未检查" else "上次检查：${Fmt.relative(lastCheckAt / 1000)}"
    return "$version · $last"
}
