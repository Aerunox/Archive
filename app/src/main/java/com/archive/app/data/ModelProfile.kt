package com.archive.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 一个大模型接入配置，可来自不同平台（智谱 / 通义 / OpenAI / 自建…）。
 *
 * 一个档案只有**一个模型**：想用不同模型跑不同任务，就建不同档案并在「用途分配」里指派。
 * 档案只描述「怎么连上这个模型」；提示词、采样参数、推理与联网开关是全局的，
 * 对所有档案统一生效（改一处即可，不用每个档案抄一遍）。
 */
@Serializable
data class ModelProfile(
    val id: String,
    /** 用户起的名字，如「智谱」「硅基流动」。 */
    val name: String,
    val url: String = "",
    val apiKey: String = "",
    /** 该档案的模型（被指派到什么用途，就为什么任务服务）。 */
    val model: String = "",
    val apiType: ApiType = ApiType.CHAT_COMPLETION,
) {
    /** 档案卡上的一行摘要。 */
    fun summary(): String = model.ifBlank { "（未填模型）" }
}

/** 档案的用途。 */
enum class AiPurpose(val label: String) {
    /** 帖子标题与正文的翻译。 */
    TRANSLATION("翻译帖子"),

    /** AI 摘要与图片问答（都要「看图」）。 */
    VISION("识别图片（摘要 / 问答）");

    companion object {
        fun fromName(name: String?): AiPurpose = entries.firstOrNull { it.name == name } ?: TRANSLATION
    }
}

private val profileJson = Json { ignoreUnknownKeys = true }

fun encodeProfiles(profiles: List<ModelProfile>): String = profileJson.encodeToString(profiles)

/** 容错解析：坏数据宁可当空列表，也别让设置页打不开。 */
fun decodeProfiles(text: String?): List<ModelProfile> =
    text?.takeIf { it.isNotBlank() }
        ?.let { runCatching { profileJson.decodeFromString<List<ModelProfile>>(it) }.getOrNull() }
        .orEmpty()

/**
 * 某个用途**实际生效**的配置：端点信息（url / key / 模型 / 接口类型）换成指派档案的，
 * 其余（提示词、采样参数、推理与联网开关）保持全局。
 *
 * - 没有任何档案时原样返回（老用户升级后不用重填，旧的单套配置继续生效）；
 * - 指派的档案被删掉时退回第一个档案。
 *
 * 客户端拿到返回值后照常使用 —— 这就是「多模型按用途调用」的全部接入成本。
 */
fun TranslationSettings.forPurpose(purpose: AiPurpose): TranslationSettings {
    if (profiles.isEmpty()) return this
    val id = when (purpose) {
        AiPurpose.TRANSLATION -> translationProfileId
        AiPurpose.VISION -> visionProfileId
    }
    val profile = profiles.firstOrNull { it.id == id } ?: profiles.first()
    return copy(
        url = profile.url,
        apiKey = profile.apiKey,
        model = profile.model,
        // 档案只有一个模型：清掉全局的旧「视觉模型」值，图片任务自然退用档案的模型
        visionModel = "",
        apiType = profile.apiType,
    )
}
