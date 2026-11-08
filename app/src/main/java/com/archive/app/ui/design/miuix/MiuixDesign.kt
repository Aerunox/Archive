package com.archive.app.ui.design.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFab
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.design.AppTopScrollBehavior
import com.archive.app.ui.design.DesignComponents

/**
 * The top bar scroll behavior created by this theme's scaffold invocation, so
 * [TopBar] can connect it to the Miuix TopAppBar without changing the
 * screens-facing API (documented pattern: TopAppBar + scrollBehavior +
 * nestedScroll on the content).
 */
private val LocalActiveScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }

/**
 * Miuix (HyperOS style) implementation of the design system.
 *
 * Everything renders inside [MiuixTheme]; overlay components such as
 * [OverlayDialog] rely on the Miuix [MiuixScaffold]'s popup host, which this
 * implementation keeps at its default. Per the official docs, the Scaffold is
 * the cross-platform popup container and MUST wrap OverlayDialog,
 * OverlayDropdownPreference and friends — screens compose AppDialog etc.
 * inside AppScaffold, which satisfies this.
 */
object MiuixDesign : DesignComponents {

    @Composable
    override fun rememberAppScrollBehavior(): AppTopScrollBehavior {
        val behavior = MiuixScrollBehavior()
        return remember { AppTopScrollBehavior(behavior) }
    }

