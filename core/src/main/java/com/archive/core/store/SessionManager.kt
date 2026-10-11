package com.archive.core.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 Pawchive 会话和认证状态。
 *
 * 安全策略（P1）：
 * - session cookie 仅写入 EncryptedSharedPreferences，禁止明文降级；
 * - 加密初始化失败时清理损坏密钥/文件并重试一次；
 * - 仍失败则进入"加密不可用"状态：读取返回 null（视为未登录），写入直接丢弃，
 *   调用方应引导用户重新登录，绝不回退到明文 SharedPreferences。
 *
 * 多账号支持（FEATURE-003）：
 * - 保存多个账号的凭据（用户名 + cookie），支持快速切换；
 * - 切换账号时清除当前账号的本地数据（收藏/历史/下载），实现数据隔离。
 *
 * ARCH-003：已迁移至 Hilt 构造函数注入（@Singleton），由 DI 容器统一管理生命周期。
 */
@Singleton
class SessionManager @Inject constructor(@ApplicationContext context: Context) {

    @Volatile
    private var prefs: SharedPreferences? = createEncryptedPrefs(context)

    val isEncryptedStorageAvailable: Boolean get() = prefs != null

    private fun createEncryptedPrefs(context: Context): SharedPreferences? {
        createEncryptedPrefsInternal(context)?.let { return it }
        // ARCH-008：清理损坏的加密存储文件后重试；失败仅记录日志
        try {
            context.deleteSharedPreferences(PREFS_FILE_NAME)
        } catch (e: Exception) {
            Log.w("SessionManager", "deleteSharedPreferences failed", e)
        }
        return createEncryptedPrefsInternal(context)
    }

    private fun createEncryptedPrefsInternal(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) { null }
    }

    fun saveSession(cookie: String): Boolean {
        val sp = prefs ?: return false
        sp.edit()
            .putString(KEY_SESSION_COOKIE, cookie)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putLong(KEY_LOGIN_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    fun getSessionCookie(): String? = prefs?.getString(KEY_SESSION_COOKIE, null)

    /**
     * 返回最近一次登录成功的时间戳（持久化），冷启动后仍可恢复。
     * 用于 AuthRepository 的宽限期判断，避免恢复会话后首个 401 误清会话。
     */
    fun getLoginAt(): Long = prefs?.getLong(KEY_LOGIN_AT, 0L) ?: 0L

    fun isLoggedIn(): Boolean {
        val sp = prefs ?: return false
        return sp.getBoolean(KEY_IS_LOGGED_IN, false) && !getSessionCookie().isNullOrEmpty()
    }

    fun saveUsername(username: String): Boolean {
        val sp = prefs ?: return false
        sp.edit().putString(KEY_USERNAME, username).apply()
        return true
    }

    fun getUsername(): String? = prefs?.getString(KEY_USERNAME, null)

    fun clearSession() {
        prefs?.edit()
            ?.remove(KEY_SESSION_COOKIE)
            ?.remove(KEY_IS_LOGGED_IN)
            ?.remove(KEY_USERNAME)
            ?.remove(KEY_LOGIN_AT)
            ?.apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "pawchive_session"
        private const val KEY_SESSION_COOKIE = "session_cookie"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_LOGIN_AT = "login_at"
    }
}
