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
import com.archive.core.api.GithubReleaseFeed
import com.archive.core.api.GithubRepo
import com.archive.core.api.GithubWebApi
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
import kotlinx.coroutines.withContext

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

    /**
     * 检查没能完成（断网 / 超时 / 5xx / 被限流）。
     *
     * 从「无字段的 data object」升级成带原因的 data class：之前界面只能显示一句
     * 「检查更新失败」，用户拿着这句话完全无法判断该怪网络、怪仓库还是怪自己点太快。
     */
    data class Error(
        /** true = 被 GitHub 的匿名配额 / 频率限制挡住了（每小时 60 次，按出口 IP 算），不是网络坏了。 */
        val rateLimited: Boolean = false,
        /** 仅供日志的简短原因（HTTP 码或异常类名），不上界面。 */
        val detail: String = "",
    ) : UpdateResult()
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

    /** 网页端兜底数据源（github.com 的 HTML / Atom 端点，不计入 api.github.com 的配额）。 */
    private val githubWebApi: GithubWebApi = GithubWebApi.create()

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

    /** 上次已经替用户自动唤起过浏览器的版本 tag（同一版本只自动跳一次，避免每次启动都被拽走）。 */
    @Volatile
    private var browserOpenedVersion: String? = null

    init {
        ioScope.launch {
            runCatching {
                val prefs = dataStore.data.first()
                _lastCheckAt.value = prefs[KEY_LAST_CHECK_TIME] ?: 0L
                ignoredVersion = prefs[KEY_IGNORED_VERSION]
                browserOpenedVersion = prefs[KEY_BROWSER_OPENED_VERSION]
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
        var rateLimited = false
        var detail = ""

        // 先问 API；问不到（被限流 / 网络异常 / 服务端 5xx）再退到网页 feed。
        // 之所以要退：api.github.com 的匿名配额是**按出口 IP** 算的（60 次/小时），
        // 用户自己多按几次、或和别人共用同一个代理出口，就会撞上 403 ——
        // 那不是「检查失败」，仓库其实好好的，只是这条路被限流了。
        val release: GithubRelease? = try {
            latestRelease()
        } catch (e: retrofit2.HttpException) {
            // 404 = 仓库还没有发布：这是**真实结论**，不是故障，也不值得再兜底。
            if (e.code() == 404) return UpdateResult.NoReleases
            detail = "HTTP " + e.code()
            rateLimited = e.code() == 403 || e.code() == 429
            if (rateLimited) {
                Log.w(TAG, "api rate limited (HTTP " + e.code() + "), falling back to web feed")
            } else {
                Log.w(TAG, "update check failed", e)
            }
            webFeedRelease()
        } catch (e: Exception) {
            detail = e.javaClass.simpleName
            Log.w(TAG, "update check failed, falling back to web feed", e)
            webFeedRelease()
        }

        // 两条路都没结果 → 这才是真的失败。
        if (release == null) {
            return UpdateResult.Error(rateLimited = rateLimited, detail = detail)
        }

        val latest = release.tagName.removePrefix("v")
        val current = currentVersion.removePrefix("v")
        return if (isNewerVersion(latest, current)) {
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
    }

    /**
     * 拉取网页 feed 里的全部发布（最新在前）。失败返回空列表，不抛异常。
     *
     * feed 里只有 tag / 标题 / 发布时间 / 落地页：它不区分草稿，
     * **也没有更新说明**（content 是渲染后的 HTML，塞进 Markdown 渲染器只会显示成源码，
     * 见 [GithubReleaseFeed]）—— 所以兜底结果的 notes 永远为空，界面按「去发布页看详情」呈现。
     */
    private suspend fun webFeedReleases(): List<GithubRelease> = runCatching {
        // .string() 是阻塞读 body：Retrofit 的 suspend 只保证请求在后台发出，
        // body 消费发生在调用线程上，所以这里显式切到 IO。
        val xml = withContext(Dispatchers.IO) {
            githubWebApi.releasesAtom(GithubRepo.ATOM_URL).string()
        }
        GithubReleaseFeed.parse(xml).filter { it.tagName.isNotBlank() }
    }.getOrElse {
        Log.w(TAG, "web feed fallback failed", it)
        emptyList()
    }

    /** 网页 feed 兜底：用 github.com 的 releases.atom 得到「有没有新版本」的结论（按当前通道挑最新）。 */
    private suspend fun webFeedRelease(): GithubRelease? {
        val releases = webFeedReleases()
        if (releases.isEmpty()) return null
        // 正式通道不把预发布算作更新：feed 里没有 prerelease 标记，
        // 按语义化版本约定用「tag 里带预发布后缀」来区分（"1.0.3-beta" 带， "1.0.3" 不带）。
        val candidates = when (settingsManager.getUpdateChannel()) {
            SettingsManager.UpdateChannel.STABLE ->
                releases.filter { !isPreReleaseTag(it.tagName) }
            SettingsManager.UpdateChannel.BETA -> releases
        }
        return candidates.maxWithOrNull { a, b ->
            compareSemver(a.tagName.removePrefix("v"), b.tagName.removePrefix("v"))
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
        if (result !is UpdateResult.Error) recordCheckTime()
        _lastAutoResult.value = result
        if (result is UpdateResult.UpdateAvailable && result.latestVersion == ignoredVersion) return null
        return result
    }

    /**
     * 更新记录：最近若干条发布（剔除草稿，语义化版本从新到旧）。失败返回空列表。
     *
     * 与 [check] 一样会兜底：API 被限流时改读网页 feed（那里没有草稿概念，
     * 也没有更新说明 —— feed 的 content 是渲染后的 HTML，见 [GithubReleaseFeed]）。
     */
    suspend fun recentReleases(limit: Int = DEFAULT_HISTORY_LIMIT): List<UpdateReleaseInfo> =
        runCatching {
            val list = runCatching { githubApi.getReleases() }.getOrElse { e ->
                Log.w(TAG, "load release history via api failed, falling back to web feed", e)
                emptyList()
            }.ifEmpty { webFeedReleases() }
            list
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

    /**
     * 领取「为这个版本自动唤起一次浏览器」的资格。
     *
     * 同一版本只自动跳一次：用户被带到下载页看过（或自己退出来）之后，
     * 之后每次启动只留 toast + 浮层，不再把人从应用里拽出去。
     * 返回 true 时**立即**记账（内存先记、磁盘异步写），重入调用直接返回 false。
     */
    fun claimBrowserOpen(versionTag: String): Boolean {
        if (browserOpenedVersion == versionTag) return false
        browserOpenedVersion = versionTag
        ioScope.launch {
            runCatching { dataStore.edit { it[KEY_BROWSER_OPENED_VERSION] = versionTag } }
                .onFailure { Log.w(TAG, "persist browser opened version failed", it) }
        }
        return true
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
     * tag 是否指向预发布版本 —— 只看 tag 里有没有预发布后缀（"1.0.3-beta" / "1.0.3-rc.1"）。
     *
     * 网页 feed 不带 GitHub 的 prerelease 标记，兜底时只能靠这个判断；
     * 正式通道据此把预发布排除在「有更新」之外。
     */
    private fun isPreReleaseTag(tag: String): Boolean =
        tag.removePrefix("v").contains('-')

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

        /** 已经替用户自动唤起过浏览器的版本 tag（同版本只跳一次）。 */
        private val KEY_BROWSER_OPENED_VERSION = stringPreferencesKey("browser_opened_version")

        private const val CHECK_INTERVAL_DAILY_MS = 24 * 60 * 60 * 1000L   // 24 小时（默认）
        private const val CHECK_INTERVAL_WEEKLY_MS = 7 * CHECK_INTERVAL_DAILY_MS // 每周

        /** 「更新记录」默认展示条数。 */
        private const val DEFAULT_HISTORY_LIMIT = 20
    }
}
