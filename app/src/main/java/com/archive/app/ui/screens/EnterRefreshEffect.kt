package com.archive.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue


/**
 * 「每次进入自动刷新」：页面重新回到前台时执行 [onEnter]。
 *
 * - 标签页：由 [LocalMainTabActive] 驱动（切标签回来、从二级页面返回都会重新变为前台）；
 * - 首次进入不重复刷新（初始加载已由各自的 ViewModel 完成），用 rememberSaveable 记录，
 *   这样从二级页面返回（composition 重建但保存状态与 ViewModel 仍在）时能正确刷新。
 */
@Composable
fun EnterRefreshEffect(active: Boolean = LocalMainTabActive.current, onEnter: () -> Unit) {
    var enteredOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (enteredOnce) onEnter() else enteredOnce = true
    }
}
