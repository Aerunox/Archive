package com.archive.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.archive.app.ui.components.AppMenuController
import com.archive.app.ui.components.LocalAppMenuController
import com.archive.app.ui.components.MenuItemSpec
import com.archive.app.ui.components.RevealMenuOverlay
import androidx.hilt.navigation.compose.hiltViewModel
import com.archive.app.ui.AppViewModel
import com.archive.app.ui.screens.CreatorDetailScreen
import com.archive.app.ui.screens.AboutScreen
import com.archive.app.ui.screens.StorageScreen
import com.archive.app.ui.screens.DownloadsScreen
import com.archive.app.ui.screens.GlobalSearchScreen
import com.archive.app.ui.screens.HistoryScreen
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.screens.MainTabsScreen
import com.archive.app.ui.screens.HomeScreen
import com.archive.app.ui.screens.MediaViewerScreen
import com.archive.app.ui.screens.AccountScreen
import com.archive.app.ui.screens.NotificationsScreen
import com.archive.app.ui.screens.PostDetailScreen
import com.archive.app.ui.screens.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val MAIN = "main"
    const val ABOUT = "about"
    const val STORAGE = "storage"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val NOTIFICATIONS = "notifications"

    /** 屏蔽管理（我 → 设置 → 屏蔽管理）：集中查看 / 取消已屏蔽的作者与帖子。 */
    const val BLOCKED = "blocked"

    /** 帖子翻译设置（我 → 设置 → 帖子翻译）：AI 接口、提示词与采样参数。 */
    const val TRANSLATION = "translation"
    const val CREATOR = "creator/{service}/{creatorId}"
    const val POST = "post/{service}/{creatorId}/{postId}"
    const val VIEWER = "viewer/{service}/{creatorId}/{postId}?index={index}"

    fun creator(service: String, creatorId: String) = "creator/$service/$creatorId"
    fun post(service: String, creatorId: String, postId: String) = "post/$service/$creatorId/$postId"
    fun viewer(service: String, creatorId: String, postId: String, index: Int) =
        "viewer/$service/$creatorId/$postId?index=$index"
}




@Composable
fun AppNavHost(navController: NavHostController) {
    val appViewModel: AppViewModel = hiltViewModel()
    val menuController = remember { AppMenuController() }
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(LocalAppMenuController provides menuController) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
                // 进入二级页面：新页从右滑入，旧页向左退让（视差），形成明确的层级推进感
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(240))
                },
                exitTransition = {
                    slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
                        fadeOut(tween(240))
                },
                // 普通返回（点返回箭头 / 非手势返回）：当前页向右滑出，上一页从左滑回
                popEnterTransition = {
                    slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { -it / 3 } +
                        fadeIn(tween(240))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(240))
                },
                // 预测式返回（系统返回手势，需 android:enableOnBackInvokedCallback="true"）：
                // 这是一套独立于 pop* 的转场，只在这套里跟手生效。默认实现只是
                // “上一页淡入 + 当前页缩到 0.7”，观感很弱，所以这里改为标准跟手滑动——
                // 当前页随手指右滑出、上一页从左滑入，手势中途松手会沿原路回退。
                predictivePopEnterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(300, easing = LinearEasing),
                    ) { -it / 3 } + fadeIn(tween(300, easing = LinearEasing))
                },
                predictivePopExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(300, easing = LinearEasing),
                    ) { it } + fadeOut(tween(300, easing = LinearEasing))
                },
            ) {
        composable(Routes.MAIN) { MainTabsScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.ABOUT) { AboutScreen(navController) }
        composable(Routes.STORAGE) { StorageScreen(navController) }
        composable(Routes.SEARCH) { GlobalSearchScreen(navController) }
        composable(Routes.HISTORY) { HistoryScreen(navController) }
        composable(Routes.DOWNLOADS) { DownloadsScreen(navController) }
        composable(Routes.LOGIN) { AccountScreen(navController) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController) }
        // 屏蔽管理：集中查看/取消已屏蔽的作者与帖子（入口在「我 → 设置」）
        composable(Routes.BLOCKED) { com.archive.app.ui.screens.BlockedContentScreen(navController) }
        // 帖子翻译设置（入口在「我 → 设置」）
        composable(Routes.TRANSLATION) {
            com.archive.app.ui.screens.TranslationSettingsScreen(navController)
        }
        composable(
            Routes.CREATOR,
            arguments = listOf(
                navArgument("service") { type = NavType.StringType },
                navArgument("creatorId") { type = NavType.StringType },
            ),
        ) { entry ->
            CreatorDetailScreen(
                navController = navController,
                service = entry.arguments?.getString("service").orEmpty(),
                creatorId = entry.arguments?.getString("creatorId").orEmpty(),
            )
        }

        composable(
            Routes.POST,
            arguments = listOf(
                navArgument("service") { type = NavType.StringType },
                navArgument("creatorId") { type = NavType.StringType },
                navArgument("postId") { type = NavType.StringType },
            ),
        ) { entry ->
            PostDetailScreen(
                navController = navController,
                service = entry.arguments?.getString("service").orEmpty(),
                creatorId = entry.arguments?.getString("creatorId").orEmpty(),
                postId = entry.arguments?.getString("postId").orEmpty(),
            )
        }

        composable(
            Routes.VIEWER,
            arguments = listOf(
                navArgument("service") { type = NavType.StringType },
                navArgument("creatorId") { type = NavType.StringType },
                navArgument("postId") { type = NavType.StringType },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            MediaViewerScreen(
                navController = navController,
                service = entry.arguments?.getString("service").orEmpty(),
                creatorId = entry.arguments?.getString("creatorId").orEmpty(),
                postId = entry.arguments?.getString("postId").orEmpty(),
                initialIndex = entry.arguments?.getInt("index") ?: 0,
            )
        }
            }

            // Fullscreen reveal menu, rendered above every screen and bar; it
            // expands from the trigger button and retracts the same way.
            // 全屏菜单只放底栏之外的入口（首页/发现/收藏/我 已在底部导航栏）
            RevealMenuOverlay(
                controller = menuController,
                items = listOf(
                    MenuItemSpec("全站搜索", LucideIcons.search) { navController.navigate(Routes.SEARCH) },
                    MenuItemSpec("浏览历史", LucideIcons.tasks) { navController.navigate(Routes.HISTORY) },
                    MenuItemSpec("下载管理", LucideIcons.downloads) { navController.navigate(Routes.DOWNLOADS) },
                    // AI 功能设置：与「我 → 设置 → AI 功能 → 帖子翻译」同一个页面
                    MenuItemSpec("AI 设置", LucideIcons.sparkles) { navController.navigate(Routes.TRANSLATION) },
                    MenuItemSpec("一键清理缓存", LucideIcons.delete) { clearCache(context) },
                ),
            )
        }
    }
}

/**
 * 首页菜单的「一键清理缓存」：与设置页/Hilt 的清理走同一个 CacheRepository.clearCache，
 * 只清 cacheDir 与 externalCacheDir（含 Coil 图片缓存），不碰下载文件。
 * 清理可能删不少文件，放到 IO 线程做，完成后在主线程 Toast 反馈。
 */
private fun clearCache(context: android.content.Context) {
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        val repository = dagger.hilt.android.EntryPointAccessors
            .fromApplication(appContext, com.archive.app.ui.AppEntryPoint::class.java)
            .cacheRepository()
        runCatching { repository.clearCache() }
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(appContext, "缓存已清理", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
