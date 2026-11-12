package com.archive.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.archive.app.data.AppSettings
import com.archive.app.data.SettingsRepository
import com.archive.app.ui.navigation.AppNavHost
import com.archive.app.ui.screens.AgeGateScreen
import com.archive.app.ui.theme.AppColorMode
import com.archive.app.ui.theme.AppMonetStyle
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.ui.theme.ArchiveTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** 全局可用的本地存储（帖子卡片的收藏状态与切换）。 */
val LocalLocalStore = staticCompositionLocalOf<com.archive.app.data.repo.LocalStore?> { null }

/** 全局可用的设置管理器（播放器倍速记忆 / 续播开关等）。 */
val LocalSettingsManager =
    staticCompositionLocalOf<com.archive.core.store.SettingsManager?> { null }

/** 全局可用的作者昵称解析（帖子接口只返回作者 id）。 */
val LocalDisplayNames = staticCompositionLocalOf<com.archive.app.data.repo.DisplayNames?> { null }

/** 全局可用的收藏动作（列表卡片的收藏会同步云端）。 */
val LocalFavoriteActions =
    staticCompositionLocalOf<com.archive.app.data.repo.FavoriteActions?> { null }

/** Hilt 入口点：以非注入上下文获取单例（组件层需要）。 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AppEntryPoint {
    fun localStore(): com.archive.app.data.repo.LocalStore
    fun favoriteActions(): com.archive.app.data.repo.FavoriteActions
    fun displayNames(): com.archive.app.data.repo.DisplayNames
    fun settingsManager(): com.archive.core.store.SettingsManager

    /**
     * 屏蔽名单的同步快照。必须在启动时主动取一次：它靠构造函数开始订阅名单，
     * 而列表侧只调它的静态判断方法，不会触发 Hilt 实例化 —— 不取的话两个快照永远是空的，
     * 屏蔽了也不生效。
     */
    fun blockedContentFilter(): com.archive.app.data.BlockedContentFilter

    /** 更新检查：启动时的自动检查（是否真的检查、间隔、忽略哪个版本都由它自己判断）。 */
    fun updateChecker(): com.archive.data.github.UpdateChecker

    /** 缓存清理：首页菜单的「一键清理缓存」直接用，不必再进设置页。 */
    fun cacheRepository(): com.archive.data.repository.CacheRepository
}

