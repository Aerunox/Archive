package com.archive.app.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * `tags` 字段在真实数据里有两种形态（与 OpenAPI spec 不符，实测 9/10 是字符串）：
 *
 *  - JSON 数组：`["NSFW","Benefits"]`（spec 声明的形态）
 *  - Postgres 数组字面量字符串：`{"Atelier Ryza",Benefits,NSFW}`（多数帖子的实际形态）
 *
 * 该序列化器两种都接受，null/空串/`{}` 一律得到空列表，避免详情页整页解析失败。
 */
object FlexStringListSerializer : KSerializer<List<String>> {

    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            is JsonNull -> emptyList()
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString || !p.isString }?.content }
            is JsonPrimitive -> parsePostgresArray(element.content)
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(delegate, value)
    }

    /**
     * 解析 Postgres 数组字面量：`{a,"b,c",d}` → ["a","b,c","d"]。
     * 支持双引号元素、引号内的逗号、`\"` 与 `\\` 转义；未加引号的 `NULL` 视为空。
     */
    fun parsePostgresArray(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val inner = if (trimmed.length >= 2 && trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
        if (inner.isBlank()) return emptyList()

        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var escaped = false

        fun flush(quoted: Boolean) {
            val value = current.toString()
            current.clear()
            if (quoted) {
                out += value
            } else {
                val bare = value.trim()
                if (bare.isNotEmpty() && !bare.equals("NULL", ignoreCase = true)) out += bare
            }
        }

        var elementQuoted = false
        for (c in inner) {
            when {
                escaped -> {
                    current.append(c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                c == '"' -> {
                    if (inQuotes) {
                        flush(quoted = true)
                        inQuotes = false
                        elementQuoted = false
                    } else {
                        inQuotes = true
                        elementQuoted = true
                    }
                }
                c == ',' && !inQuotes -> flush(quoted = elementQuoted)
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty() || elementQuoted) flush(quoted = elementQuoted)
        return out
    }
}
