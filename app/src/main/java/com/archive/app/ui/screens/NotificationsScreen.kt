package com.archive.app.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.core.store.SettingsManager
import com.archive.data.repository.CreatorSubscriptionRepository
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppEmptyState
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppSliderSetting
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.navigation.Routes
import com.archive.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用内通知中心 + 订阅管理（Pawchive ARCH-FEATURE-003）：
 * 订阅创作者的新帖检测由 ContentUpdateWorker 周期执行，结果落库供本页展示。
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val creatorSubscriptionRepository: CreatorSubscriptionRepository,
    private val settingsManager: SettingsManager,
) : ViewModel() {

    data class NotifyPrefs(
        val newPostNotification: Boolean = true,
        val syncIntervalMinutes: Int = 60,
        val autoSubscribeOnBookmark: Boolean = false,
    )

    val notifyPrefs = MutableStateFlow(refreshNotifyPrefs())

    private fun refreshNotifyPrefs(): NotifyPrefs = NotifyPrefs(
        newPostNotification = settingsManager.isNewPostNotificationEnabled(),
        syncIntervalMinutes = settingsManager.getSyncIntervalMinutes(),
        autoSubscribeOnBookmark = settingsManager.isAutoSubscribeOnBookmarkEnabled(),
    )

    fun setNewPostNotification(v: Boolean) {
        settingsManager.setNewPostNotificationEnabled(v); notifyPrefs.value = refreshNotifyPrefs()
    }

    fun setSyncIntervalMinutes(v: Int) {
        settingsManager.setSyncIntervalMinutes(v); notifyPrefs.value = refreshNotifyPrefs()
    }

    fun setAutoSubscribeOnBookmark(v: Boolean) {
        settingsManager.setAutoSubscribeOnBookmarkEnabled(v); notifyPrefs.value = refreshNotifyPrefs()
    }

    val updates = creatorSubscriptionRepository.observeUpdates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount = creatorSubscriptionRepository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val subscriptions = creatorSubscriptionRepository.observeSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncMessage = MutableStateFlow<String?>(null)

    fun markRead(id: Long) = viewModelScope.launch { creatorSubscriptionRepository.markRead(id) }

    fun markAllRead() = viewModelScope.launch { creatorSubscriptionRepository.markAllRead() }

    fun unsubscribe(service: String, creatorId: String) = viewModelScope.launch {
        creatorSubscriptionRepository.unsubscribe(service, creatorId)
    }

    fun syncNow() {
        viewModelScope.launch {
            val count = runCatching { creatorSubscriptionRepository.syncSubscribedCreators() }
                .getOrDefault(0)
            syncMessage.value = "同步完成，本次发现 $count 位作者的新内容"
        }
    }
}

@Composable
fun NotificationsScreen(
    navController: NavHostController,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val notifyPrefs by viewModel.notifyPrefs.collectAsStateWithLifecycle()

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "通知中心",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .appListModifier(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        if (unreadCount > 0) "未读 $unreadCount 条" else "暂无未读",
                        style = AppTextStyle.SMALL,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        "立即同步",
                        { viewModel.syncNow() },
                        primary = false,
                    )
                    AppButton(
                        "全部已读",
                        { viewModel.markAllRead() },
                        Modifier.padding(start = 8.dp),
                        primary = false,
                    )
                }
                syncMessage?.let {
                    AppText(it, style = AppTextStyle.SMALL, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            if (updates.isEmpty()) {
                item { AppEmptyState("还没有通知\n订阅创作者后，新帖会自动检测并通知你") }
            }
            items(updates.size) { i ->
                val item = updates[i]
                val update = item.update
                AppListItem(
                    title = (if (!update.read) "● " else "") + (update.postTitle ?: "(无标题)"),
                    summary = "${item.creatorName ?: update.creatorId} · ${Fmt.relativeIso(
                        java.time.Instant.ofEpochMilli(update.discoveredAt).toString(),
                    )}",
                    onClick = {
                        viewModel.markRead(update.id)
                        navController.navigate(Routes.post(update.service, update.creatorId, update.postId))
                    },
                )
            }

            item { AppSmallTitle("订阅管理（${subscriptions.size}）") }
            if (subscriptions.isEmpty()) {
                item { AppEmptyState("还没有订阅任何创作者\n在作者主页点「订阅」即可关注新帖") }
            }
            items(subscriptions.size) { i ->
                val sub = subscriptions[i]
                AppListItem(
                    title = sub.name ?: sub.creatorId,
                    summary = "${sub.service} · 订阅于 ${Fmt.isoDate(java.time.Instant.ofEpochMilli(sub.subscribedAt).toString())}",
                    end = {
                        AppButton(
                            "退订",
                            { viewModel.unsubscribe(sub.service, sub.creatorId) },
                            primary = false,
                        )
                    },
                    onClick = {
                        navController.navigate(Routes.creator(sub.service, sub.creatorId))
                    },
                )
            }

            // 通知与订阅参数（按类型归位：都收在消息中心）
            item { AppSmallTitle("通知设置") }
            item {
                AppSwitchSetting(
                    title = "新帖系统通知",
                    summary = "检测到订阅作者的新帖时推送系统通知",
                    checked = notifyPrefs.newPostNotification,
                    onCheckedChange = { viewModel.setNewPostNotification(it) },
                )
                AppSwitchSetting(
                    title = "收藏即订阅",
                    summary = "收藏帖子时自动订阅该创作者",
                    checked = notifyPrefs.autoSubscribeOnBookmark,
                    onCheckedChange = { viewModel.setAutoSubscribeOnBookmark(it) },
                )
                AppSliderSetting(
                    title = "新帖检测间隔（分钟，最低 15）",
                    value = notifyPrefs.syncIntervalMinutes.toFloat(),
                    onValueChange = { viewModel.setSyncIntervalMinutes(it.toInt().coerceIn(15, 240)) },
                    valueRange = 15f..240f,
                    valueText = notifyPrefs.syncIntervalMinutes.toString() + " 分钟",
                )
            }
        }
    }
}
