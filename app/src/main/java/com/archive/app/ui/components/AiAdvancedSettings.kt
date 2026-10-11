package com.archive.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.archive.app.R
import com.archive.app.ui.design.AppDropdownSetting
import com.archive.app.ui.design.AppSwitchSetting

/**
 * 推理与联网：深度思考开关、思考等级、联网搜索开关。
 *
 * 三者**默认全关**，且关闭时不下发任何相关字段 —— 各家命名不统一（通义
 * enable_thinking / thinking_budget / enable_search，智谱 thinking.type +
 * 内置 web_search 工具，OpenAI 系 reasoning_effort），而部分端点会直接拒绝
 * 不认识的参数（400）。只有主动打开时才把对应字段拼进请求体，
 * 拼装规则集中在 TranslationSettings.advancedParams()。
 *
 * 文案走资源字符串，跟随「设置 - 界面语言」。
 */
@Composable
fun AiAdvancedSettingsSection(
    deepThinking: Boolean,
    thinkingLevel: Int,
    webSearch: Boolean,
    onDeepThinkingChange: (Boolean) -> Unit,
    onThinkingLevelChange: (Int) -> Unit,
    onWebSearchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 等级名走资源，跟随界面语言；下标与 TranslationSettings.thinkingLevel 对应
    val low = stringResource(R.string.ai_thinking_low)
    val middle = stringResource(R.string.ai_thinking_middle)
    val high = stringResource(R.string.ai_thinking_high)
    val levels = remember(low, middle, high) { listOf(low, middle, high) }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppSwitchSetting(
            title = stringResource(R.string.ai_switch_deep),
            checked = deepThinking,
            onCheckedChange = onDeepThinkingChange,
            summary = stringResource(R.string.ai_switch_deep_summary),
        )
        AppDropdownSetting(
            title = stringResource(R.string.ai_thinking_level),
            summary = stringResource(R.string.ai_thinking_level_summary),
            items = levels,
            selectedIndex = thinkingLevel.coerceIn(0, levels.lastIndex),
            onSelect = onThinkingLevelChange,
        )
        AppSwitchSetting(
            title = stringResource(R.string.ai_switch_web),
            checked = webSearch,
            onCheckedChange = onWebSearchChange,
            summary = stringResource(R.string.ai_switch_web_summary),
        )
    }
}