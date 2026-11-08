package com.archive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.app.data.AiPurpose
import com.archive.app.data.ApiType
import com.archive.app.data.ModelProfile
import com.archive.app.data.TranslationClient
import com.archive.app.data.TranslationSettings
import com.archive.app.data.TranslationSettingsRepository
import com.archive.app.data.VisionClient
import com.archive.app.data.forPurpose
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppDropdownSetting
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.AppSliderSetting
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextField
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import com.archive.app.ui.design.appListModifier
import com.archive.app.ui.theme.LocalPurrColors
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 设置：模型档案（可来自不同平台）+ 用途指派 + 全局的提示词 / 采样 / 推理与联网开关。
 *
 * 数据模型见 `ModelProfile` / `AiPurpose`：
 * - 档案只描述「怎么连上某个模型」（地址 / Key / 模型 / 接口类型），可添加、编辑、删除；
 * - 每个用途（翻译帖子、识别图片）各自指派一个档案，调用侧用 `forPurpose` 取生效配置；
 * - 提示词、采样参数、推理与联网开关是全局的，改一处对所有档案生效。
 */
@HiltViewModel
class TranslationSettingsViewModel @Inject constructor(
    /** 测试结果消息需要本地化，所以这里要一个 Context（仅用于取字符串）。 */
    @ApplicationContext private val context: Context,
    private val repository: TranslationSettingsRepository,
    private val client: TranslationClient,
    private val vision: VisionClient,
) : ViewModel() {

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TranslationSettings(),
    )

    init {
        // 老版本的单套配置转成第一个档案（只跑一次，用户无感）
        viewModelScope.launch { repository.migrateLegacyIfNeeded() }
    }

    /** 一次测试的结果：成功与否 + 要展示的一行文字（含耗时与模型返回）。 */
    data class TestResult(val ok: Boolean, val message: String)

    val connTesting = MutableStateFlow(false)
    val connResult = MutableStateFlow<TestResult?>(null)
    val visionTesting = MutableStateFlow(false)
    val visionResult = MutableStateFlow<TestResult?>(null)

    fun upsertProfile(profile: ModelProfile) = save { repository.upsertProfile(profile) }
    fun deleteProfile(id: String) = save { repository.deleteProfile(id) }
    fun assignPurpose(purpose: AiPurpose, profileId: String) =
        save { repository.assignPurpose(purpose, profileId) }

    fun setPrompt(value: String) = save { repository.setPrompt(value) }
    fun setTemperature(value: Float) = save { repository.setTemperature(value) }
    fun setTopP(value: Float) = save { repository.setTopP(value) }
    fun setMaxTokens(value: Int) = save { repository.setMaxTokens(value) }
    fun setFrequencyPenalty(value: Float) = save { repository.setFrequencyPenalty(value) }
    fun setPresencePenalty(value: Float) = save { repository.setPresencePenalty(value) }
    fun setDeepThinking(value: Boolean) = save { repository.setDeepThinking(value) }
    fun setThinkingLevel(value: Int) = save { repository.setThinkingLevel(value) }
    fun setWebSearch(value: Boolean) = save { repository.setWebSearch(value) }
    fun setAutoTranslate(value: Boolean) = save { repository.setAutoTranslate(value) }

    /** 连通性测试：走「翻译帖子」指派的档案，与翻译同一段请求代码。 */
    fun testConnection() {
        if (connTesting.value) return
        connTesting.value = true
        connResult.value = null
        viewModelScope.launch {
            val elapsed = System.currentTimeMillis()
            val current = settings.value
            val outcome = client.testConnection(current.forPurpose(AiPurpose.TRANSLATION))
            val cost = System.currentTimeMillis() - elapsed
            val switchesOn = current.deepThinking || current.webSearch
            connResult.value = outcome.fold(
                onSuccess = { result ->
                    // 消息也走资源，否则切了界面语言这里仍是中文
                    TestResult(
                        ok = true,
                        message = buildString {
                            append(
                                if (result.applied.isEmpty()) {
                                    context.getString(
                                        com.archive.app.R.string.test_ok_no_switches,
                                        cost,
                                        result.reply.take(40),
                                    )
                                } else {
                                    // 测试与翻译走同一段拼装代码，所以这里能确认开关字段确实发出去了
                                    context.getString(
                                        com.archive.app.R.string.test_ok_with_switches,
                                        cost,
                                        result.applied.joinToString(" / "),
                                        result.reply.take(40),
                                    )
                                },
                            )
                            if (current.deepThinking && result.reasoningChars == 0) {
                                append(context.getString(com.archive.app.R.string.test_warn_no_reasoning))
                            }
                            if (current.webSearch && !result.searchSignal) {
                                append(context.getString(com.archive.app.R.string.test_warn_no_search))
                            }
                        },
                    )
                },
                onFailure = { error ->
                    val hint = if (switchesOn) {
                        context.getString(com.archive.app.R.string.test_switches_hint)
                    } else {
                        ""
                    }
                    TestResult(
                        false,
                        context.getString(com.archive.app.R.string.test_failed, error.describe()) + hint,
                    )
                },
            )
            connTesting.value = false
        }
    }

    /** 识图测试：走「识别图片」指派的档案，读现画图上的随机数字。 */
    fun testVision() {
        if (visionTesting.value) return
        visionTesting.value = true
        visionResult.value = null
        viewModelScope.launch {
            val elapsed = System.currentTimeMillis()
            val outcome = vision.testVision(settings.value.forPurpose(AiPurpose.VISION))
            val cost = System.currentTimeMillis() - elapsed
            visionResult.value = outcome.fold(
                onSuccess = { result ->
                    if (result.matched) {
                        TestResult(
                            ok = true,
                            message = context.getString(
                                com.archive.app.R.string.vision_ok,
                                cost,
                                result.readback.take(40),
                            ),
                        )
                    } else {
                        TestResult(
                            ok = false,
                            message = context.getString(
                                com.archive.app.R.string.vision_failed_unmatched,
                                result.expected,
                                result.readback.take(60),
                            ),
                        )
                    }
                },
                onFailure = {
                    TestResult(
                        false,
                        context.getString(com.archive.app.R.string.test_failed, it.describe()),
                    )
                },
            )
            visionTesting.value = false
        }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /** 失败信息尽量给出可读原因：抛出的文案本身就是给用户看的。 */
    private fun Throwable.describe(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}

@Composable
fun TranslationSettingsScreen(
    navController: NavHostController,
    viewModel: TranslationSettingsViewModel = hiltViewModel(),
) {
    val editorState = rememberAiSettingsEditorState()
    AppScaffold(topBar = {
        AppTopBar(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.ai_settings_title),
            onBack = { navController.popBackStack() },
        )
    }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .appListModifier(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AiSettingsColumn(viewModel, editorState) }
        }
    }
}

