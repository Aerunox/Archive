package com.archive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.data.repository.AuthRepository
import com.archive.data.repository.CreatorSubscriptionRepository
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.LocalBottomBarScrollState
import com.archive.app.ui.design.hideBottomBarOnScroll
import com.archive.app.ui.design.rememberAppBottomBarScrollState
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.navigation.Routes
import com.archive.app.ui.theme.LocalPurrColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 我（个人中心）：布局参考主流 App 个人页——顶部头像 + 用户名大行，
 * 下方三组「图标 + 标题 + ›」列表，组间留白、组内细分隔线。
 */
@HiltViewModel
class MineViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    creatorSubscriptionRepository: CreatorSubscriptionRepository,
) : ViewModel() {

    data class AccountState(
        val loggedIn: Boolean = false,
        val username: String? = null,
    )

    val account = MutableStateFlow(
        AccountState(
            loggedIn = authRepository.isLoggedIn(),
            username = authRepository.getUsername(),
        ),
    )

    val unreadNotifications: StateFlow<Int> =
        creatorSubscriptionRepository.observeUnreadCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        refresh()
    }

    fun refresh() {
        account.value = AccountState(
            loggedIn = authRepository.isLoggedIn(),
            username = authRepository.getUsername(),
        )
    }
}

/** 一行：左侧小图标 + 标题 + 右侧 ›（可带未读角标）。 */
@Composable
private fun MineRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        if (badge != null && badge > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.error)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = colors.onPrimary,
                    fontSize = 11.sp,
                )
            }
        }
        Icon(
            imageVector = LucideIcons.chevronForward,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }
}

@Composable
fun MineScreen(
    navController: NavHostController,
    viewModel: MineViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val unread by viewModel.unreadNotifications.collectAsStateWithLifecycle()
    val colors = LocalPurrColors.current
    val context = LocalContext.current

    // 每次进入刷新账号状态与统计数据
    EnterRefreshEffect { viewModel.refresh() }

    AppScaffold(
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .appListModifier()
                .hideBottomBarOnScroll(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            // ── 头部：头像 + 用户名 + ›（无顶栏，直接开在状态栏下方）──
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.refresh()
                            navController.navigate(Routes.LOGIN)
                        }
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        val initial = account.username?.firstOrNull()
                        if (initial != null) {
                            Text(
                                initial.uppercase(),
                                color = colors.primary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Icon(
                                imageVector = LucideIcons.mine,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(
                            // 站点没有「取当前用户」的接口（只有 /account/favorites），所以从网页
                            // 自动登录拿到的会话可能没有用户名。此时标题必须显示「已登录」而不是
                            // 「未登录」—— 否则会与下面「已登录 · 已保存 N 个账号」自相矛盾。
                            account.username
                                ?: androidx.compose.ui.res.stringResource(
                                    if (account.loggedIn) {
                                        com.archive.app.R.string.mine_signed_in_title
                                    } else {
                                        com.archive.app.R.string.mine_not_signed_in
                                    },
                                ),
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (account.loggedIn) {
                                androidx.compose.ui.res.stringResource(
                                    com.archive.app.R.string.mine_signed_in,
                                )
                            } else {
                                androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_sign_in_hint)
                            },
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = LucideIcons.chevronForward,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // ── 第一组：单行 ──（组间不留大空隙，用一条分隔线交待组的边界）
            item {
                MineRow(
                    icon = LucideIcons.alarm,
                    title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_messages),
                    badge = unread,
                    onClick = { navController.navigate(Routes.NOTIFICATIONS) },
                )
                AppDivider(Modifier.padding(start = 54.dp))
            }

            // ── 第二组：我的内容（三行，组内细分隔线）──
            item {
                MineRow(
                    icon = LucideIcons.folder,
                    title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_storage),
                    onClick = { navController.navigate(Routes.STORAGE) },
                )
                AppDivider(Modifier.padding(start = 54.dp))
                MineRow(
                    icon = LucideIcons.downloads,
                    title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_downloads),
                    onClick = { navController.navigate(Routes.DOWNLOADS) },
                )
                AppDivider(Modifier.padding(start = 54.dp))
                MineRow(
                    icon = LucideIcons.tasks,
                    title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_history),
                    onClick = { navController.navigate(Routes.HISTORY) },
                )
                AppDivider(Modifier.padding(start = 54.dp))
            }

            // ── 第三组：设置与关于 ──
            item {
                MineRow(
                    icon = LucideIcons.settings,
                    title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_settings),
                    onClick = { navController.navigate(Routes.SETTINGS) },
                )
                AppDivider(Modifier.padding(start = 54.dp))
                MineRow(
                    icon = LucideIcons.info,
                    title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.mine_about),
                    onClick = { navController.navigate(Routes.ABOUT) },
                )
            }
        }
    }
}
