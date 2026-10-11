package com.archive.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 翻译/AI 配置单独一个 DataStore：与站点设置分开，互不影响
private val Context.translationDataStore by
    preferencesDataStore(name = "archive_translation")

/**
 * 帖子翻译用的 AI 接口配置（OpenAI 兼容的 chat/completions）。
 *
 * 全部参数都可由用户自定义，默认值取「翻译」这个任务比较稳的一组。
 */
data class TranslationSettings(
    /** chat/completions 的完整地址，例如 https://api.openai.com/v1/chat/completions */
    val url: String = "",
    val apiKey: String = "",
    val model: String = "",

    /**
     * 图片理解（AI 摘要 / 图片问答）用的**多模态模型名**；留空则沿用 [model]。
     * 与翻译分开的原因：翻译用纯文本模型通常更快更便宜，图片必须换成视觉模型。
     */
    val visionModel: String = "",

    /**
     * 接口类型：OpenAI Chat Completion / Responses、Anthropic Messages、Gemini generateContent。
     * 决定请求体怎么拼、响应怎么解、鉴权用哪个头（实现见 `AiProtocol`）。
     */
    val apiType: ApiType = ApiType.CHAT_COMPLETION,

    // ── 多档案 ──
    // 档案列表为空时，上面的单套配置继续生效（老用户升级零成本）；
    // 一旦开始管理档案，端点信息就以指派的档案为准（见 ModelProfile.forPurpose）。

    /** 模型档案列表，可来自不同平台。 */
    val profiles: List<ModelProfile> = emptyList(),
    /** 「翻译帖子」用途指派的档案 id。 */
    val translationProfileId: String = "",
    /** 「识别图片（摘要 / 问答）」用途指派的档案 id。 */
    val visionProfileId: String = "",

    /** 进入帖子时自动开始翻译（默认关闭；开关持久化保存）。 */
    val autoTranslate: Boolean = false,

    /** 界面语言：只影响应用界面文案（资源字符串），默认跟随系统；帖子内容不翻译。 */
    val language: String = AppLanguage.SYSTEM.name,

    // ── 推理与联网 ──
    // 三个开关默认全关，且关闭时**不下发任何相关字段**：部分端点会直接拒绝不认识的
    // 参数（400），默认配置必须能在任意端点上跑通。拼装逻辑见 advancedParams()。

    /** 深度思考：让模型先推理再作答（通义 `enable_thinking` / 智谱 `thinking.type`）。 */
    val deepThinking: Boolean = false,

    /** 思考等级：0 低 / 1 中 / 2 高（映射到 reasoning_effort 与 thinking_budget）。 */
    val thinkingLevel: Int = 1,

    /** 联网搜索（通义 `enable_search` / 智谱内置 web_search 工具）。 */
    val webSearch: Boolean = false,
    /** 系统提示词：翻译成什么语言、用什么语气，都在这里约束 */
    val prompt: String = DEFAULT_PROMPT,
    val temperature: Float = 0.3f,
    val topP: Float = 1f,
    val maxTokens: Int = 1200,
    val frequencyPenalty: Float = 0f,
    val presencePenalty: Float = 0f,
) {
    /**
     * 是否已配置到「能干活」的程度：
     * - 管理了档案：任一档案有地址且有模型；
     * - 没管理档案：旧的单套配置有地址与模型。
     */
    val configured: Boolean
        get() = profiles.any { it.url.isNotBlank() && it.model.isNotBlank() } ||
            (url.isNotBlank() && model.isNotBlank())

    companion object {
        const val DEFAULT_PROMPT =
            "你是翻译助手。把用户发来的内容翻译成简体中文，只输出译文本身：" +
                "不要解释、不要加引号、不要重复原文，保留原有换行与段落。"
    }
}

