package com.archive.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import com.archive.app.ui.design.AppBottomBar
import com.archive.app.ui.design.AppBottomBarHost
import com.archive.app.ui.design.AppBottomBarItem
import com.archive.app.ui.design.ProvideBottomBarScrollState
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.design.rememberAppBottomBarScrollState
import kotlinx.coroutines.launch

/**
 * 一级页面容器：四个标签页放在 [HorizontalPager] 里（等价于 Pawchive 的 ViewPager2），
 * 点底栏标签用 `animateScrollToPage` 做水平滑动动画，也可以左右跟手滑动切换。
 *
 * 底栏固定在 pager 外层（不随页面滑动），页面内容区止于底栏上方——与 Pawchive 的
 * “底栏可见时内容区止于底栏上方”一致。
 */
object MainTabs {
    const val HOME = 0
    const val DISCOVER = 1
    const val FAVORITES = 2
    const val MINE = 3

    fun icons(): List<ImageVector> = listOf(
        LucideIcons.home,
        LucideIcons.discover,
        LucideIcons.favorites,
        LucideIcons.mine,
    )
}

/**
 * 底部标签文案：`@Composable` 是因为 `stringResource` 必须在可组合上下文里解析。
 * 调用方在组合函数体里取一次（`val labels = mainTabLabels()`），
 * 之后的 `pageCount` / `key` 等非可组合 lambda 直接用这份纯数据。
 *
 * 走资源字符串 → 跟随「设置 → 界面语言」；帖子内容不经过这里。
 */
@androidx.compose.runtime.Composable
fun mainTabLabels(): List<String> = listOf(
    androidx.compose.ui.res.stringResource(com.archive.app.R.string.nav_home),
    androidx.compose.ui.res.stringResource(com.archive.app.R.string.nav_discover),
    androidx.compose.ui.res.stringResource(com.archive.app.R.string.nav_favorites),
    androidx.compose.ui.res.stringResource(com.archive.app.R.string.nav_mine),
)

/** 供一级页面内部请求切到另一个标签（底栏以外的页内跳转）。 */
val LocalMainTabSwitcher = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** 当前一级标签页是否处于前台（用于「每次进入自动刷新」）。 */
val LocalMainTabActive = staticCompositionLocalOf { true }

@Composable
fun MainTabsScreen(navController: NavHostController) {
    val labels = mainTabLabels()
    val pagerState = rememberPagerState(pageCount = { labels.size })
    val scope = rememberCoroutineScope()
    val bottomBarState = rememberAppBottomBarScrollState()
    val icons = remember { MainTabs.icons() }

    val switchTo: (Int) -> Unit = { index ->
        scope.launch { pagerState.animateScrollToPage(index.coerceIn(0, labels.size - 1)) }
    }

    ProvideBottomBarScrollState(bottomBarState) {
        CompositionLocalProvider(LocalMainTabSwitcher provides switchTo) {
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    // 与 ViewPager2 的 offscreenPageLimit = 1 对齐：相邻页保持组合，切换即时
                    beyondViewportPageCount = 1,
                    key = { labels[it] },
                    // 首尾页（首页 / 我）越过边界时彻底不动：默认的 overscroll 拉伸
                    // 会让页面跟着手指晃一下，看起来像还能再滑出一页
                    overscrollEffect = null,
                ) { page ->
                    // 页面是否处于前台：页面内据此实现「每次进入自动刷新」
                    CompositionLocalProvider(
                        LocalMainTabActive provides (pagerState.currentPage == page),
                    ) {
                        when (page) {
                            MainTabs.HOME -> HomeScreen(navController)
                            MainTabs.DISCOVER -> CreatorsScreen(navController)
                            MainTabs.FAVORITES -> FavoritesScreen(navController)
                            else -> MineScreen(navController)
                        }
                    }
                }

                AppBottomBarHost(bottomBarState) {
                    AppBottomBar {
                        labels.forEachIndexed { index, label ->
                            AppBottomBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = { switchTo(index) },
                                icon = icons[index],
                                label = label,
                            )
                        }
                    }
                }
            }
        }
    }
}
