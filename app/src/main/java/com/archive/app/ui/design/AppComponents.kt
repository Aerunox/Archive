package com.archive.app.ui.design

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Thin wrappers screens actually call; dispatch to the active [DesignComponents]. */

@Composable
fun rememberAppScrollBehavior(): AppTopScrollBehavior =
    LocalDesign.current.rememberAppScrollBehavior()

@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    topScrollBehavior: AppTopScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) = LocalDesign.current.Scaffold(topBar, bottomBar, floatingActionButton, topScrollBehavior, content)

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) = LocalDesign.current.TopBar(title, onBack, actions)

@Composable
fun AppBottomBar(content: @Composable RowScope.() -> Unit) =
    LocalDesign.current.BottomBar(content)

@Composable
fun RowScope.AppBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    with(LocalDesign.current) { BottomBarItem(selected, onClick, icon, label) }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = LocalDesign.current.Card(modifier, onClick, content)

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: AppTextStyle = AppTextStyle.MAIN,
    color: Color? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) = LocalDesign.current.Text(text, modifier, style, color, fontSize, fontWeight, maxLines, overflow, textAlign)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    singleLine: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) = LocalDesign.current.TextField(
    value, onValueChange, modifier, label, singleLine, leading, trailing, visualTransformation,
)

@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    autoFocus: Boolean = false,
    onCancel: (() -> Unit)? = null,
) = LocalDesign.current.SearchField(
    query, onQueryChange, onSearch, placeholder, modifier, trailing, autoFocus, onCancel,
)

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) =
    LocalDesign.current.Switch(checked, onCheckedChange, modifier)

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) = LocalDesign.current.Button(text, onClick, modifier, primary, enabled)

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) = LocalDesign.current.IconButton(onClick, icon, contentDescription, modifier, tint)

@Composable
fun AppDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) = LocalDesign.current.Dialog(show, title, summary, onDismiss, confirmLabel, onConfirm, dismissLabel, content)

@Composable
fun AppListItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    start: @Composable (() -> Unit)? = null,
    end: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = LocalDesign.current.ListItem(title, modifier, summary, start, end, onClick)

@Composable
fun AppSmallTitle(text: String, modifier: Modifier = Modifier) =
    LocalDesign.current.SmallTitle(text, modifier)

@Composable
fun AppDivider(modifier: Modifier = Modifier) = LocalDesign.current.Divider(modifier)

@Composable
fun AppSwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) = LocalDesign.current.SwitchSetting(title, checked, onCheckedChange, summary)

@Composable
fun AppSliderSetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    steps: Int = 0,
    valueText: String? = null,
) = LocalDesign.current.SliderSetting(title, value, onValueChange, valueRange, modifier, summary, steps, valueText)

@Composable
fun AppDropdownSetting(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = LocalDesign.current.DropdownSetting(title, items, selectedIndex, onSelect, modifier, summary)

@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) = LocalDesign.current.PullToRefresh(isRefreshing, onRefresh, modifier, contentPadding, content)

@Composable
fun AppFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) = LocalDesign.current.Fab(onClick, icon, contentDescription, modifier)