/**
 * 档案编辑态（编辑既有 / 新增一个档案）。
 *
 * 提出来单独持有，是因为「我 → 设置 → AI 功能」把各配置区块拆成了多个列表项，
 * 编辑态要跨列表项共用，不能放进某一个重组作用域里。
 */
class AiSettingsEditorState {
    var editing by mutableStateOf<ModelProfile?>(null)
    var adding by mutableStateOf(false)

    val editorOpen: Boolean get() = adding || editing != null

    fun edit(profile: ModelProfile) {
        editing = profile
        adding = false
    }

    fun startAdd() {
        adding = true
        editing = null
    }

    fun close() {
        adding = false
        editing = null
    }
}

@Composable
fun rememberAiSettingsEditorState(): AiSettingsEditorState = remember { AiSettingsEditorState() }

/**
 * AI 设置的全部配置区块，纵向排列成一个整体。
 *
 * 独立页（首页菜单「AI 设置」）与「我 → 设置 → AI 功能」共用这一份定义：
 * 两处只有外壳不同，配置项一行都不用重复写。
 *
 * 刻意保持「一个整体」而不是拆成多个 LazyColumn item：设置页的分类详情每帧都会
 * 重建分组列表，拆成多个 item 后这些 item 的身份每帧都变，列表会被不断重新布局、
 * 滚动位置被拉回顶部（实测滚不动）。
 */
@Composable
fun AiSettingsColumn(
    viewModel: TranslationSettingsViewModel,
    editorState: AiSettingsEditorState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AiOverviewCard(viewModel)
        AiAutoTranslateCard(viewModel)
        AiSectionTitle(androidx.compose.ui.res.stringResource(com.archive.app.R.string.section_profiles))
        AiProfilesCard(viewModel, editorState)
        if (editorState.editorOpen) {
            AiProfileEditorSection(viewModel, editorState)
        }
        AiSectionTitle(androidx.compose.ui.res.stringResource(com.archive.app.R.string.section_purposes))
        AiPurposesCard(viewModel)
        AiSectionTitle(androidx.compose.ui.res.stringResource(com.archive.app.R.string.section_test))
        AiTestCard(viewModel)
        AiSectionTitle(androidx.compose.ui.res.stringResource(com.archive.app.R.string.section_thinking))
        AiThinkingCard(viewModel)
        AiSectionTitle(androidx.compose.ui.res.stringResource(com.archive.app.R.string.section_prompt))
        AiPromptCard(viewModel)
        AiSectionTitle(androidx.compose.ui.res.stringResource(com.archive.app.R.string.section_sampling))
        AiSamplingCard(viewModel)
    }
}

