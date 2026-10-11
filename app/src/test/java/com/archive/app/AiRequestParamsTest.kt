package com.archive.app

import com.archive.app.data.TranslationSettings
import com.archive.app.data.advancedParams
import com.archive.app.data.appliedFeatureLabels
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「推理与联网」开关 → 请求体字段的回归测试。
 *
 * 钉住两条约定：
 * 1. **开关全关时必须一个字段都不带**（空对象）—— 部分端点会直接拒绝不认识的参数（400），
 *    默认配置必须能在任意端点上跑通。若哪天被改成「总是下发 false」，所有没开这些功能的
 *    用户都会开始报错；
 * 2. 开着哪个开关，就必须带上对应字段 —— 连通性测试走的是同一段拼装代码，
 *    所以「测试通过」才等于「这些字段被端点接受」。
 */
class AiRequestParamsTest {

    private fun params(
        deepThinking: Boolean = false,
        thinkingLevel: Int = 1,
        webSearch: Boolean = false,
    ) = TranslationSettings(
        deepThinking = deepThinking,
        thinkingLevel = thinkingLevel,
        webSearch = webSearch,
    ).advancedParams()

    private fun TranslationSettings.labels() = appliedFeatureLabels()

    @Test
    fun `all switches off sends nothing`() {
        assertTrue("关闭时不应带任何字段", params().isEmpty())
        assertTrue("关闭时也不该有「已带」说明", TranslationSettings().labels().isEmpty())
    }

    @Test
    fun `deep thinking adds fields for both vendors`() {
        val p = params(deepThinking = true, thinkingLevel = 2)

        assertEquals(true, p["enable_thinking"]!!.jsonPrimitive.boolean)
        assertEquals("enabled", p["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("high", p["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals(16384, p["thinking_budget"]!!.jsonPrimitive.int)
        assertNull("没开联网就不该带检索字段", p["enable_search"])
    }

    @Test
    fun `thinking level maps to effort and budget`() {
        val low = params(deepThinking = true, thinkingLevel = 0)
        val middle = params(deepThinking = true, thinkingLevel = 1)

        assertEquals("low", low["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals(1024, low["thinking_budget"]!!.jsonPrimitive.int)
        assertEquals("medium", middle["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals(4096, middle["thinking_budget"]!!.jsonPrimitive.int)
    }

    @Test
    fun `web search adds search flag and builtin tool`() {
        val p = params(webSearch = true)

        assertEquals(true, p["enable_search"]!!.jsonPrimitive.boolean)
        val tool = p["tools"]!!.jsonArray.first().jsonObject
        assertEquals("web_search", tool["type"]!!.jsonPrimitive.content)
        assertEquals(true, tool["web_search"]!!.jsonObject["enable"]!!.jsonPrimitive.boolean)
        assertNull("没开思考就不该带思考字段", p["enable_thinking"])
    }

    /** 两个开关同时打开时互不干扰：一次请求里两类字段都在。 */
    @Test
    fun `both switches can be on together`() {
        val p = params(deepThinking = true, webSearch = true)

        assertEquals(true, p["enable_thinking"]!!.jsonPrimitive.boolean)
        assertEquals(true, p["enable_search"]!!.jsonPrimitive.boolean)
    }

    /** 测试结果里要能说明「本次带了哪些开关」，等级名要与用户看到的一致。 */
    @Test
    fun `labels describe enabled switches`() {
        assertEquals(
            listOf("深度思考·高（更深入）", "联网搜索"),
            TranslationSettings(deepThinking = true, thinkingLevel = 2, webSearch = true).labels(),
        )
    }
}
