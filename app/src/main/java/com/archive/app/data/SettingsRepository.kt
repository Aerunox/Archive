package com.archive.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.archive.app.data.remote.UrlProvider
import com.archive.app.ui.theme.AppColorMode
import com.archive.app.ui.theme.AppMonetStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 文件损坏（写盘中途进程被杀等）时抛 CorruptionException，sessionCookie/baseUrl 的
// runCatching 会静默吞成默认值——登录态与自定义域名永久失效且无提示；替换为空偏好可恢复。
@Suppress("MissingPermission")
private val Context.settingsDataStore by preferencesDataStore(
    name = "archive_settings",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
        androidx.datastore.preferences.core.emptyPreferences()
    },
)

data class AppSettings(
    val colorMode: AppColorMode = AppColorMode.FOLLOW_SYSTEM,
    val monetStyle: AppMonetStyle = AppMonetStyle.TONAL_SPOT,
    val seedColor: Long = 0L,
    val baseUrl: String = UrlProvider.DEFAULT_BASE_URL,
    val sessionCookie: String = "",
    val blurThumbnails: Boolean = false,
    val flagSecure: Boolean = false,
    val ageConfirmed: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val MONET_STYLE = stringPreferencesKey("monet_style")
        val SEED_COLOR = longPreferencesKey("seed_color")
        val BASE_URL = stringPreferencesKey("base_url")
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val BLUR_THUMBNAILS = booleanPreferencesKey("blur_thumbnails")
        val FLAG_SECURE = booleanPreferencesKey("flag_secure")
        val AGE_CONFIRMED = booleanPreferencesKey("age_confirmed")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            colorMode = p[Keys.COLOR_MODE]?.let { runCatching { AppColorMode.valueOf(it) }.getOrNull() }
                ?: defaults.colorMode,
            monetStyle = p[Keys.MONET_STYLE]?.let { runCatching { AppMonetStyle.valueOf(it) }.getOrNull() }
                ?: defaults.monetStyle,
            seedColor = p[Keys.SEED_COLOR] ?: defaults.seedColor,
            baseUrl = (p[Keys.BASE_URL] ?: defaults.baseUrl).ifBlank { defaults.baseUrl },
            sessionCookie = p[Keys.SESSION_COOKIE] ?: "",
            blurThumbnails = p[Keys.BLUR_THUMBNAILS] ?: defaults.blurThumbnails,
            flagSecure = p[Keys.FLAG_SECURE] ?: defaults.flagSecure,
            ageConfirmed = p[Keys.AGE_CONFIRMED] ?: defaults.ageConfirmed,
        )
    }

    /**
     * 是否已经读到磁盘上的真实设置。
     *
     * 存在的理由：上面 [settings] 流的第一帧只有默认值（其中 `ageConfirmed = false`）。
     * 界面若据此判断，**每次冷启动都会先闪一下年龄确认页**——磁盘真实值抵达后又被换掉。
     * 因此界面先等这个信号，再决定是进主界面还是显示年龄确认。
     */
    val loaded: Flow<Boolean> = settings.map { true }

    /** Blocking read for OkHttp interceptors (network thread). */
    fun baseUrlOnce(): String = runCatching { kotlinx.coroutines.runBlocking { settings.first().baseUrl } }
        .getOrDefault(UrlProvider.DEFAULT_BASE_URL)

    fun sessionCookieOnce(): String = runCatching {
        kotlinx.coroutines.runBlocking { settings.first().sessionCookie }
    }.getOrDefault("")

    suspend fun setColorMode(value: AppColorMode) = edit { it[Keys.COLOR_MODE] = value.name }
    suspend fun setMonetStyle(value: AppMonetStyle) = edit { it[Keys.MONET_STYLE] = value.name }
    suspend fun setSeedColor(value: Long) = edit { it[Keys.SEED_COLOR] = value }
    suspend fun setBaseUrl(value: String) = edit { it[Keys.BASE_URL] = value.trim() }
    suspend fun setSessionCookie(value: String) = edit { it[Keys.SESSION_COOKIE] = value.trim() }
    suspend fun setBlurThumbnails(value: Boolean) = edit { it[Keys.BLUR_THUMBNAILS] = value }
    suspend fun setFlagSecure(value: Boolean) = edit { it[Keys.FLAG_SECURE] = value }
    suspend fun setAgeConfirmed(value: Boolean) = edit { it[Keys.AGE_CONFIRMED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