@Composable
private fun AiSectionTitle(text: String) = AppSmallTitle(text)

/** 概览：配置状态胶囊 + 一句说明。 */
@Composable
fun AiOverviewCard(viewModel: TranslationSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = LocalPurrColors.current
    SettingsCard(verticalSpacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.ai_overview_title),
                style = AppTextStyle.MAIN,
                color = colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            StatusPill(
                text = androidx.compose.ui.res.stringResource(
                    if (settings.configured) com.archive.app.R.string.status_configured
                    else com.archive.app.R.string.status_pending,
                ),
                highlight = settings.configured,
            )
        }
        AppText(
            androidx.compose.ui.res.stringResource(com.archive.app.R.string.ai_overview_summary),
            style = AppTextStyle.SMALL,
            color = colors.textTertiary,
        )
    }
}

/** 自动翻译开关。 */
@Composable
fun AiAutoTranslateCard(viewModel: TranslationSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsCard(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalSpacing = 0.dp,
    ) {
        com.archive.app.ui.design.AppSwitchSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.auto_translate),
            checked = settings.autoTranslate,
            onCheckedChange = viewModel::setAutoTranslate,
            summary = androidx.compose.ui.res.stringResource(
                com.archive.app.R.string.auto_translate_summary,
            ),
        )
    }
}

/** 模型档案列表 + 「添加档案」按钮。 */
@Composable
fun AiProfilesCard(
    viewModel: TranslationSettingsViewModel,
    editorState: AiSettingsEditorState,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = LocalPurrColors.current
    SettingsCard(verticalSpacing = 10.dp) {
        if (settings.profiles.isEmpty()) {
            AppText(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.profiles_empty),
                style = AppTextStyle.SMALL,
                color = colors.textTertiary,
            )
        }
        settings.profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                onEdit = { editorState.edit(profile) },
                onDelete = { viewModel.deleteProfile(profile.id) },
            )
        }
        if (!editorState.editorOpen) {
            AppButton(
                text = androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_add),
                onClick = { editorState.startAdd() },
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
        }
    }
}

/** 档案编辑器（新增 / 编辑共用）。 */
@Composable
fun AiProfileEditorSection(
    viewModel: TranslationSettingsViewModel,
    editorState: AiSettingsEditorState,
) {
    ProfileEditorCard(
        initial = editorState.editing,
        onSave = {
            viewModel.upsertProfile(it)
            editorState.close()
        },
        onCancel = { editorState.close() },
    )
}

/** 用途指派：翻译帖子 / 识别图片 各选一个档案。 */
@Composable
fun AiPurposesCard(viewModel: TranslationSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = LocalPurrColors.current
    SettingsCard(verticalSpacing = 4.dp) {
        if (settings.profiles.isEmpty()) {
            AppText(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.purposes_empty),
                style = AppTextStyle.SMALL,
                color = colors.textTertiary,
            )
        }
        PurposeDropdown(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.purpose_translation),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.purpose_translation_summary),
            profiles = settings.profiles,
            assignedId = settings.translationProfileId,
            onSelect = { viewModel.assignPurpose(AiPurpose.TRANSLATION, it) },
        )
        PurposeDropdown(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.purpose_vision),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.purpose_vision_summary),
            profiles = settings.profiles,
            assignedId = settings.visionProfileId,
            onSelect = { viewModel.assignPurpose(AiPurpose.VISION, it) },
        )
    }
}

