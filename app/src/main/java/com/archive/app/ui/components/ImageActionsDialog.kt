package com.archive.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.theme.LocalPurrColors

/** 长按图片弹出的操作项。 */
data class ImageAction(val label: String, val onClick: () -> Unit)

/**
 * 长按图片弹出的操作列表（样式对照系统/AI 相册的长按菜单：居中卡片 + 纯文字纵向列表）。
 *
 * 用法：`ImageActionsDialog(show = showMenu, actions = listOf(ImageAction("保存原图") { … }), onDismiss = { showMenu = false })`
 */
@Composable
fun ImageActionsDialog(
    show: Boolean,
    actions: List<ImageAction>,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    AppDialog(
        show = show,
        title = title ?: "",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            actions.forEachIndexed { index, action ->
                if (index > 0) AppDivider()
                ActionRow(label = action.label) {
                    onDismiss()
                    action.onClick()
                }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            label,
            style = AppTextStyle.MAIN,
            fontSize = 17.sp,
            color = colors.textPrimary,
        )
    }
}

/** 便捷：为图片生成标准动作集合（分享 / 保存原图 / 保存全部原图 / 复制链接）。 */
@Composable
fun rememberImageActions(
    onShare: () -> Unit,
    onSaveOriginal: () -> Unit,
    onSaveAll: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
): List<ImageAction> {
    val actions = mutableListOf(
        ImageAction("分享图片", onShare),
        ImageAction("保存原图", onSaveOriginal),
    )
    onSaveAll?.let { actions += ImageAction("保存全部原图", it) }
    onCopyLink?.let { actions += ImageAction("复制图片链接", it) }
    return actions
}

/** 对话框内容槽（需要自定义内容时使用）。 */
@Composable
fun ImageActionsDialogContent(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}
