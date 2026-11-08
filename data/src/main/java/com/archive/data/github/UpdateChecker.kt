package com.archive.data.github

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.archive.core.api.GithubApi
import com.archive.core.model.GithubRelease
import com.archive.core.store.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 顶层 DataStore 单例（更新检查时间戳 / 忽略的版本）
private val Context.updateCheckerDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_checker_prefs")

// 共享 IO 作用域：明确运行在 IO 线程并带 SupervisorJob 隔离异常
private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 一次检查的结果。 */
sealed class UpdateResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
        /** 发布时间（GitHub 的 ISO 字符串，展示时再格式化）。 */
        val publishedAt: String,
    ) : UpdateResult()

    data object UpToDate : UpdateResult()

    /** 仓库还没有任何发布（GitHub 对 releases/latest 返回 404）——不是故障，别按「检查失败」报。 */
    data object NoReleases : UpdateResult()

    data object Error : UpdateResult()
}

/** 「更新记录」里的一条发布（版本号 / 更新日志 / 发布时间）。 */
data class UpdateReleaseInfo(
    val version: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val url: String,
    val prerelease: Boolean,
)

/**
 * 更新检查（FEATURE：更新通道 + 检查频率 + 自动提醒）。
 *
 * 两个入口，语义**刻意不同**：
 * - [check]：手动「检查更新」。不受总开关、间隔、以及「忽略此版本」限制 ——
 *   用户主动问了就必须如实回答；
 * - [autoCheck]：自动（启动时调用）。关了自动检查、还没到间隔、或该版本已被忽略时返回 null（静默）。
 *
 * 这里只管「检查 + 记录状态」，不做任何弹窗：界面层（Compose）统一呈现，
 * 免得同一件事出现两套观感不同的对话框（旧实现里的 MaterialAlertDialog 因此已删除）。
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsManager: SettingsManager,
) {

    private val dataStore = context.updateCheckerDataStore
    private val githubApi: GithubApi = GithubApi.create()

    private val _lastCheckAt = MutableStateFlow(0L)

    /** 上次检查时间（0 = 从未检查）：「关于」页显示它。 */
    val lastCheckAt: StateFlow<Long> = _lastCheckAt.asStateFlow()

    private val _lastAutoResult = MutableStateFlow<UpdateResult?>(null)

    /**
     * 最近一次**自动**检查的结论（含启动那次）。
     *
     * 存在理由：自动检查发生在应用启动（根组件），而「关于」页是后来才打开的 ——
     * 没有这份记录，页面上就只能显示「检查更新」这种没信息量的初始态，
     * 明明刚才已经查过一轮了。手动检查不写这里（它的结果由页面自己持有）。
     */
    val lastAutoResult: StateFlow<UpdateResult?> = _lastAutoResult.asStateFlow()

    /** 用户选择「忽略此版本」的 tag（如 "v1.7.2"）。null 表示未忽略。 */
    @Volatile
    private var ignoredVersion: String? = null

    init {
        ioScope.launch {
            runCatching {
                val prefs = dataStore.data.first()
                _lastCheckAt.value = prefs[KEY_LAST_CHECK_TIME] ?: 0L
                ignoredVersion = prefs[KEY_IGNORED_VERSION]
            }.onFailure { Log.w(TAG, "load update prefs failed", it) }
        }
    }

    /**
     * 手动检查是否有新版本。**不受**自动检查开关、检查间隔、忽略列表影响。
     *
     * 通道语义（FEATURE：更新通道）：
     * - STABLE：GitHub releases/latest（最新正式版）；
     * - BETA：全量发布列表（剔除草稿）中语义化版本最新的一条（含 beta/rc 预发布）。
     */
    suspend fun check(currentVersion: String): UpdateResult {
        return try {
            val release = latestRelease() ?: return UpdateResult.Error
            val latest = release.tagName.removePrefix("v")
            val current = currentVersion.removePrefix("v")
            if (isNewerVersion(latest, current)) {
                UpdateResult.UpdateAvailable(
                    latestVersion = release.tagName,
                    currentVersion = currentVersion,
                    releaseNotes = release.body ?: "",
                    downloadUrl = release.htmlUrl,
                    publishedAt = release.publishedAt,
                )
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                UpdateResult.NoReleases
            } else {
                Log.w(TAG, "update check failed", e)
                UpdateResult.Error
            }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed", e)
            UpdateResult.Error
        }
    }

    /**
     * 自动检查（应用启动时调用）。
     *
     * @return null = 本次**不提示**，三种静默情形：设置里关了自动检查 / 距上次检查未到设定间隔 /
     *   新版本正是用户点过「忽略此版本」的那个。否则返回检查结果（含 Error，由调用方决定要不要吭声）。
     */
    suspend fun autoCheck(currentVersion: String): UpdateResult? {
        if (!settingsManager.isAutoCheckUpdateEnabled()) return null
        if (shouldSkipCheck()) return null
        val result = check(currentVersion)
        // 检查失败（断网/超时/5xx）不推进时间戳：否则一次失败会锁死整个间隔期，
        // "每天"频率下当天再也不自动检查。只有拿到真实结论（含"已是最新"）才算检查过。
        if (result != UpdateResult.Error) recordCheckTime()
        _lastAutoResult.value = result
        if (result is UpdateResult.UpdateAvailable && result.latestVersion == ignoredVersion) return null
        return result
    }

    /** 更新记录：最近若干条发布（剔除草稿，语义化版本从新到旧）。失败返回空列表。 */
    suspend fun recentReleases(limit: Int = DEFAULT_HISTORY_LIMIT): List<UpdateReleaseInfo> =
        runCatching {
            githubApi.getReleases()
                .filter { !it.draft && it.tagName.isNotBlank() }
                .sortedWith { a, b ->
                    compareSemver(b.tagName.removePrefix("v"), a.tagName.removePrefix("v"))
                }
                .take(limit)
                .map { release ->
                    UpdateReleaseInfo(
                        version = release.tagName,
                        name = release.name,
                        notes = release.body.orEmpty(),
                        publishedAt = release.publishedAt,
                        url = release.htmlUrl,
                        prerelease = release.prerelease,
                    )
                }
        }.getOrElse {
            Log.w(TAG, "load release history failed", it)
            emptyList()
        }

    /**
     * 记住「忽略此版本」：之后**自动**检查不再为它弹窗（手动检查不受限，仍会提示）。
     */
    fun ignoreVersion(versionTag: String) {
        ignoredVersion = versionTag
        ioScope.launch {
            runCatching { dataStore.edit { it[KEY_IGNORED_VERSION] = versionTag } }
                .onFailure { Log.w(TAG, "persist ignored version failed", it) }
        }
    }

    /** 取「最新」发布：正式通道看 releases/latest；测试通道从全量里挑语义化版本最大的。 */
    private suspend fun latestRelease(): GithubRelease? =
        when (settingsManager.getUpdateChannel()) {
            SettingsManager.UpdateChannel.STABLE -> githubApi.getLatestRelease()
            SettingsManager.UpdateChannel.BETA -> githubApi.getReleases()
                .filter { !it.draft && it.tagName.isNotBlank() }
                .maxWithOrNull { a, b ->
                    compareSemver(a.tagName.removePrefix("v"), b.tagName.removePrefix("v"))
                }
        }

    /**
     * 语义化版本号比较。
     * 支持格式："1.0.10" > "1.0.9"。
     * 也支持带预发布后缀的版本号，如 "1.2.0-beta"、"1.2.0-rc.1"：
     *  - 数字部分按数值比较；
     *  - 后缀（如 -beta / -rc.1）视为预发布版本，比同号无后缀版本旧（语义化版本约定）。
     */
    @JvmSynthetic
    internal fun isNewerVersion(latest: String, current: String): Boolean {
        return compareSemver(latest, current) > 0
    }

    /**
     * 语义化版本号比较。返回正数表示 a 更新，负数表示 b 更新，0 表示相等。
     * 预发布后缀（-beta / -rc.1）比同号正式版旧（语义化版本约定）。
     */
    @JvmSynthetic
    internal fun compareSemver(a: String, b: String): Int {
        val av = parseSemver(a)
        val bv = parseSemver(b)

        val maxLength = maxOf(av.numeric.size, bv.numeric.size)
        for (i in 0 until maxLength) {
            val x = av.numeric.getOrNull(i) ?: 0
            val y = bv.numeric.getOrNull(i) ?: 0
            if (x > y) return 1
            if (x < y) return -1
        }

        return when {
            av.preRelease.isNullOrEmpty() && bv.preRelease.isNullOrEmpty() -> 0
            av.preRelease.isNullOrEmpty() -> 1   // a 为正式版，b 为预发布 → a 更新
            bv.preRelease.isNullOrEmpty() -> -1  // a 为预发布，b 为正式版 → b 更新
            else -> comparePreRelease(av.preRelease, bv.preRelease)
        }
    }

    /**
     * 预发布后缀逐段比较（semver 规则）：
     * - 按 `.` 切段；纯数字段按数值比较，字母段按字典序，数字段 < 字母段；
     * - 前段全等时段数多者更新（"1.2.0-rc.1" > "1.2.0-rc"）。
     * 字典序直比会把 "beta.10" 判成小于 "beta.9"、"rc" 判成大于 "beta"，均错。
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val asg = a.split('.')
        val bsg = b.split('.')
        val n = maxOf(asg.size, bsg.size)
        for (i in 0 until n) {
            val x = asg.getOrNull(i)
            val y = bsg.getOrNull(i)
            if (x == y) continue
            // 段数少的一方为空 → 少者更旧
            if (x == null) return -1
            if (y == null) return 1
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            return when {
                xn != null && yn != null -> xn.compareTo(yn)      // 数字段按数值
                xn != null -> -1                                   // 数字段 < 字母段
                yn != null -> 1
                else -> x.compareTo(y)                             // 字母段按字典序
            }
        }
        return 0
    }

    private data class Semver(val numeric: List<Int>, val preRelease: String?)

    private fun parseSemver(version: String): Semver {
        // 仅去除首尾空白，'v' 前缀由调用方处理
        val trimmed = version.trim()
        // 拆分预发布后缀
        val (core, pre) = if (trimmed.contains('-')) {
            val idx = trimmed.indexOf('-')
            trimmed.substring(0, idx) to trimmed.substring(idx + 1)
        } else {
            trimmed to null
        }
        val numeric = core.split(".")
            .mapNotNull { it.toIntOrNull() }
        return Semver(numeric, pre?.takeIf { it.isNotEmpty() })
    }

    /**
     * 是否应该跳过本次自动检查（间隔由设置页的「检查频率」决定）。
     * - 每次启动：从不跳过
     * - 每天（默认）/ 每周 / 自定义（[SettingsManager.getUpdateCustomDays] 天）
     */
    private fun shouldSkipCheck(): Boolean {
        val last = _lastCheckAt.value
        if (last == 0L) return false
        val intervalMs = when (settingsManager.getUpdateFrequency()) {
            SettingsManager.UpdateFrequency.EVERY_LAUNCH -> return false
            SettingsManager.UpdateFrequency.DAILY -> CHECK_INTERVAL_DAILY_MS
            SettingsManager.UpdateFrequency.WEEKLY -> CHECK_INTERVAL_WEEKLY_MS
            SettingsManager.UpdateFrequency.CUSTOM ->
                settingsManager.getUpdateCustomDays() * CHECK_INTERVAL_DAILY_MS
        }
        return (System.currentTimeMillis() - last) < intervalMs
    }

    /** 记录本次检查时间（内存立即可见，磁盘异步写）。 */
    private fun recordCheckTime() {
        val now = System.currentTimeMillis()
        _lastCheckAt.value = now
        ioScope.launch {
            runCatching { dataStore.edit { it[KEY_LAST_CHECK_TIME] = now } }
                .onFailure { Log.w(TAG, "persist last check time failed", it) }
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"

        private val KEY_LAST_CHECK_TIME = longPreferencesKey("last_check_time")

        /** 用户选择「忽略此版本」的版本 tag。 */
        private val KEY_IGNORED_VERSION = stringPreferencesKey("ignored_version")

        private const val CHECK_INTERVAL_DAILY_MS = 24 * 60 * 60 * 1000L   // 24 小时（默认）
        private const val CHECK_INTERVAL_WEEKLY_MS = 7 * CHECK_INTERVAL_DAILY_MS // 每周

        /** 「更新记录」默认展示条数。 */
        private const val DEFAULT_HISTORY_LIMIT = 20
    }
}
