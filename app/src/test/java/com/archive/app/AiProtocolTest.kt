package com.archive.app

import com.archive.app.data.ApiType
import com.archive.app.data.TranslationSettings
import com.archive.app.data.buildRequest
import com.archive.app.data.parseReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 接口类型（`ApiType`）的回归测试：请求体关键字段、鉴权头、URL 里的 `{model}` 替换，以及响应解析。
 *
 * 这些形状写错的表现是「整条链路不可用」，而且往往只有报错信息、看不出哪里错，
 * 所以四种类型各钉一组；解析再各钉一组，避免「能用但读不出内容」。
 */
class AiProtocolTest {

    private fun settings(type: ApiType, url: String = "https://example.com/v1/x") = TranslationSettings(
        url = url,
        apiKey = "sk-test",
        model = "test-model",
        apiType = type,
    )

    private fun build(
        type: ApiType,
        url: String = "https://example.com/v1/x",
        image: String? = null,
        history: List<com.archive.app.data.AiMessage> = emptyList(),
    ): Pair<Map<String, String>, JsonObject> {
        val s = settings(type, url)
        val built = s.apiType.buildRequest(
            settings = s,
            model = "test-model",
            system = "SYS",
            history = history,
            prompt = "PROMPT",
            imageDataUrl = image,
        )
        return built.headers to Json.parseToJsonElement(built.body).jsonObject
    }

    // ── 请求拼装 ──

    @Test
    fun `chat completion uses messages with bearer auth`() {
        val (headers, body) = build(ApiType.CHAT_COMPLETION)

        assertEquals("Bearer sk-test", headers["Authorization"])
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("SYS", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        // 纯文本时不发 content 数组，兼容性最好
        assertEquals("PROMPT", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertNotNull(body["max_tokens"])
    }

    @Test
    fun `chat completion carries image as image_url part`() {
        val (_, body) = build(ApiType.CHAT_COMPLETION, image = "data:image/jpeg;base64,QUJD")

        val content = body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image_url", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,QUJD",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `responses uses instructions and input`() {
        val (headers, body) = build(ApiType.RESPONSES)

        assertEquals("Bearer sk-test", headers["Authorization"])
        assertEquals("SYS", body["instructions"]!!.jsonPrimitive.content)
        val input = body["input"]!!.jsonArray
        val part = input.last().jsonObject["content"]!!.jsonArray.first().jsonObject
        assertEquals("input_text", part["type"]!!.jsonPrimitive.content)
        assertEquals("PROMPT", part["text"]!!.jsonPrimitive.content)
        assertNotNull(body["max_output_tokens"])
        assertNull("Responses 没有 messages", body["messages"])
    }

    @Test
    fun `anthropic uses x-api-key and base64 source`() {
        val (headers, body) = build(ApiType.ANTHROPIC, image = "data:image/jpeg;base64,QUJD")

        assertEquals("sk-test", headers["x-api-key"])
        assertEquals("2023-06-01", headers["anthropic-version"])
        assertNull(headers["Authorization"])
        assertEquals("SYS", body["system"]!!.jsonPrimitive.content)
        val source = body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
            .last().jsonObject["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("QUJD", source["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `gemini substitutes model into url and uses inline_data`() {
        val url = "https://example.com/v1beta/models/{model}:generateContent"
        val s = settings(ApiType.GEMINI, url)
        val built = s.apiType.buildRequest(
            settings = s,
            model = "gemini-2.0-flash",
            system = "SYS",
            history = emptyList(),
            prompt = "PROMPT",
            imageDataUrl = "data:image/jpeg;base64,QUJD",
        )

        assertEquals("https://example.com/v1beta/models/gemini-2.0-flash:generateContent", built.url)
        assertEquals("sk-test", built.headers["x-goog-api-key"])
        val body = Json.parseToJsonElement(built.body).jsonObject
        val parts = body["contents"]!!.jsonArray.last().jsonObject["parts"]!!.jsonArray
        assertEquals("PROMPT", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(
            "QUJD",
            parts[1].jsonObject["inline_data"]!!.jsonObject["data"]!!.jsonPrimitive.content,
        )
        assertNotNull(body["generationConfig"]!!.jsonObject["maxOutputTokens"])
    }

    @Test
    fun `history is replayed per protocol`() {
        val history = listOf(
            com.archive.app.data.AiMessage(fromUser = true, text = "Q1"),
            com.archive.app.data.AiMessage(fromUser = false, text = "A1"),
        )

        val chat = build(ApiType.CHAT_COMPLETION, history = history).second["messages"]!!.jsonArray
        assertEquals("Q1", chat[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("A1", chat[2].jsonObject["content"]!!.jsonPrimitive.content)

        val gemini = build(ApiType.GEMINI, history = history).second["contents"]!!.jsonArray
        assertEquals("user", gemini[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("model", gemini[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    // ── 响应解析 ──

    private fun replyOf(type: ApiType, json: String) =
        type.parseReply(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `parses chat completion reply with reasoning and search signals`() {
        val reply = replyOf(
            ApiType.CHAT_COMPLETION,
            """
                {"choices":[{"message":{"role":"assistant","content":"pong",
                 "reasoning_content":"想了一下"}}],"search_info":{"count":2}}
            """.trimIndent(),
        )

        assertEquals("pong", reply.text)
        assertEquals(4, reply.reasoningChars)
        assertEquals(true, reply.searchSignal)
    }

    @Test
    fun `parses responses reply from output array`() {
        val reply = replyOf(
            ApiType.RESPONSES,
            """
                {"output":[
                  {"type":"reasoning","summary":[{"type":"summary_text","text":"想一想"}]},
                  {"type":"message","content":[{"type":"output_text","text":"pong"}]},
                  {"type":"web_search_call","id":"ws_1"}]}
            """.trimIndent(),
        )

        assertEquals("pong", reply.text)
        assertEquals(3, reply.reasoningChars)
        assertEquals(true, reply.searchSignal)
    }

    @Test
    fun `parses anthropic reply with thinking block`() {
        val reply = replyOf(
            ApiType.ANTHROPIC,
            """
                {"content":[
                  {"type":"thinking","thinking":"想了两下"},
                  {"type":"text","text":"pong"}]}
            """.trimIndent(),
        )

        assertEquals("pong", reply.text)
        assertEquals(4, reply.reasoningChars)
    }

    @Test
    fun `parses gemini reply skipping thought parts`() {
        val reply = replyOf(
            ApiType.GEMINI,
            """
                {"candidates":[{"content":{"parts":[
                   {"text":"内部思考","thought":true},
                   {"text":"pong"}]},"groundingMetadata":{"webSearchQueries":["x"]}}]}
            """.trimIndent(),
        )

        assertEquals("pong", reply.text)
        assertEquals(4, reply.reasoningChars)
        assertEquals(true, reply.searchSignal)
    }

    /** 没有正文时返回 null，由调用方决定报什么错（而不是抛在这里）。 */
    @Test
    fun `missing text yields null instead of throwing`() {
        assertNull(replyOf(ApiType.CHAT_COMPLETION, """{"choices":[{"message":{"content":""}}]}""").text)
        assertNull(replyOf(ApiType.ANTHROPIC, """{"content":[]}""").text)
    }
}
