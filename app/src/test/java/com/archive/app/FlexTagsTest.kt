package com.archive.app

import com.archive.app.data.remote.FlexStringListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归：详情接口的 tags 既可能是 JSON 数组，也可能是 Postgres 数组字面量字符串
 * （实测 9/10 的帖子是后者），两种都必须解析成功，否则整个详情页报 JSON 解析错误。
 */
class FlexTagsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun decode(raw: String): List<String> =
        json.decodeFromString(FlexStringListSerializer, raw)

    @Test
    fun `postgres literal with quoted element containing comma`() {
        assertEquals(
            listOf("Atelier Ryza", "Benefits", "NSFW"),
            decode("\"{\\\"Atelier Ryza\\\",Benefits,NSFW}\""),
        )
    }

    @Test
    fun `postgres literal with unicode tags`() {
        assertEquals(
            listOf("Benefits", "Ikki Tousen", "NSFW", "一騎当千"),
            decode("\"{Benefits,\\\"Ikki Tousen\\\",NSFW,一騎当千}\""),
        )
    }

    @Test
    fun `plain postgres literal`() {
        assertEquals(listOf("NSFW", "Notice"), decode("\"{NSFW,Notice}\""))
    }

    @Test
    fun `json array form still works`() {
        assertEquals(listOf("NSFW", "Benefits"), decode("""["NSFW","Benefits"]"""))
    }

    @Test
    fun `empty shapes yield empty list`() {
        assertEquals(emptyList<String>(), decode("null"))
        assertEquals(emptyList<String>(), decode("\"\""))
        assertEquals(emptyList<String>(), decode("\"{}\""))
    }

    @Test
    fun `escaped quote inside an element`() {
        assertEquals(listOf("a\"b", "c"), decode("\"{\\\"a\\\\\\\"b\\\",c}\""))
    }
}
