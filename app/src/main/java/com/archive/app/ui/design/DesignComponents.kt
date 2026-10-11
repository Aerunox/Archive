package com.archive.app.ui.design

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Theme-neutral component surface. Screens depend only on these; the concrete
 * implementation ([com.archive.app.ui.design.miuix.MiuixDesign]) is provided
 * by the theme engine, so screens never touch Miuix APIs directly.
 */
interface DesignComponents {
    /**
     * Creates the theme's top bar scroll behavior. Under Miuix this is
     * MiuixScrollBehavior() (documented pattern: TopAppBar + nestedScroll +
     * overScrollVertical); other themes return a no-op holder.
     */
    @Composable
    fun rememberAppScrollBehavior(): AppTopScrollBehavior

    @Composable
    fun Scaffold(
        topBar: @Composable () -> Unit = {},
        bottomBar: @Composable () -> Unit = {},
        floatingActionButton: @Composable () -> Unit = {},
        topScrollBehavior: AppTopScrollBehavior? = null,
        content: @Composable (PaddingValues) -> Unit,
    )

    @Composable
    fun TopBar(
        title: String,
        onBack: (() -> Unit)? = null,
        actions: @Composable RowScope.() -> Unit = {},
    )

    @Composable
    fun BottomBar(content: @Composable RowScope.() -> Unit)

    @Composable
    fun RowScope.BottomBarItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        label: String,
    )

    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    )

    @Composable
    fun Text(
        text: String,
        modifier: Modifier = Modifier,
        style: AppTextStyle = AppTextStyle.MAIN,
        color: Color? = null,
        fontSize: TextUnit = TextUnit.Unspecified,
        fontWeight: FontWeight? = null,
        maxLines: Int = Int.MAX_VALUE,
        overflow: TextOverflow = TextOverflow.Clip,
        textAlign: TextAlign? = null,
    )

    @Composable
    fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        label: String = "",
        singleLine: Boolean = true,
        leading: @Composable (() -> Unit)? = null,
        trailing: @Composable (() -> Unit)? = null,
        visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
            androidx.compose.ui.text.input.VisualTransformation.None,
    )

    @Composable
    fun SearchField(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit,
        placeholder: String,
        modifier: Modifier = Modifier,
        trailing: @Composable (() -> Unit)? = null,
        /** 首次组合即请求焦点（原位展开的搜索框会用到）。 */
        autoFocus: Boolean = false,
        /** 「取消」被点击时回调：清空文本与键盘之后触发（用于收起搜索态）。 */
        onCancel: (() -> Unit)? = null,
    )

    @Composable
    fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier)

    @Composable
    fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        primary: Boolean = true,
        enabled: Boolean = true,
    )

    @Composable
    fun IconButton(
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        tint: Color? = null,
    )

    @Composable
    fun Dialog(
        show: Boolean,
        title: String,
        summary: String? = null,
        onDismiss: () -> Unit,
        confirmLabel: String? = null,
        onConfirm: (() -> Unit)? = null,
        dismissLabel: String? = null,
        content: @Composable ColumnScope.() -> Unit = {},
    )

    @Composable
    fun ListItem(
        title: String,
        modifier: Modifier = Modifier,
        summary: String? = null,
        start: @Composable (() -> Unit)? = null,
        end: @Composable (RowScope.() -> Unit)? = null,
        onClick: (() -> Unit)? = null,
    )

    @Composable
    fun SmallTitle(text: String, modifier: Modifier = Modifier)

    @Composable
    fun Divider(modifier: Modifier = Modifier)

    @Composable
    fun SwitchSetting(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        summary: String? = null,
    )

    @Composable
    fun SliderSetting(
        title: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        modifier: Modifier = Modifier,
        summary: String? = null,
        steps: Int = 0,
        valueText: String? = null,
    )

    @Composable
    fun DropdownSetting(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier,
        summary: String? = null,
    )

    @Composable
    fun PullToRefresh(
        isRefreshing: Boolean,
        onRefresh: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Fab(
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
    )
}

enum class AppTextStyle { MAIN, SECONDARY, TITLE, SMALL }

/**
 * Opaque holder for the Miuix collapsing top bar behavior
 * (top.yukonga.miuix.kmp.basic.ScrollBehavior). Created through
 * [DesignComponents.rememberAppScrollBehavior].
 */
class AppTopScrollBehavior internal constructor(val raw: Any?)

val LocalDesign = staticCompositionLocalOf<DesignComponents> {
    error("DesignComponents not provided — wrap content in ArchiveTheme")
}
