package com.archive.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.archive.app.R
import com.archive.app.data.AppLanguage
import com.archive.app.data.TranslationSettingsRepository
import com.archive.app.data.writeLanguageMirror
import com.archive.app.ui.design.AppDropdownSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LanguageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TranslationSettingsRepository,
) : ViewModel() {

    val language = repository.settings
        .map { AppLanguage.fromName(it.language) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.SYSTEM)

    /**
     * 切换语言：**先同步写镜像**（`Activity.attachBaseContext` 依赖它同步读取），再持久化。
     * 镜像写入是同步的，所以调用方紧接着 `recreate()` 不会出现「用旧语言重建」的竞态。
     */
    fun setLanguage(value: AppLanguage) {
        context.writeLanguageMirror(value)
        viewModelScope.launch { repository.setLanguage(value) }
    }
}

/**
 * 语言选择卡片。
 *
 * 选项里除「跟随系统」外，每种语言都用自己的写法显示（简体中文 / 繁體中文 / English /
 * 日本語 …），这样即便误设成看不懂的语言也认得回来。
 *
 * 选中后立刻 `recreate()`：Activity 在 `attachBaseContext` 里用新语言重建，界面语言随即整体
 * 生效 —— 既不用用户手动重启，也不会去替换 `LocalContext` 的类型（那会让 Hilt 拿不到 Activity）。
 */
@Composable
fun LanguageSettingCard() {
    val viewModel: LanguageViewModel = hiltViewModel()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val systemLabel = stringResource(R.string.language_system)
    val context = LocalContext.current

    val items = remember(systemLabel) {
        listOf(systemLabel) + AppLanguage.entries.drop(1).map { it.label }
    }

    AppDropdownSetting(
        title = stringResource(R.string.language_title),
        summary = stringResource(R.string.language_summary),
        items = items,
        selectedIndex = language.ordinal,
        onSelect = { index ->
            AppLanguage.entries.getOrNull(index)?.let { picked ->
                if (picked != language) {
                    viewModel.setLanguage(picked)
                    (context as? android.app.Activity)?.recreate()
                }
            }
        },
    )
}
