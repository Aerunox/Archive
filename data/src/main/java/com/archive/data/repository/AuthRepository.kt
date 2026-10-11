package com.archive.data.repository

import android.content.Context
import com.archive.data.R
import com.archive.core.error.AppError
import com.archive.core.api.ApiCallHandler
import com.archive.core.api.ApiClient
import com.archive.core.api.ApiMemoryCache
import com.archive.core.api.ApiResult
import com.archive.core.api.ClearanceCoordinator
import com.archive.core.api.PawchiveApi
import com.archive.core.model.FavoriteCreator
import com.archive.core.model.FavoritePost
import com.archive.core.model.PendingFavoriteActionKind
import com.archive.core.store.SessionManager
import com.archive.core.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 处理认证和收藏同步的仓库类（ARCH-003：已迁移至 Hilt 构造函数注入）。
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val localDataCleaner: LocalDataCleaner,
    private val pendingFavoriteActionRepository: PendingFavoriteActionRepository
) {

    /** 登录成功后的宽限期（毫秒）：期间单个 401 不立即清除会话，防止"登录失效"误判导致登录循环 */
    private val LOGIN_GRACE_MS = 60_000L

    private companion object {
        /** 跳过 ApiMemoryCache 的请求头值（与 HomeViewModel 下拉刷新口径一致）。 */
        const val NO_CACHE = "no-cache"

        /**
         * 收藏列表接口路径（[ApiClient.API_BASE_URL] 之后的部分）。
         * 写入收藏成功后按此路径前缀失效内存缓存。
         */
        const val FAVORITES_PATH = "/api/v1/account/favorites"
    }

    /**
     * 最近一次登录成功的时间戳，用于宽限期判断。
     * 从 SessionManager 持久化恢复，冷启动后仍有效。
     */
    @Volatile
    private var lastLoginAt = sessionManager.getLoginAt()

    /**
     * 登录并提取 session cookie
     * Pawchive 使用 Flask session，无论登录成功或失败都会返回 302 + Set-Cookie
     * 区分方式：检查 Location 头，重定向到首页 / 表示成功，重定向回 /account/login 表示失败
     */
    suspend fun login(username: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // ARCH-009：登录前确保已过盾（主域请求可能被 Cloudflare 拦截）
                ClearanceCoordinator.ensureClearance()
                val loginApi = ApiClient.loginApi
                val response = loginApi.login(username, password)

                // 检查响应码（登录接口通常返回 30x 重定向）
                val statusCode = response.code()
                if (statusCode !in 300..399) {
                    return@withContext Result.failure(
                        AppError.Server(statusCode, "登录失败，HTTP $statusCode")
                    )
                }

                // 检查重定向目标：重定向到登录页表示失败，其他目标通常表示成功
                val locationHeader = response.headers()["Location"]?.lowercase().orEmpty()
                if (locationHeader.contains("/account/login") || locationHeader.endsWith("/login")) {
                    return@withContext Result.failure(
                        AppError.Auth(AppError.Auth.Reason.INVALID_CREDENTIALS)
                    )
                }

                // 提取 session cookie
                val cookies = response.headers().values("Set-Cookie")
                val sessionCookie = cookies.find { it.startsWith("session=") }
                    ?.substringAfter("session=")
                    ?.substringBefore(";")

                if (!sessionCookie.isNullOrEmpty()) {
                    sessionManager.saveSession(sessionCookie)
                    sessionManager.saveUsername(username)
                    lastLoginAt = System.currentTimeMillis()
                    Result.success(username)
                } else {
                    Result.failure(AppError.Business("登录失败，服务器未返回 session cookie"))
                }
            } catch (e: Exception) {
                Result.failure(AppError.from(e))
            }
        }
    }

    /**
     * 注册新账户
     * Pawchive 注册接口：
     * - 成功 → 302 重定向到登录页或首页
     * - 用户名已存在 → 302 重定向回注册页
     * - 其他验证失败 → 200 重新渲染表单（HTML 中包含错误信息）
     */
    suspend fun register(username: String, password: String, confirmPassword: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (password != confirmPassword) {
                    return@withContext Result.failure(
                        AppError.Business(context.getString(R.string.error_passwords_not_match))
                    )
                }

                // 站点注册表单上写着 data-pattern="^[a-z0-9_@+.\-]{3,15}$"：
                // 大写字母、空格、中文、长度不符都会被拒。先在本地拦下，
                // 用户就不会只看到一句「注册失败」却不知道哪儿写错了。
                if (!USERNAME_PATTERN.matches(username.trim())) {
                    return@withContext Result.failure(
                        AppError.Business(context.getString(R.string.register_username_rule))
                    )
                }

                // ARCH-009：注册前确保已过盾
                ClearanceCoordinator.ensureClearance()
                val loginApi = ApiClient.loginApi
                val response = loginApi.register(username, password, confirmPassword)

                val statusCode = response.code()
                val locationHeader = response.headers()["Location"]?.lowercase().orEmpty()

                val bodyString = response.body()?.string().orEmpty()

                when {
                    statusCode in 300..399 && !locationHeader.contains("/account/register") -> {
                        Result.success(username)
                    }

                    statusCode in 300..399 && locationHeader.contains("/account/register") -> {
                        Result.failure(AppError.Business(extractRegisterError(bodyString, context)))
                    }

                    statusCode == 200 -> {
                        Result.failure(AppError.Business(extractRegisterError(bodyString, context)))
                    }

                    else -> {
                        Result.failure(AppError.Server(statusCode, context.getString(R.string.register_failed, statusCode)))
                    }
                }
            } catch (e: Exception) {
                Result.failure(AppError.from(e))
            }
        }
    }

    /** 站点注册表单的用户名规则（取自表单自身的 `data-pattern`）。 */
    private val USERNAME_PATTERN = Regex("^[a-z0-9_@+.\\-]{3,15}$")

    /**
     * 从注册失败的响应里读出**站点自己给出的原因**。
     *
     * 站点把校验信息放在 `<div class="flash_messages">…</div>` 里（实测：
     * `Captcha verification failed. Please try again.` / `Invalid username`），
     * 所以优先解析这个区块 —— 比用正则猜「可能是哪种错误」准确得多，
     * 也让用户看到的是真实原因而不是一句笼统的「注册失败」。
     */
    private fun extractRegisterError(htmlBody: String, context: Context): String {
        val flash = Regex(
            """flash_messages[^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE,
        ).find(htmlBody)?.groupValues?.get(1)

        val messages = flash
            ?.let { Regex("<[^>]+>").replace(it, "\n") }
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.length > 3 }
            .orEmpty()

        if (messages.any { it.contains("captcha", ignoreCase = true) }) {
            // 站点要求人机验证：原生表单提交过不去，直接告诉用户可行的路径
            return context.getString(R.string.register_failed_captcha)
        }
        if (messages.any { it.contains("username", ignoreCase = true) }) {
            return context.getString(R.string.register_username_rule)
        }
        if (messages.isNotEmpty()) {
            // 其余原因（如密码泄露库命中）原样回传，便于用户自行处理
            return messages.joinToString("；").take(220)
        }

        // 兜底：错误不在 flash 区块里时，沿用原有的关键字匹配
        val errorPatterns = listOf(
            "Username.*already.*exists",
            "already.*taken",
            "already.*registered",
            "Username must be",
            "must be.*3.*15",
            "only.*letters.*numbers",
            "Password must be",
            "must be.*5.*characters",
            "confirm.*password.*must.*match",
            "contains.*invalid",
            "invalid.*character",
        )
        for (pattern in errorPatterns) {
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(htmlBody)) {
                return context.getString(R.string.register_failed_validation)
            }
        }
        return context.getString(R.string.register_failed_unknown)
    }

    /**
     * 登出
     * 清除会话并清理该账号的本地数据（FEATURE-003 数据边界）。
     */
    suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // 清除当前账号的本地数据（收藏/历史/下载）以实现数据隔离
            localDataCleaner.clearAllLocalData()
            sessionManager.clearSession()
            ApiClient.clearMemoryCache()
            Result.success(Unit)
        }
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    /**
     * 获取当前用户名
     */
    fun getUsername(): String? = sessionManager.getUsername()

    /**
     * 获取已认证的 API 客户端
     */
    fun getAuthenticatedApi(): PawchiveApi {
        val cookie = sessionManager.getSessionCookie()
        return if (cookie != null) {
            ApiClient.authApi(cookie)
        } else {
            ApiClient.publicApi
        }
    }

    /**
     * 同步账号收藏的帖子
     *
     * @param offset 分页偏移；null 表示首页
     * @param forceRefresh true 时携带 `Cache-Control: no-cache` 跳过 5 分钟内存缓存，
     *   用于下拉刷新与返回本页时的静默同步——否则会直接命中旧缓存，看不到新收藏
     */
    suspend fun syncFavoritePosts(
        offset: Int? = null,
        forceRefresh: Boolean = false
    ): Result<List<FavoritePost>> {
        return withContext(Dispatchers.IO) {
            ensureLoggedIn { api ->
                val result = ApiCallHandler.safeApiCallDirect {
                    api.getFavoritePosts(
                        offset = offset,
                        cacheControl = if (forceRefresh) NO_CACHE else null
                    )
                }
                apiResultToResult(result)
            }
        }
    }

    /**
     * 同步账号收藏的创作者
     *
     * @param forceRefresh 语义同 [syncFavoritePosts]
     */
    suspend fun syncFavoriteCreators(forceRefresh: Boolean = false): Result<List<FavoriteCreator>> {
        return withContext(Dispatchers.IO) {
            ensureLoggedIn { api ->
                val result = ApiCallHandler.safeApiCallDirect {
                    api.getFavoriteCreators(cacheControl = if (forceRefresh) NO_CACHE else null)
                }
                apiResultToResult(result)
            }
        }
    }

    /**
     * 添加帖子到账号收藏（FEAT-OFFLINE-FAV-QUEUE：离线时入队待同步，联网后自动补写）。
     *
     * 成功后失效收藏列表的内存缓存，保证收藏页能立即看到本次写入。
     */
    suspend fun addPostToFavorites(service: String, creatorId: String, postId: String): Result<Unit> {
        return favoriteWriteWithOfflineQueue(PendingFavoriteActionKind.ADD_POST, service, creatorId, postId) {
            addPostToFavoritesDirect(service, creatorId, postId)
        }
    }

    internal suspend fun addPostToFavoritesDirect(service: String, creatorId: String, postId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            ensureLoggedIn { api ->
                val result = ApiCallHandler.safeApiCallUnit {
                    api.addPostToFavorites(service, creatorId, postId)
                }
                apiResultToResult(result).onSuccess { invalidateFavoritesCache() }
            }
        }
    }

    /**
     * 从账号收藏移除帖子（FEAT-OFFLINE-FAV-QUEUE：离线时入队待同步，联网后自动补写）。
     *
     * 成功后失效收藏列表的内存缓存（同上）。
     */
    suspend fun removePostFromFavorites(service: String, creatorId: String, postId: String): Result<Unit> {
        return favoriteWriteWithOfflineQueue(PendingFavoriteActionKind.REMOVE_POST, service, creatorId, postId) {
            removePostFromFavoritesDirect(service, creatorId, postId)
        }
    }

    internal suspend fun removePostFromFavoritesDirect(service: String, creatorId: String, postId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            ensureLoggedIn { api ->
                val result = ApiCallHandler.safeApiCallUnit {
                    api.removePostFromFavorites(service, creatorId, postId)
                }
                apiResultToResult(result).onSuccess { invalidateFavoritesCache() }
            }
        }
    }

    /**
     * 添加创作者到账号收藏（FEAT-OFFLINE-FAV-QUEUE：离线时入队待同步，联网后自动补写）。
     *
     * 成功后失效收藏列表的内存缓存（同上）。
     */
    suspend fun addCreatorToFavorites(service: String, creatorId: String): Result<Unit> {
        return favoriteWriteWithOfflineQueue(PendingFavoriteActionKind.ADD_CREATOR, service, creatorId, null) {
            addCreatorToFavoritesDirect(service, creatorId)
        }
    }

    internal suspend fun addCreatorToFavoritesDirect(service: String, creatorId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            ensureLoggedIn { api ->
                val result = ApiCallHandler.safeApiCallUnit {
                    api.addCreatorToFavorites(service, creatorId)
                }
                apiResultToResult(result).onSuccess { invalidateFavoritesCache() }
            }
        }
    }

    /**
     * 从账号收藏移除创作者（FEAT-OFFLINE-FAV-QUEUE：离线时入队待同步，联网后自动补写）。
     *
     * 成功后失效收藏列表的内存缓存（同上）。
     */
    suspend fun removeCreatorFromFavorites(service: String, creatorId: String): Result<Unit> {
        return favoriteWriteWithOfflineQueue(PendingFavoriteActionKind.REMOVE_CREATOR, service, creatorId, null) {
            removeCreatorFromFavoritesDirect(service, creatorId)
        }
    }

    internal suspend fun removeCreatorFromFavoritesDirect(service: String, creatorId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            ensureLoggedIn { api ->
                val result = ApiCallHandler.safeApiCallUnit {
                    api.removeCreatorFromFavorites(service, creatorId)
                }
                apiResultToResult(result).onSuccess { invalidateFavoritesCache() }
            }
        }
    }

    /**
     * 收藏写操作统一入口（FEAT-OFFLINE-FAV-QUEUE）。
     *
     * 设备离线（NetworkUtils 判定无可用网络）时把动作入队并返回成功：
     * UI 保持本地乐观状态、提示"联网后自动同步"；联网后由
     * FavoriteActionsSyncWorker 依序补写。在线时的失败原样上报，不进队列
     * （站点故障被静默吞掉反而误导用户）。
     */
    private suspend fun favoriteWriteWithOfflineQueue(
        kind: PendingFavoriteActionKind,
        service: String,
        creatorId: String,
        postId: String?,
        call: suspend () -> Result<Unit>
    ): Result<Unit> {
        val result = call()
        if (result.isSuccess) return result
        if (NetworkUtils.isNetworkAvailable(context)) return result
        pendingFavoriteActionRepository.enqueue(kind, service, creatorId, postId)
        return Result.success(Unit)
    }

    /**
     * 收藏写入序号：每次成功增删收藏后自增（进程内，不持久化）。
     *
     * 收藏页在发起拉取时记录该值，返回本页时若发现它已变化，说明期间发生过收藏写入，
     * 需要**绕过静默刷新的节流**立即重新拉取。
     *
     * 否则会出现：刚在 App 内收藏完就返回收藏页，因距上次拉取不足节流间隔而跳过请求，
     * 依旧看不到刚写入的条目——正是本次要修的问题。
     */
    @Volatile
    private var favoritesWriteTick = 0L

    /**
     * 当前收藏写入序号（语义见 [favoritesWriteTick]）。
     * 供收藏页判断"自上次拉取以来是否发生过收藏写入"。
     */
    fun currentFavoritesWriteTick(): Long = favoritesWriteTick

    /**
     * 收藏写入成功后失效收藏列表的内存缓存。
     *
     * 收藏列表走 GET 且被 [ApiMemoryCache] 缓存 5 分钟；而增删收藏走 POST/DELETE，
     * 不经过该缓存拦截器。若不在写入后显式失效，收藏页会滞后最长 5 分钟
     * 才能反映本次写入（App 内收藏后返回收藏页看不到新条目即由此导致）。
     */
    private fun invalidateFavoritesCache() {
        ApiMemoryCache.invalidateByPathPrefix(FAVORITES_PATH)
        favoritesWriteTick++
    }

    private suspend fun <T> ensureLoggedIn(block: suspend (PawchiveApi) -> Result<T>): Result<T> {
        return if (!sessionManager.isLoggedIn()) {
            Result.failure(AppError.Auth(AppError.Auth.Reason.NOT_LOGGED_IN))
        } else {
            try {
                val api = getAuthenticatedApi()
                block(api)
            } catch (e: Exception) {
                Result.failure(AppError.from(e))
            }
        }
    }

    private fun <T> apiResultToResult(apiResult: ApiResult<T>): Result<T> {
        // 统一使用 AppError 包装错误，UI 层可直接通过 toMessage() 获取友好文案（P2 BACKEND-007）
        return when (apiResult) {
            is ApiResult.Success -> Result.success(apiResult.data)
            is ApiResult.Error.NetworkError -> Result.failure(
                AppError.from(apiResult.cause ?: Exception(apiResult.message))
            )
            is ApiResult.Error.AuthError -> {
                if (withinLoginGrace()) {
                    // 登录宽限期内的单个 401 不立即清除会话：WebView 匿名 session 污染等
                    // 误判会触发"登录即失效"循环；宽限期后仍 401 才判定会话真正失效
                    Result.failure(AppError.Unknown(IllegalStateException("HTTP 401 during login grace")))
                } else {
                    sessionManager.clearSession()
                    Result.failure(AppError.Auth(AppError.Auth.Reason.SESSION_EXPIRED))
                }
            }
            is ApiResult.Error.ServerError -> {
                if (apiResult.code == 401) {
                    if (withinLoginGrace()) {
                        Result.failure(AppError.Unknown(IllegalStateException("HTTP 401 during login grace")))
                    } else {
                        sessionManager.clearSession()
                        Result.failure(AppError.Auth(AppError.Auth.Reason.SESSION_EXPIRED))
                    }
                } else {
                    Result.failure(AppError.Server(apiResult.code, apiResult.message))
                }
            }
            is ApiResult.Error.UnknownError -> Result.failure(
                AppError.from(apiResult.cause)
            )
        }
    }

    /** 是否处于登录成功后的宽限期内（此期间单次 401 不判定为会话失效） */
    private fun withinLoginGrace(): Boolean =
        lastLoginAt > 0L && System.currentTimeMillis() - lastLoginAt < LOGIN_GRACE_MS
}
