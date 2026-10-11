package com.archive.app

import com.archive.app.data.AiPurpose
import com.archive.app.data.ApiType
import com.archive.app.data.ModelProfile
import com.archive.app.data.TranslationSettings
import com.archive.app.data.decodeProfiles
import com.archive.app.data.encodeProfiles
import com.archive.app.data.forPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多档案与用途指派的解析规则（「按用途调用正确的模型」全靠这几条）。 */
class ModelProfileTest {

    private fun profile(
        id: String,
        model: String = "m-$id",
        type: ApiType = ApiType.CHAT_COMPLETION,
    ) = ModelProfile(
        id = id,
        name = "档案-$id",
        url = "https://$id.example.com/v1/chat/completions",
        apiKey = "key-$id",
        model = model,
        apiType = type,
    )

    private fun settings(
        profiles: List<ModelProfile>,
        translationId: String = "",
        visionId: String = "",
    ) = TranslationSettings(
        temperature = 0.7f,
        profiles = profiles,
        translationProfileId = translationId,
        visionProfileId = visionId,
    )

    @Test
    fun `assigned profile supplies endpoint per purpose`() {
        val a = profile("a")
        val b = profile("b", type = ApiType.ANTHROPIC)
        val s = settings(listOf(a, b), translationId = "a", visionId = "b")

        val forTranslation = s.forPurpose(AiPurpose.TRANSLATION)
        assertEquals("https://a.example.com/v1/chat/completions", forTranslation.url)
        assertEquals("m-a", forTranslation.model)
        assertEquals(ApiType.CHAT_COMPLETION, forTranslation.apiType)

        val forVision = s.forPurpose(AiPurpose.VISION)
        assertEquals("https://b.example.com/v1/chat/completions", forVision.url)
        assertEquals(ApiType.ANTHROPIC, forVision.apiType)
    }

    /** 全局旋钮不跟随档案：温度这类参数对所有用途统一。 */
    @Test
    fun `global knobs survive resolution`() {
        val s = settings(listOf(profile("a")), translationId = "a").forPurpose(AiPurpose.TRANSLATION)
        assertEquals(0.7f, s.temperature)
    }

    /** 指派的档案被删掉时退回第一个档案，而不是退回一套没人维护的旧配置。 */
    @Test
    fun `missing assignment falls back to first profile`() {
        val s = settings(listOf(profile("a"), profile("b")), translationId = "gone")
        assertEquals("m-a", s.forPurpose(AiPurpose.TRANSLATION).model)
    }

    @Test
    fun `no profiles means legacy config untouched`() {
        val legacy = TranslationSettings(url = "https://old.example.com", model = "old-model", temperature = 0.7f)
        val resolved = legacy.forPurpose(AiPurpose.VISION)
        assertEquals("https://old.example.com", resolved.url)
        assertEquals("old-model", resolved.model)
        assertEquals(0.7f, resolved.temperature)
    }

    /** 档案只有一个模型：解析时清掉全局旧「视觉模型」，图片任务自然退用档案的模型。 */
    @Test
    fun `resolution clears legacy vision model`() {
        val resolved = TranslationSettings(
            visionModel = "glm-4v-flash",
            profiles = listOf(profile("a")),
            visionProfileId = "a",
        ).forPurpose(AiPurpose.VISION)

        assertEquals("", resolved.visionModel)
        assertEquals("m-a", resolved.model)
    }

    @Test
    fun `profiles survive json round trip`() {
        val profiles = listOf(profile("a"), profile("b", type = ApiType.GEMINI))
        val decoded = decodeProfiles(encodeProfiles(profiles))

        assertEquals(profiles, decoded)
    }

    /** 坏数据宁可当空列表，也不能让设置页打不开。 */
    @Test
    fun `broken json decodes to empty list`() {
        assertTrue(decodeProfiles("not json").isEmpty())
        assertTrue(decodeProfiles(null).isEmpty())
    }
}