/** 连通性与识图测试。 */
@Composable
fun AiTestCard(viewModel: TranslationSettingsViewModel) {
    val connTesting by viewModel.connTesting.collectAsStateWithLifecycle()
    val connResult by viewModel.connResult.collectAsStateWithLifecycle()
    val visionTesting by viewModel.visionTesting.collectAsStateWithLifecycle()
    val visionResult by viewModel.visionResult.collectAsStateWithLifecycle()
    val colors = LocalPurrColors.current
    SettingsCard(verticalSpacing = 10.dp) {
        AppText(
            androidx.compose.ui.res.stringResource(com.archive.app.R.string.test_summary),
            style = AppTextStyle.SMALL,
            color = colors.textTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(
                text = androidx.compose.ui.res.stringResource(
                    if (connTesting) com.archive.app.R.string.test_running
                    else com.archive.app.R.string.test_connection,
                ),
                onClick = { viewModel.testConnection() },
                modifier = Modifier.weight(1f),
                primary = false,
                enabled = !connTesting,
            )
            AppButton(
                text = androidx.compose.ui.res.stringResource(
                    if (visionTesting) com.archive.app.R.string.test_running
                    else com.archive.app.R.string.test_vision,
                ),
                onClick = { viewModel.testVision() },
                modifier = Modifier.weight(1f),
                primary = false,
                enabled = !visionTesting,
            )
        }
        connResult?.let { TestResultLine(it) }
        visionResult?.let { TestResultLine(it) }
    }
}

/** 推理与联网开关。 */
@Composable
fun AiThinkingCard(viewModel: TranslationSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsCard(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalSpacing = 0.dp,
    ) {
        com.archive.app.ui.components.AiAdvancedSettingsSection(
            deepThinking = settings.deepThinking,
            thinkingLevel = settings.thinkingLevel,
            webSearch = settings.webSearch,
            onDeepThinkingChange = viewModel::setDeepThinking,
            onThinkingLevelChange = viewModel::setThinkingLevel,
            onWebSearchChange = viewModel::setWebSearch,
        )
    }
}

/** 翻译提示词。 */
@Composable
fun AiPromptCard(viewModel: TranslationSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsCard {
        SettingField(
            label = androidx.compose.ui.res.stringResource(com.archive.app.R.string.prompt_label),
            value = settings.prompt,
            onValueChange = viewModel::setPrompt,
            hint = androidx.compose.ui.res.stringResource(com.archive.app.R.string.prompt_hint),
            singleLine = false,
        )
    }
}

/** 采样参数：温度 / top_p / 最大 token / 频率与存在惩罚。 */
@Composable
fun AiSamplingCard(viewModel: TranslationSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsCard(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalSpacing = 8.dp,
    ) {
        AppSliderSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_temperature),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_temperature_summary),
            value = settings.temperature,
            onValueChange = viewModel::setTemperature,
            valueRange = 0f..2f,
            valueText = "%.2f".format(settings.temperature),
        )
        AppSliderSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_top_p),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_top_p_summary),
            value = settings.topP,
            onValueChange = viewModel::setTopP,
            valueRange = 0f..1f,
            valueText = "%.2f".format(settings.topP),
        )
        AppSliderSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_max_tokens),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_max_tokens_summary),
            value = settings.maxTokens.toFloat(),
            onValueChange = { viewModel.setMaxTokens(it.toInt()) },
            valueRange = 128f..8192f,
            valueText = settings.maxTokens.toString(),
        )
        AppSliderSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_freq),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_freq_summary),
            value = settings.frequencyPenalty,
            onValueChange = viewModel::setFrequencyPenalty,
            valueRange = -2f..2f,
            valueText = "%.2f".format(settings.frequencyPenalty),
        )
        AppSliderSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_pres),
            summary = androidx.compose.ui.res.stringResource(com.archive.app.R.string.sampling_pres_summary),
            value = settings.presencePenalty,
            onValueChange = viewModel::setPresencePenalty,
            valueRange = -2f..2f,
            valueText = "%.2f".format(settings.presencePenalty),
        )
    }
}

/** 一张档案卡：名字 + 模型摘要 + 编辑 / 删除。 */
@Composable
private fun ProfileRow(
    profile: ModelProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                profile.name,
                style = AppTextStyle.MAIN,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            AppText(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_edit),
                style = AppTextStyle.SMALL,
                color = colors.primary,
                modifier = Modifier
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            AppText(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_delete),
                style = AppTextStyle.SMALL,
                color = colors.error,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        AppText(
            profile.model.ifBlank {
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_no_model)
            },
            style = AppTextStyle.SMALL,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        AppText(
            profile.apiType.label,
            style = AppTextStyle.SMALL,
            color = colors.textTertiary,
        )
    }
}