@Singleton
class TranslationSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val URL = stringPreferencesKey("ai_url")
        val KEY = stringPreferencesKey("ai_key")
        val MODEL = stringPreferencesKey("ai_model")
        val VISION_MODEL = stringPreferencesKey("ai_vision_model")
        val DEEP_THINKING = androidx.datastore.preferences.core.booleanPreferencesKey("ai_deep_thinking")
        val THINKING_LEVEL = intPreferencesKey("ai_thinking_level")
        val WEB_SEARCH = androidx.datastore.preferences.core.booleanPreferencesKey("ai_web_search")
        val API_TYPE = stringPreferencesKey("ai_api_type")
        val PROFILES = stringPreferencesKey("ai_profiles")
        val PURPOSE_TRANSLATION = stringPreferencesKey("ai_purpose_translation")
        val PURPOSE_VISION = stringPreferencesKey("ai_purpose_vision")
        /** 档案结构迁移版本：1 = 旧单套配置转档案；2 = 档案只保留一个模型字段。 */
        val PROFILE_MIGRATION = intPreferencesKey("ai_profile_migration")
        val AUTO_TRANSLATE = androidx.datastore.preferences.core.booleanPreferencesKey("ai_auto_translate")
        val LANGUAGE = stringPreferencesKey("ai_language")
        val PROMPT = stringPreferencesKey("ai_prompt")
        val TEMPERATURE = floatPreferencesKey("ai_temperature")
        val TOP_P = floatPreferencesKey("ai_top_p")
        val MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val FREQ_PENALTY = floatPreferencesKey("ai_frequency_penalty")
        val PRES_PENALTY = floatPreferencesKey("ai_presence_penalty")
    }

    val settings: Flow<TranslationSettings> = context.translationDataStore.data.map { p ->
        val defaults = TranslationSettings()
        // 语言镜像同步：Activity.attachBaseContext 必须**同步**拿到语言（DataStore 是异步的）。
        // 只在两者不一致时写，避免每次设置变更都落盘；也让历史数据在启动后自动收敛到镜像。
        val storedLanguage = AppLanguage.fromName(p[Keys.LANGUAGE])
        if (storedLanguage != context.readLanguageMirror()) {
            context.writeLanguageMirror(storedLanguage)
        }
        TranslationSettings(
            url = p[Keys.URL] ?: defaults.url,
            apiKey = p[Keys.KEY] ?: defaults.apiKey,
            model = p[Keys.MODEL] ?: defaults.model,
            visionModel = p[Keys.VISION_MODEL] ?: defaults.visionModel,
            deepThinking = p[Keys.DEEP_THINKING] ?: defaults.deepThinking,
            thinkingLevel = p[Keys.THINKING_LEVEL] ?: defaults.thinkingLevel,
            webSearch = p[Keys.WEB_SEARCH] ?: defaults.webSearch,
            // 存的是枚举名，取不到就退回默认类型
            apiType = ApiType.fromName(p[Keys.API_TYPE]),
            profiles = decodeProfiles(p[Keys.PROFILES]),
            translationProfileId = p[Keys.PURPOSE_TRANSLATION] ?: defaults.translationProfileId,
            visionProfileId = p[Keys.PURPOSE_VISION] ?: defaults.visionProfileId,
            autoTranslate = p[Keys.AUTO_TRANSLATE] ?: defaults.autoTranslate,
            language = p[Keys.LANGUAGE] ?: defaults.language,
            prompt = (p[Keys.PROMPT] ?: defaults.prompt).ifBlank { defaults.prompt },
            temperature = p[Keys.TEMPERATURE] ?: defaults.temperature,
            topP = p[Keys.TOP_P] ?: defaults.topP,
            maxTokens = p[Keys.MAX_TOKENS] ?: defaults.maxTokens,
            frequencyPenalty = p[Keys.FREQ_PENALTY] ?: defaults.frequencyPenalty,
            presencePenalty = p[Keys.PRES_PENALTY] ?: defaults.presencePenalty,
        )
    }

    suspend fun setUrl(value: String) = edit { it[Keys.URL] = value.trim() }
    suspend fun setApiKey(value: String) = edit { it[Keys.KEY] = value.trim() }
    suspend fun setModel(value: String) = edit { it[Keys.MODEL] = value.trim() }

    suspend fun setVisionModel(value: String) = edit { it[Keys.VISION_MODEL] = value.trim() }

    suspend fun setDeepThinking(value: Boolean) = edit { it[Keys.DEEP_THINKING] = value }
    suspend fun setThinkingLevel(value: Int) = edit { it[Keys.THINKING_LEVEL] = value }
    suspend fun setWebSearch(value: Boolean) = edit { it[Keys.WEB_SEARCH] = value }

    /** 存枚举名而不是序号：以后增删类型不会让已存的配置串到别的类型上。 */
    suspend fun setApiType(value: ApiType) = edit { it[Keys.API_TYPE] = value.name }

    // ── 模型档案管理 ──

    /**
     * 档案结构的版本化迁移：
     *
     * - v1：老版本只有一套配置 → 转成名为「默认配置」的第一个档案，两个用途都指给它；
     * - v2：档案改为只保留一个模型字段。v1 时代档案里独立的「视觉模型」拆成独立档案
     *   （「默认配置（视觉）」），避免升级后图片任务悄悄退回文字模型。
     *
     * 已按旧布尔标记迁移过的用户视为 v1，从这里继续补 v2，不会覆盖他们改过的档案。
     */
    suspend fun migrateLegacyIfNeeded() {
        edit {
            val MIGRATION_VERSION = 2
            // 旧布尔标记视为 v1，防止重复执行 v1 覆盖用户已编辑过的档案
            val version = it[Keys.PROFILE_MIGRATION]
                ?: if (it[androidx.datastore.preferences.core.booleanPreferencesKey("ai_profiles_migrated")] == true) {
                    1
                } else {
                    0
                }
            if (version >= MIGRATION_VERSION) return@edit

            if (version < 1) {
                val legacyUrl = it[Keys.URL]
                if (!legacyUrl.isNullOrBlank()) {
                    val profile = ModelProfile(
                        id = "legacy",
                        name = "默认配置",
                        url = legacyUrl,
                        apiKey = it[Keys.KEY] ?: "",
                        model = it[Keys.MODEL] ?: "",
                        apiType = ApiType.fromName(it[Keys.API_TYPE]),
                    )
                    it[Keys.PROFILES] = encodeProfiles(listOf(profile))
                    it[Keys.PURPOSE_TRANSLATION] = profile.id
                    it[Keys.PURPOSE_VISION] = profile.id
                }
            }

            if (version < 2) {
                val legacyVision = it[Keys.VISION_MODEL]?.takeIf { value -> value.isNotBlank() }
                val profiles = decodeProfiles(it[Keys.PROFILES])
                if (legacyVision != null && profiles.any { p -> p.id == "legacy" }) {
                    val visionProfile = ModelProfile(
                        id = "legacy-vision",
                        name = "默认配置（视觉）",
                        url = it[Keys.URL] ?: "",
                        apiKey = it[Keys.KEY] ?: "",
                        model = legacyVision,
                        apiType = ApiType.fromName(it[Keys.API_TYPE]),
                    )
                    it[Keys.PROFILES] = encodeProfiles(profiles + visionProfile)
                    it[Keys.PURPOSE_VISION] = visionProfile.id
                }
            }

            it[Keys.PROFILE_MIGRATION] = MIGRATION_VERSION
        }
    }

    /** 新增或更新档案；新档案自动接管尚未指派的用途，避免「加了档案却没人用」。 */
    suspend fun upsertProfile(profile: ModelProfile) = edit {
        val current = decodeProfiles(it[Keys.PROFILES])
        it[Keys.PROFILES] = encodeProfiles(current.filterNot { p -> p.id == profile.id } + profile)
        if (it[Keys.PURPOSE_TRANSLATION] == null) it[Keys.PURPOSE_TRANSLATION] = profile.id
        if (it[Keys.PURPOSE_VISION] == null) it[Keys.PURPOSE_VISION] = profile.id
    }

    suspend fun deleteProfile(id: String) = edit {
        it[Keys.PROFILES] = encodeProfiles(decodeProfiles(it[Keys.PROFILES]).filterNot { p -> p.id == id })
        if (it[Keys.PURPOSE_TRANSLATION] == id) it[Keys.PURPOSE_TRANSLATION] = ""
        if (it[Keys.PURPOSE_VISION] == id) it[Keys.PURPOSE_VISION] = ""
    }

    suspend fun assignPurpose(purpose: AiPurpose, profileId: String) = edit {
        when (purpose) {
            AiPurpose.TRANSLATION -> it[Keys.PURPOSE_TRANSLATION] = profileId
            AiPurpose.VISION -> it[Keys.PURPOSE_VISION] = profileId
        }
    }

    suspend fun setAutoTranslate(value: Boolean) = edit { it[Keys.AUTO_TRANSLATE] = value }

    /** 界面语言：存枚举名，读不到就退回「跟随系统」。 */
    suspend fun setLanguage(value: AppLanguage) = edit { it[Keys.LANGUAGE] = value.name }
    suspend fun setPrompt(value: String) = edit { it[Keys.PROMPT] = value }
    suspend fun setTemperature(value: Float) = edit { it[Keys.TEMPERATURE] = value }
    suspend fun setTopP(value: Float) = edit { it[Keys.TOP_P] = value }
    suspend fun setMaxTokens(value: Int) = edit { it[Keys.MAX_TOKENS] = value }
    suspend fun setFrequencyPenalty(value: Float) = edit { it[Keys.FREQ_PENALTY] = value }
    suspend fun setPresencePenalty(value: Float) = edit { it[Keys.PRES_PENALTY] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.translationDataStore.edit(block)
    }
}
