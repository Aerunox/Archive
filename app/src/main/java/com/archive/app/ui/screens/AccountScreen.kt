package com.archive.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.core.store.SessionManager
import com.archive.data.repository.AuthRepository
import com.archive.app.data.SettingsRepository
import com.archive.app.ui.components.CheckMorphIcon
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextField
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.CommonTabButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 账号登录 / 注册 / 多账号切换（功能移植自 Pawchive AuthRepository）。
 * 登录成功后把 session Cookie 桥接到本 App 的 DataStore，使旧有功能同样可用。
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val postRepository: com.archive.app.data.repo.PostRepository,
) : ViewModel() {

    /** 会话有效性（/account/favorites 探测：200=有效，401=失效）。 */
    enum class SessionState { UNKNOWN, CHECKING, VALID, INVALID }

    val sessionState = MutableStateFlow(SessionState.UNKNOWN)

    fun checkSession() {
        val hasCookie = sessionManager.isLoggedIn() || sessionManager.getSessionCookie().orEmpty().isNotBlank()
        if (!hasCookie) {
            sessionState.value = SessionState.INVALID
            return
        }
        if (sessionState.value == SessionState.CHECKING) return
        viewModelScope.launch {
            sessionState.value = SessionState.CHECKING
            sessionState.value = runCatching { postRepository.accountFavorites("artist") }
                .fold(
                    onSuccess = { SessionState.VALID },
                    onFailure = { error ->
                        val message = error.message ?: ""
                        if (message.contains("401") || message.contains("Unauthorized")) {
                            SessionState.INVALID
                        } else {
                            SessionState.UNKNOWN
                        }
                    },
                )
        }
    }


    data class UiState(
        val loggedIn: Boolean = false,
        val username: String? = null,
        val busy: Boolean = false,
        val message: String? = null,
        val success: Boolean = false,
        val sessionCookieForEdit: String = "",
    )

    private val _state = MutableStateFlow(UiState(loggedIn = authRepository.isLoggedIn(), username = authRepository.getUsername()))
    val state: StateFlow<UiState> = _state

    init {
        refresh()
        checkSession()
    }

    fun refresh() {
        _state.value = _state.value.copy(
            loggedIn = authRepository.isLoggedIn(),
            username = authRepository.getUsername(),
            sessionCookieForEdit = sessionManager.getSessionCookie().orEmpty(),
        )
    }

    fun login(username: String, password: String) = submit {
        authRepository.login(username, password)
            .onSuccess {
                bridgeSession()
                _state.value = _state.value.copy(message = "登录成功，欢迎回来 $it", success = true)
            }
            .onFailure { _state.value = _state.value.copy(message = it.message ?: "登录失败") }
    }

    fun register(username: String, password: String, confirm: String) = submit {
        authRepository.register(username, password, confirm)
            .onSuccess {
                _state.value = _state.value.copy(message = "注册成功，请登录", success = true)
            }
            .onFailure { _state.value = _state.value.copy(message = it.message ?: "注册失败") }
    }

    fun logout() = submit {
        authRepository.logout()
            .onSuccess {
                settingsRepository.setSessionCookie("")
                _state.value = _state.value.copy(message = "已退出登录", success = false)
            }
            .onFailure { _state.value = _state.value.copy(message = it.message ?: "退出失败") }
    }

    private fun bridgeSession() {
        sessionManager.getSessionCookie()?.let { cookie ->
            viewModelScope.launch { settingsRepository.setSessionCookie(cookie) }
        }
        refresh()
    }

    /**
     * 注册页（内嵌 WebView）里拿到 session cookie 后自动登录。
     *
     * **先探测再落库**：注册页刚打开时页面里的 session 往往仍是**未登录的匿名会话**，
     * 直接导入就会出现「界面显示已登录、登录状态却是会话失效」的自相矛盾（旧行为正是如此）。
     * 因此这里先把这个 cookie 写进去探测一次，只有探测通过才真正落库；否则**退回原状**，
     * 等用户在网页上登录后，下一次页面加载会带着有效会话再来一次。
     *
     * 探测用的是与「登录状态」完全相同的那次请求（[PostRepository.accountFavorites]），
     * 所以两处结论必然一致，不会再出现自相矛盾。
     */
    private var importingSession = false

    /** 已经处理过的 cookie 值（无论成功与否）：避免同一会话被反复探测。 */
    private var handledWebCookie: String? = null

    fun importSessionFromWeb(cookie: String) {
        if (cookie.isBlank()) return
        if (cookie == handledWebCookie) return
        if (importingSession) return
        importingSession = true
        handledWebCookie = cookie
        viewModelScope.launch {
            val previous = sessionManager.getSessionCookie().orEmpty()
            settingsRepository.setSessionCookie(cookie)
            val valid = runCatching { postRepository.accountFavorites("artist") }.isSuccess
            if (valid) {
                sessionManager.saveSession(cookie)
                _state.value = _state.value.copy(message = "已自动获取登录会话", success = true)
            } else {
                // 匿名会话：回退，不要把「有 cookie」当成「已登录」
                settingsRepository.setSessionCookie(previous)
                handledWebCookie = null // 允许同一 cookie 在真正登录后重新尝试
            }
            refresh()
            checkSession()
            importingSession = false
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, success = false)
            try {
                block()
                refresh()
            } catch (throwable: Throwable) {
                // 原来没有 try/finally：block 或 refresh 里任何一次意外异常
                // 都会让 busy 永远停在 true，按钮就卡在「请稍候…」再也点不动。
                android.util.Log.e("Account", "账号操作异常", throwable)
                _state.value = _state.value.copy(
                    message = _state.value.message ?: throwable.message ?: "操作失败",
                )
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}

/** 账号页：登录 / 注册 / 多账号切换 / 退出登录。 */
@Composable
fun AccountScreen(
    navController: NavHostController,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = com.archive.app.ui.theme.LocalPurrColors.current
    // 会话状态的可视化：账号卡里的状态圆点与「登录状态」行共用同一份摘要文案
    val sessionSummary = when (sessionState) {
        AccountViewModel.SessionState.VALID -> "已登录（会话有效）"
        AccountViewModel.SessionState.CHECKING -> "检查中…"
        AccountViewModel.SessionState.INVALID -> "未登录或会话已失效"
        AccountViewModel.SessionState.UNKNOWN -> "未知（网络不可用时无法确认）"
    }
    val sessionDotColor = when (sessionState) {
        AccountViewModel.SessionState.VALID -> colors.primary
        AccountViewModel.SessionState.INVALID -> colors.error
        AccountViewModel.SessionState.CHECKING,
        AccountViewModel.SessionState.UNKNOWN -> colors.textTertiary
    }
    var mode by remember { mutableIntStateOf(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "账号",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            // 注册页是内嵌 WebView：外层列表必须让出纵向手势，
            // 否则滑动会被 LazyColumn 抢走，网页根本滚不动。
            userScrollEnabled = !(mode == 1 && !state.loggedIn),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (state.loggedIn) {
                // 账号卡（已登录）：首字母头像 + 账号名 + 会话状态（带状态圆点）。
                // 整卡可点击重新检测 —— 取代原来「账号名一行 + 登录状态一行」的平铺两段式，
                // 也消除了两行信息重复的问题。
                item {
                    AppCard(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        onClick = { viewModel.checkSession() },
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    (state.username ?: "A").take(1).uppercase(),
                                    color = colors.primary,
                                    fontSize = 20.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                AppText(
                                    state.username ?: "已登录",
                                    style = AppTextStyle.MAIN,
                                )
                                Row(
                                    Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 状态圆点：主题色（有效）/ 错误色（失效）/ 灰（检查中、未知）
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(sessionDotColor),
                                    )
                                    AppText(
                                        "$sessionSummary · 点击重新检测",
                                        style = AppTextStyle.SMALL,
                                        color = colors.textTertiary,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                // 登录成功、退出、自动登录等提示仍显示在这里
                                val message = state.message
                                if (!message.isNullOrBlank()) {
                                    AppText(
                                        message,
                                        style = AppTextStyle.SMALL,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 未登录：仅在有提示信息（登录失败、自动登录结果等）时才显示卡片，
                // 否则会孤零零留一个「未登录」标签
                val pendingMessage = state.message
                if (!pendingMessage.isNullOrBlank()) {
                    item {
                        AppCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                val message = state.message
                                when {
                                    message.isNullOrBlank() -> Unit
                                    state.success -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        CheckMorphIcon(visible = true, iconSize = 16.dp)
                                        AppText(message, style = AppTextStyle.SMALL)
                                    }

                                    else -> AppText(message, style = AppTextStyle.SMALL)
                                }
                            }
                        }
                    }
                }

                // 登录状态：未登录时保留独立一行（已登录时已折叠进上方账号卡）
                item {
                    AppListItem(
                        title = "登录状态",
                        summary = sessionSummary,
                        onClick = { viewModel.checkSession() },
                    )
                }
            }

            if (!state.loggedIn) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        CommonTabButton("登录", mode == 0, Modifier.weight(1f)) { mode = 0 }
                        CommonTabButton("注册新账号", mode == 1, Modifier.weight(1f)) { mode = 1 }
                    }
                    if (mode == 0) {
                        // 登录：站点登录不需要人机验证，继续用原生表单
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            AppTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = "用户名",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "密码",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            AppButton(
                                text = if (state.busy) "请稍候…" else "登录",
                                onClick = { viewModel.login(username, password) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
                            )
                            AppText(
                                "登录成功后会话仅保存在本机加密存储；多账号可随时切换，账号间收藏/历史/下载数据相互隔离。",
                                style = AppTextStyle.SMALL,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    } else {
                        // 注册：站点要求人机验证（实测返回 Captcha verification failed），原生表单必然失败。
                        // 改为在应用内直接嵌入官方注册页 —— 验证码在其中能正常显示与通过；
                        // 页面加载失败或设备没有 WebView 时，组件内会给出原因与「在浏览器中打开」出口。
                        AppText(
                            androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.register_web_hint,
                            ),
                            style = AppTextStyle.SMALL,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        com.archive.app.ui.components.RegisterWebForm(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 留出提示与下方浏览器按钮的空间，避免小屏上按钮被挤出屏幕
                                .fillParentMaxHeight(0.6f)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            // 注册/登录成功后站点会写入 session cookie：自动导入即完成登录
                            onSessionCookie = viewModel::importSessionFromWeb,
                        )
                        AppButton(
                            text = androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.register_web_open_browser,
                            ),
                            onClick = {
                                com.archive.app.ui.components.openInExternalBrowser(
                                    context = context,
                                    url = com.archive.app.ui.components.REGISTER_URL,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            primary = false,
                        )
                    }
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        AppButton(
                            "退出登录",
                            { confirmLogout = true },
                            Modifier.fillMaxWidth(),
                            primary = false,
                        )
                    }
                }
            }


        }

        // 退出登录确认（自设置页迁入）
        AppDialog(
            show = confirmLogout,
            title = "退出登录",
            summary = "将清除本机保存的会话 Cookie 与网页登录状态，账号收藏将不可用。",
            onDismiss = { confirmLogout = false },
            confirmLabel = "退出",
            onConfirm = {
                viewModel.logout()
                confirmLogout = false
            },
            dismissLabel = "取消",
        )
    }
}