/** 档案编辑器（内联展开）：一个档案一个模型；新增与编辑共用，必填项齐了才能保存。 */
@Composable
private fun ProfileEditorCard(
    initial: ModelProfile?,
    onSave: (ModelProfile) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalPurrColors.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var apiType by remember { mutableStateOf(initial?.apiType ?: ApiType.CHAT_COMPLETION) }
    var revealed by remember { mutableStateOf(false) }

    val valid = name.isNotBlank() && url.isNotBlank() && model.isNotBlank()

    SettingsCard(verticalSpacing = 12.dp) {
        AppText(
            androidx.compose.ui.res.stringResource(
                if (initial == null) com.archive.app.R.string.profile_add
                else com.archive.app.R.string.profile_editor_edit,
            ),
            style = AppTextStyle.MAIN,
            color = colors.textPrimary,
        )
        AppTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_field_name),
        )
        AppTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_field_url),
            singleLine = false,
        )
        AppTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_field_key),
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                AppText(
                    if (revealed) {
                        androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_field_hide)
                    } else {
                        androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_field_show)
                    },
                    style = AppTextStyle.SMALL,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable { revealed = !revealed }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            },
        )
        AppTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_field_model),
        )
        AppDropdownSetting(
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.field_api_type),
            summary = apiType.hint,
            items = ApiType.LABELS,
            selectedIndex = apiType.ordinal,
            onSelect = { apiType = ApiType.entries[it] },
        )
        if (!valid) {
            AppText(
                androidx.compose.ui.res.stringResource(com.archive.app.R.string.profile_invalid),
                style = AppTextStyle.SMALL,
                color = colors.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(
                text = androidx.compose.ui.res.stringResource(com.archive.app.R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                primary = false,
            )
            AppButton(
                text = androidx.compose.ui.res.stringResource(com.archive.app.R.string.action_save),
                onClick = {
                    val base = initial ?: ModelProfile(id = UUID.randomUUID().toString(), name = "")
                    onSave(
                        base.copy(
                            name = name.trim(),
                            url = url.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                            apiType = apiType,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = valid,
            )
        }
    }
}

/** 用途下拉：选项是指派档案的名字；还没有档案时给占位文案。 */
@Composable
private fun PurposeDropdown(
    title: String,
    summary: String,
    profiles: List<ModelProfile>,
    assignedId: String,
    onSelect: (String) -> Unit,
) {
    val placeholder = androidx.compose.ui.res.stringResource(com.archive.app.R.string.purpose_placeholder)
    val items = if (profiles.isEmpty()) listOf(placeholder) else profiles.map { it.name }
    val selected = profiles.indexOfFirst { it.id == assignedId }.let { if (it >= 0) it else 0 }
    AppDropdownSetting(
        title = title,
        summary = summary,
        items = items,
        selectedIndex = selected,
        onSelect = { index -> profiles.getOrNull(index)?.let { onSelect(it.id) } },
    )
}

/** 分组卡片：统一圆角、底色与内边距，组内项间距固定。 */
@Composable
private fun SettingsCard(
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    verticalSpacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPurrColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

/**
 * 一个配置项：输入框（label 作为空态占位）+ 下方一行说明。
 * [masked] 为真时默认打码，右侧提供「显示 / 隐藏」。
 */
@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    singleLine: Boolean = true,
    masked: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    val colors = LocalPurrColors.current

    val revealToggle: (@Composable () -> Unit)? = if (!masked) {
        null
    } else {
        {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { revealed = !revealed }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                AppText(
                    androidx.compose.ui.res.stringResource(
                        if (revealed) com.archive.app.R.string.profile_field_hide
                        else com.archive.app.R.string.profile_field_show,
                    ),
                    style = AppTextStyle.SMALL,
                    color = colors.primary,
                )
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
            singleLine = singleLine,
            visualTransformation = if (masked && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailing = revealToggle,
        )
        AppText(
            hint,
            style = AppTextStyle.SMALL,
            color = colors.textTertiary,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

/** 测试结果一行：成功用主题色、失败用错误色；可长按选中，便于复制报错信息。 */
@Composable
private fun TestResultLine(result: TranslationSettingsViewModel.TestResult) {
    val colors = LocalPurrColors.current
    SelectionContainer {
        AppText(
            (if (result.ok) "✓ " else "✗ ") + result.message,
            style = AppTextStyle.SMALL,
            color = if (result.ok) colors.primary else colors.error,
        )
    }
}

/** 配置状态胶囊：已配置用主题色淡底，未配置用中性底。 */
@Composable
private fun StatusPill(text: String, highlight: Boolean) {
    val colors = LocalPurrColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (highlight) colors.primary.copy(alpha = 0.15f) else colors.surfaceContainerHigh,
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        AppText(
            text,
            style = AppTextStyle.SMALL,
            color = if (highlight) colors.primary else colors.textTertiary,
        )
    }
}