@Composable
fun AppColorMode.effectiveDark(): Boolean = when (this) {
    AppColorMode.FOLLOW_SYSTEM, AppColorMode.MONET_SYSTEM -> isSystemInDarkTheme()
    AppColorMode.LIGHT, AppColorMode.MONET_LIGHT -> false
    AppColorMode.DARK, AppColorMode.MONET_DARK -> true
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /**
     * 是否已读到磁盘设置（见 [SettingsRepository.loaded]）。
     *
     * 用途：`state` 的初始值是 `AppSettings()` 默认值，其中 `ageConfirmed = false` ——
     * 若这时就据此渲染，**每次冷启动都会闪一下年龄确认页**。所以界面等这个信号为 true
     * 之后才决定「显示年龄确认」还是「直接进主界面」。
     */
    val loaded: StateFlow<Boolean> = settings.loaded
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setColorMode(value: AppColorMode) = launch { settings.setColorMode(value) }
    fun setMonetStyle(value: AppMonetStyle) = launch { settings.setMonetStyle(value) }
    fun setSeedColor(value: Long) = launch { settings.setSeedColor(value) }
    fun setBaseUrl(value: String) = launch { settings.setBaseUrl(value) }
    fun setSessionCookie(value: String) = launch { settings.setSessionCookie(value) }
    fun setBlurThumbnails(value: Boolean) = launch { settings.setBlurThumbnails(value) }
    fun setFlagSecure(value: Boolean) = launch { settings.setFlagSecure(value) }
    fun setAgeConfirmed(value: Boolean) = launch { settings.setAgeConfirmed(value) }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

@Singleton
class ActivityFinder @Inject constructor() {
    fun find(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

/**
 * Root of the app. The theme state flows from DataStore through
 * [AppViewModel]; theme switches only recompose [ArchiveTheme] — the
 * Activity is never recreated and the hoisted NavController keeps the
 * back stack across theme changes.
 */
@Composable
fun ArchiveRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val settings by appViewModel.state.collectAsStateWithLifecycle()
    val navController: NavHostController = rememberNavController()

    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(settings.flagSecure) {
        (ActivityFinder().find(context))?.window?.let { window ->
            if (settings.flagSecure) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val isDark = settings.colorMode.effectiveDark()
    SideEffect {
        val activity = ActivityFinder().find(view.context) as? ComponentActivity ?: return@SideEffect
        val style = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // 供列表卡片读取/切换本地收藏
    val localStore = remember(context.applicationContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .localStore()
    }
    val favoriteActions = remember(context.applicationContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .favoriteActions()
    }
    val settingsManager = remember(context.applicationContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .settingsManager()
    }
    val displayNames = remember(context.applicationContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .displayNames()
    }
    val updateChecker = remember(context.applicationContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .updateChecker()
    }

    // 启动时的自动更新检查结果：命中新版本才非空，由根组件用应用级浮层提示。
    // 检查与否（总开关 / 间隔 / 是否忽略过该版本）都由 UpdateChecker 判定，这里不做二次判断。
    val updatePrompt = remember {
        androidx.compose.runtime.mutableStateOf<com.archive.data.github.UpdateResult.UpdateAvailable?>(null)
    }
    LaunchedEffect(settings.ageConfirmed) {
        // 年龄确认页还没过就别弹更新提示（那一刻屏幕上不该出现第二个窗口）
        if (!settings.ageConfirmed) return@LaunchedEffect
        val result = runCatching {
            updateChecker.autoCheck(com.archive.app.BuildConfig.VERSION_NAME)
        }.getOrNull()
        if (result is com.archive.data.github.UpdateResult.UpdateAvailable) {
            updatePrompt.value = result
            // latestVersion 是 release.tagName 原样（常已带 v，如 "v1.0.3"），
            // 先去掉前缀再统一补一个 v，避免出现 "vv1.0.3"。
            val versionLabel = "v" + result.latestVersion.removePrefix("v")

            // 主路径：直接把用户送到**能点下载的网页**——该版本的 release 页
            // （downloadUrl = release.htmlUrl，页面上就是它自己的 APK 附件）。
            // 拿不到单条发布地址时退到仓库的 releases 列表页，仍能点到 apk。
            // 同一版本只自动跳一次：否则每次冷启动都把人从应用里拽出去，
            // 用户已经看过、只是暂时不想装的时候尤其难受；跳过一次之后
            // 就只留下面的 toast 与浮层，主动权交回用户。
            val target = result.downloadUrl.ifBlank { com.archive.core.api.GithubRepo.RELEASES_URL }
            val jumpToBrowser = updateChecker.claimBrowserOpen(result.latestVersion)
            if (jumpToBrowser) {
                com.archive.app.ui.components.openUrl(context, target)
            }

            // 轻量提示：浏览器是另一个窗口，用户返回应用时未必记得刚才发生了什么；
            // 另外「同版本只跳一次」之后就不会再有跳转了，这条 toast 是那时唯一的提示。
            // 用 LONG 是因为版本号+一句话在 SHORT 里容易被读漏；toast 是系统窗口，
            // 与下面那层自绘浮层不冲突。
            val hint = if (jumpToBrowser) {
                "发现新版本 " + versionLabel + "，已为你打开下载页，返回后可在此应用内继续查看"
            } else {
                "发现新版本 " + versionLabel + "，可在「关于」页或浏览器下载页查看更新"
            }
            android.widget.Toast.makeText(context, hint, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Android 13+ 需要运行时申请通知权限：下载进度通知（通知栏进度条）依赖它。
    // 不申请的话系统会静默丢弃所有通知 —— 下载照常完成，但用户既看不到进度也看不到完成提醒。
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(settings.ageConfirmed) {
        if (!settings.ageConfirmed) return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    ArchiveTheme(
        colorMode = settings.colorMode,
        monetStyle = settings.monetStyle,
        seedColor = settings.seedColor.takeIf { it != 0L },
        blurThumbnails = settings.blurThumbnails,
    ) {
        // 帖子多选：状态放在根上，所有列表页共享（PostCard 自己读取，见 PostSelection.kt）
        val selection = com.archive.app.ui.components.rememberPostSelection()
        val selectionViewModel: com.archive.app.ui.components.PostSelectionViewModel = hiltViewModel()
        val blocking = remember { androidx.compose.runtime.mutableStateOf(false) }
        val selectionScope = androidx.compose.runtime.rememberCoroutineScope()

        // 多选模式下按返回键＝退出多选，而不是退出页面
        androidx.activity.compose.BackHandler(enabled = selection.active) { selection.clear() }

        // 退出二次确认：只在导航栈根（一级标签页）接管系统返回。
        // 二级页面不接管，照常交给 NavHost 出栈，返回手势与箭头不受影响；
        // 多选态由上面那条 BackHandler 优先处理（两条 enabled 互斥）。
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val atAppRoot = currentBackStackEntry?.destination?.route ==
            com.archive.app.ui.navigation.Routes.MAIN
        val lastExitAttemptAt = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
        androidx.activity.compose.BackHandler(enabled = atAppRoot && !selection.active) {
            val now = System.currentTimeMillis()
            val activity = ActivityFinder().find(context)
            if (lastExitAttemptAt.value != 0L && now - lastExitAttemptAt.value < 2_000) {
                activity?.finish()
            } else {
                lastExitAttemptAt.value = now
                android.widget.Toast.makeText(
                    context,
                    "再按一次退出应用",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

        // RTL 兜底：本项目靠 attachBaseContext 换 locale，字符串方向会跟着语言变，
        // 但整页 start/end 镜像是否随 View 传导到 Compose 并不总是可靠（时序微妙）。
        // 这里按当前 configuration 的方向显式提供 LocalLayoutDirection：阿拉伯语等 RTL
        // 语言时整页镜像，其余语言解析为 LTR、行为不变。
        val appLayoutDirection =
            if (androidx.compose.ui.platform.LocalConfiguration.current.layoutDirection ==
                android.view.View.LAYOUT_DIRECTION_RTL
            ) {
                androidx.compose.ui.unit.LayoutDirection.Rtl
            } else {
                androidx.compose.ui.unit.LayoutDirection.Ltr
            }

        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides appLayoutDirection,
            LocalLocalStore provides localStore,
            LocalFavoriteActions provides favoriteActions,
            LocalDisplayNames provides displayNames,
            LocalSettingsManager provides settingsManager,
            com.archive.app.ui.components.LocalPostSelection provides selection,
            // 帖子正文里的翻译齿轮：直接跳到翻译设置（根组件才有 NavController）
            com.archive.app.ui.components.LocalOpenTranslationSettings provides {
                navController.navigate(com.archive.app.ui.navigation.Routes.TRANSLATION)
            },
        ) {
        CompositionLocalProviderCompat(settings) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                // 磁盘设置读到之前不渲染任何界面：此时 settings 全是默认值，
                // 用默认值判断会闪一下年龄确认页（它只在**从未确认过**时该出现一次）。
                val loaded by appViewModel.loaded.collectAsStateWithLifecycle()
                when {
                    !loaded -> androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(LocalPurrColors.current.background),
                    )

                    !settings.ageConfirmed -> {
                        AgeGateScreen(onConfirm = { appViewModel.setAgeConfirmed(true) })
                    }

                    else -> {
                        // 界面语言由 MainActivity.attachBaseContext 套用（切语言时 recreate 生效）。
                        // 这里刻意**不**替换 Context —— 换成 ContextImpl 会让 hiltViewModel() 抛
                        // 「Expected an activity context」，导致启动即崩。
                        AppNavHost(navController)
                    }
                }
                // 多选操作条浮在最上层：已选 N 项 +「屏蔽作者」/「屏蔽帖子」+「取消」
                if (selection.active) {
                    // 两个屏蔽动作的统一收尾：跑完给 Toast 反馈并退出多选
                    fun runBlockAction(success: (Int) -> String, already: String, action: suspend () -> Int) {
                        if (blocking.value) return
                        blocking.value = true
                        selectionScope.launch {
                            val added = runCatching { action() }.getOrDefault(0)
                            blocking.value = false
                            selection.clear()
                            android.widget.Toast.makeText(
                                context,
                                if (added > 0) success(added) else already,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier
                            .align(androidx.compose.ui.Alignment.TopCenter),
                    ) {
                        com.archive.app.ui.components.PostSelectionBar(
                            count = selection.count,
                            busy = blocking.value,
                            onCancel = { selection.clear() },
                            onBlock = {
                                runBlockAction(
                                    success = { "已屏蔽 $it 位作者，相关帖子不再展示" },
                                    already = "选中的帖子所属作者已在屏蔽名单里",
                                ) { selectionViewModel.blockCreators(selection.selected.toList()) }
                            },
                            onBlockPosts = {
                                runBlockAction(
                                    success = { "已屏蔽 $it 篇帖子（该作者其他帖子照常显示）" },
                                    already = "选中的帖子已在屏蔽名单里",
                                ) { selectionViewModel.blockPosts(selection.selectedPosts.values.toList()) }
                            },
                        )
                    }
                }
                // 自动检查到新版本：浮在**所有页面之上**提示（不依赖页面级 Scaffold）
                updatePrompt.value?.let { available ->
                    com.archive.app.ui.components.UpdatePromptOverlay(
                        currentVersion = com.archive.app.BuildConfig.VERSION_NAME,
                        latestVersion = available.latestVersion,
                        publishedAt = available.publishedAt,
                        notes = available.releaseNotes,
                        baseUrl = settings.baseUrl,
                        onUpdate = {
                            com.archive.app.ui.components.openUrl(context, available.downloadUrl)
                            updatePrompt.value = null
                        },
                        onIgnore = {
                            // 之后**自动**检查不再为这个版本弹窗；手动检查仍会提示
                            updateChecker.ignoreVersion(available.latestVersion)
                            updatePrompt.value = null
                        },
                        onDismiss = { updatePrompt.value = null },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CompositionLocalProviderCompat(settings: AppSettings, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppSettings provides settings, content = content)
}
