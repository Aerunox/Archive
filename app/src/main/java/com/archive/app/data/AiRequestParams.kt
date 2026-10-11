package com.archive.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 思考等级的展示名，下标即 [TranslationSettings.thinkingLevel]。 */
val THINKING_LEVELS = listOf("低（更快）", "中（默认）", "高（更深入）")

/** 等级 → OpenAI 系的 `reasoning_effort` 取值（协议层也会用）。 */
internal fun effortOf(level: Int): String = when (level) {
    0 -> "low"
    2 -> "high"
    else -> "medium"
}

/** 等级 → 思考预算 token（通义 `thinking_budget` / Anthropic `budget_tokens` / Gemini `thinkingBudget`）。 */
internal fun budgetOf(level: Int): Int = when (level) {
    0 -> 1024
    2 -> 16384
    else -> 4096
}

/**
 * 深度思考 / 思考等级 / 联网搜索 → 请求体里的额外字段。
 *
 * 各家命名并不统一，这里是「按开关追加」的务实做法，一次把主流两家的字段都带上
 * （不认识的字段通常被忽略；真被拒了就关掉对应开关，用「测试」按钮能立刻验证）：
 *
 * - 深度思考：`enable_thinking`（通义 DashScope 兼容模式）+ `thinking.type`（智谱 GLM）；
 * - 思考等级：`reasoning_effort`（OpenAI o 系列与兼容网关）+ `thinking_budget`（通义）；
 * - 联网搜索：`enable_search`（通义）+ 智谱的内置 `web_search` 工具。
 *
 * **开关全关时返回空对象**，也就是一个额外字段都不带 —— 这是默认配置能在任何端点上
 * 跑通的前提，请不要改成「总是下发 false」。
 */
fun TranslationSettings.advancedParams(): JsonObject = buildJsonObject {
    if (deepThinking) {
        put("enable_thinking", true)
        putJsonObject("thinking") { put("type", "enabled") }
        put("reasoning_effort", effortOf(thinkingLevel))
        put("thinking_budget", budgetOf(thinkingLevel))
    }
    if (webSearch) {
        put("enable_search", true)
        putJsonArray("tools") {
            addJsonObject {
                put("type", "web_search")
                putJsonObject("web_search") { put("enable", true) }
            }
        }
    }
}

/**
 * 当前生效的「推理与联网」开关（人类可读），用于测试结果里说明本次到底带了哪些开关。
 * 与 [advancedParams] 一一对应，改参数时记得同步改这里。
 */
fun TranslationSettings.appliedFeatureLabels(): List<String> = buildList {
    if (deepThinking) {
        add("深度思考·" + THINKING_LEVELS.getOrElse(thinkingLevel) { THINKING_LEVELS[1] })
    }
    if (webSearch) add("联网搜索")
}

/** 把 [advancedParams] 合进已建好的请求体（返回 JsonObject 以便 toString 仍是合法 JSON）。 */
fun JsonObject.withAdvancedParams(settings: TranslationSettings): JsonObject =
    JsonObject(this + settings.advancedParams())