    @Composable
    override fun Scaffold(
        topBar: @Composable () -> Unit,
        bottomBar: @Composable () -> Unit,
        floatingActionButton: @Composable () -> Unit,
        topScrollBehavior: AppTopScrollBehavior?,
        content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
    ) {
        val behavior = topScrollBehavior?.raw as? ScrollBehavior
        CompositionLocalProvider(LocalActiveScrollBehavior provides behavior) {
            MiuixScaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                floatingActionButton = floatingActionButton,
                content = { paddingValues ->
                    // Documented wiring: the collapsing TopAppBar needs its
                    // nestedScrollConnection above the scrolling content.
                    if (behavior != null) {
                        Box(Modifier.nestedScroll(behavior.nestedScrollConnection)) {
                            content(paddingValues)
                        }
                    } else {
                        content(paddingValues)
                    }
                },
            )
        }
    }

    @Composable
    override fun TopBar(
        title: String,
        onBack: (() -> Unit)?,
        actions: @Composable RowScope.() -> Unit,
    ) {
        val behavior = LocalActiveScrollBehavior.current
        val navigationIcon: @Composable () -> Unit = {
            if (onBack != null) {
                MiuixIconButton(onClick = { onBack() }) {
                    // Miuix 的 LocalContentColor 默认硬编码 Black 且应用全程不覆盖，
                    // 省略 tint 的 Icon 在深色顶栏上不可见，故显式取 textPrimary（跟随深浅色）。
                    Icon(
                        imageVector = LucideIcons.back,
                        contentDescription = "返回",
                        tint = com.archive.app.ui.theme.LocalPurrColors.current.textPrimary,
                    )
                }
            }
        }
        if (title.isBlank()) {
            // 空标题：只保留返回箭头与操作的紧凑栏（页面正文已有自己的标题时用）
            SmallTopAppBar(
                title = "",
                navigationIcon = navigationIcon,
                actions = actions,
            )
        } else if (behavior != null && onBack == null) {
            // 只有无返回按钮的根页面才用可折叠的大标题：大标题会把标题排到第二行，
            // 与返回箭头分成两行；带返回按钮的页面必须让箭头和标题同处一行。
            MiuixTopAppBar(
                title = title,
                scrollBehavior = behavior,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        } else {
            SmallTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        }
    }

    @Composable
    override fun BottomBar(content: @Composable RowScope.() -> Unit) {
        MiuixNavigationBar(content = content)
    }

    @Composable
    override fun RowScope.BottomBarItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        label: String,
    ) {
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = icon,
            label = label,
        )
    }

    @Composable
    override fun Card(
        modifier: Modifier,
        onClick: (() -> Unit)?,
        content: @Composable (ColumnScope.() -> Unit),
    ) {
        MiuixCard(modifier = modifier, onClick = onClick, content = content)
    }

    @Composable
    override fun Text(
        text: String,
        modifier: Modifier,
        style: AppTextStyle,
        color: Color?,
        fontSize: TextUnit,
        fontWeight: FontWeight?,
        maxLines: Int,
        overflow: TextOverflow,
        textAlign: TextAlign?,
    ) {
        val textStyle = when (style) {
            AppTextStyle.MAIN -> MiuixTheme.textStyles.main
            AppTextStyle.SECONDARY -> MiuixTheme.textStyles.body2
            AppTextStyle.TITLE -> MiuixTheme.textStyles.title3
            AppTextStyle.SMALL -> MiuixTheme.textStyles.footnote2
        }
        MiuixText(
            text = text,
            modifier = modifier,
            color = color ?: Color.Unspecified,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            style = textStyle,
        )
    }

    @Composable
    override fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier,
        label: String,
        singleLine: Boolean,
        leading: @Composable (() -> Unit)?,
        trailing: @Composable (() -> Unit)?,
        visualTransformation: androidx.compose.ui.text.input.VisualTransformation,
    ) {
        // ── 本地缓冲：把「输入」与「异步回写」解耦 ──
        // 上游的值常常来自 DataStore / Flow（写盘 → 发射 → 重组之间有一段延迟）。
        // 若把延迟抵达的旧文本直接喂回输入框，输入框会短暂显示「少了最后一个字符」的内容，
        // 光标随之落到最后一个字符之前 —— 表现为「每输入一个字符，光标左移一位」。
        // 因此：输入期间以本地缓冲为准，只在**没有焦点**时（首次加载、外部重置）才采纳外部值。
        // 对本地同步状态（如 AccountScreen 的登录表单）行为完全不变：两个值始终相等。
        var text by remember { mutableStateOf(value) }
        var editing by remember { mutableStateOf(false) }
        LaunchedEffect(value, editing) {
            if (!editing && text != value) text = value
        }
        MiuixTextField(
            value = text,
            onValueChange = { updated ->
                text = updated
                onValueChange(updated)
            },
            modifier = modifier.onFocusChanged { editing = it.isFocused },
            label = label,
            useLabelAsPlaceholder = true,
            singleLine = singleLine,
            leadingIcon = leading,
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
        )
    }

    /**
     * 搜索输入框（样式对照 HyperOS 设置页的搜索框）：
     * 浅灰胶囊背景 + 左侧放大镜图标 + 灰色占位文字；获得焦点时右侧出现「取消」，
     * 点击即清空关键词、收起键盘并取消焦点。
     *
     * 输入法动作为「搜索」，按回车只提交搜索（ImeAction.Search + KeyboardActions(onSearch)），
     * 组件本身不会清空文本——Miuix 的 SearchBar/InputField 在收起时会主动清空查询，
     * 会造成「提交后搜索框与结果同时被清空」，因此这里用基础输入框自行绘制外观。
     */
    @Composable
    override fun SearchField(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit,
        placeholder: String,
        modifier: Modifier,
        trailing: @Composable (() -> Unit)?,
        autoFocus: Boolean,
        onCancel: (() -> Unit)?,
    ) {
        val colors = com.archive.app.ui.theme.LocalPurrColors.current
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        var focused by remember { mutableStateOf(false) }
        val textStyle = MiuixTheme.textStyles.main.copy(color = colors.textPrimary)

        // 原位展开的搜索框需要立即定位光标并弹出键盘
        LaunchedEffect(autoFocus) {
            if (autoFocus) {
                runCatching { focusRequester.requestFocus() }
            }
        }

        // 拿到焦点后补一次 show()：requestFocus() 本身不会唤出输入法，而且点外框
        // 触发的焦点变化里 IME 连接可能还没建好，所以要等一帧再请求一次。
        LaunchedEffect(focused) {
            if (focused) {
                withFrameNanos { }
                keyboard?.show()
            }
        }

        fun cancel() {
            onQueryChange("")
            focused = false
            focusManager.clearFocus()
            keyboard?.hide()
            onCancel?.invoke()
        }

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // requestFocus() 只给焦点、不会唤起输入法：点的是外框（不是
                        // BasicTextField 本身）时键盘不会弹出来，必须显式 show()。
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = LucideIcons.search,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textTertiary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = textStyle,
                        singleLine = true,
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focused = it.isFocused },
                    )
                }
                trailing?.let { trailingContent ->
                    Box(Modifier.padding(start = 4.dp)) { trailingContent() }
                }
            }

            // 聚焦时右侧出现「取消」（与参考图一致）
            AnimatedVisibility(
                visible = focused || query.isNotEmpty(),
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    text = "取消",
                    color = colors.primary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable { cancel() }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier) {
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
    }

    @Composable
    override fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier,
        primary: Boolean,
        enabled: Boolean,
    ) {
        TextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = if (primary) ButtonDefaults.textButtonColorsPrimary()
            else ButtonDefaults.textButtonColors(),
        )
    }

    @Composable
    override fun IconButton(
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        tint: Color?,
    ) {
        // Miuix 的 LocalContentColor 默认硬编码为 Color.Black（不随暗色主题变化），
        // 且 MiuixIconButton 不给 Icon 下发颜色：不传 tint 时深色模式下黑色图标
        // 画在深色顶栏上不可见（首页搜索按钮即此症状）。默认取 PurrColors 的
        // 主文字色，显式传 tint 仍优先。
        val resolvedTint = tint ?: com.archive.app.ui.theme.LocalPurrColors.current.textPrimary
        MiuixIconButton(onClick = onClick, modifier = modifier) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
            )
        }
    }

    @Composable
    override fun Dialog(
        show: Boolean,
        title: String,
        summary: String?,
        onDismiss: () -> Unit,
        confirmLabel: String?,
        onConfirm: (() -> Unit)?,
        dismissLabel: String?,
        content: @Composable (ColumnScope.() -> Unit),
    ) {
        OverlayDialog(
            show = show,
            title = title,
            summary = summary,
            onDismissRequest = onDismiss,
        ) {
            Column {
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissLabel != null) {
                        TextButton(text = dismissLabel, onClick = onDismiss)
                    }
                    if (confirmLabel != null) {
                        TextButton(
                            text = confirmLabel,
                            onClick = { onConfirm?.invoke() },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun ListItem(
        title: String,
        modifier: Modifier,
        summary: String?,
        start: @Composable (() -> Unit)?,
        end: @Composable (RowScope.() -> Unit)?,
        onClick: (() -> Unit)?,
    ) {
        BasicComponent(
            title = title,
            summary = summary,
            startAction = start,
            endActions = { end?.invoke(this) },
            onClick = onClick,
            modifier = modifier,
        )
    }

    @Composable
    override fun SmallTitle(text: String, modifier: Modifier) {
        MiuixSmallTitle(text = text, modifier = modifier)
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        HorizontalDivider(modifier = modifier)
    }

    @Composable
    override fun SwitchSetting(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        summary: String?,
    ) {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }

    @Composable
    override fun SliderSetting(
        title: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        modifier: Modifier,
        summary: String?,
        steps: Int,
        valueText: String?,
    ) {
        SliderPreference(
            title = title,
            summary = summary,
            value = value,
            valueText = valueText,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier,
        )
    }

    @Composable
    override fun DropdownSetting(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier,
        summary: String?,
    ) {
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelect,
            modifier = modifier,
        )
    }

    @Composable
    override fun PullToRefresh(
        isRefreshing: Boolean,
        onRefresh: () -> Unit,
        modifier: Modifier,
        contentPadding: androidx.compose.foundation.layout.PaddingValues,
        content: @Composable () -> Unit,
    ) {
        // 下拉刷新的四段提示由库提供，但**写死在库里且是英文**，库里也没有 strings.xml
        // 可随系统语言切换（默认值：Pull down to refresh / Release to refresh /
        // Refreshing... / Refreshed successfully），所以只能由调用方传入本地化文案。
        // 顺序即库的用法：下拉中 → 松开可刷新 → 刷新中 → 刷新完成。
        val refreshTexts = listOf(
            stringResource(com.archive.app.R.string.pull_to_refresh_pull),
            stringResource(com.archive.app.R.string.pull_to_refresh_release),
            stringResource(com.archive.app.R.string.pull_to_refresh_refreshing),
            stringResource(com.archive.app.R.string.pull_to_refresh_done),
        )
        // 库内置组件在 0.9.4 起下拉检测不再依赖 topAppBarScrollBehavior（官方示例即
        // 传 null），保持默认空值即可，本应用也全面停用可折叠顶栏（见各页面注释）。
        MiuixPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            // 全屏铺底的页面（内容直接铺在顶栏下面）靠它把刷新指示器挪到顶栏下方；
            // 内容本身不在顶栏内的页面传默认值即可，指示器随容器自然落在顶栏下方。
            contentPadding = contentPadding,
            refreshTexts = refreshTexts,
            content = content,
        )
    }

    @Composable
    override fun Fab(
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
    ) {
        MiuixFab(onClick = onClick, modifier = modifier) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = com.archive.app.ui.theme.LocalPurrColors.current.onPrimary,
            )
        }
    }
}
