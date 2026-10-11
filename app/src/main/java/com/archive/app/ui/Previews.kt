package com.archive.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.archive.app.domain.Attachment
import com.archive.app.domain.PostSummary
import com.archive.app.ui.components.PostCard
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSwitchSetting
import com.archive.app.ui.theme.AppColorMode
import com.archive.app.ui.theme.AppMonetStyle
import com.archive.app.ui.theme.ArchiveTheme

@Composable
private fun SampleContent() {
    Column(Modifier.padding(8.dp)) {
        AppSmallTitle("示例区块")
        PostCard(
            post = PostSummary(
                service = "patreon",
                creatorId = "30500811",
                postId = "171486256",
                title = "示例帖子标题 Sample Post",
                excerpt = "帖子摘要：这里展示列表卡片的两行摘要文本。",
                published = "2026-10-05T12:13:08",
                cover = Attachment("cover.jpg", "/a9/d7/a9d71a.jpg"),
                previewOnly = false,
            ),
            onClick = {},
        )
        AppSwitchSetting(title = "模糊缩略图", summary = "保护隐私", checked = true, onCheckedChange = {})
        AppListItem(title = "Base URL", summary = "https://pawchive.pw", onClick = null)
    }
}

@Preview(name = "Miuix · Light", showBackground = true)
@Composable
private fun PreviewMiuixLight() {
    ArchiveTheme(
        colorMode = AppColorMode.LIGHT,
        monetStyle = AppMonetStyle.TONAL_SPOT,
        seedColor = null,
        blurThumbnails = false,
    ) {
        SampleContent()
    }
}

@Preview(name = "Miuix · Dark", showBackground = true)
@Composable
private fun PreviewMiuixDark() {
    ArchiveTheme(
        colorMode = AppColorMode.DARK,
        monetStyle = AppMonetStyle.TONAL_SPOT,
        seedColor = null,
        blurThumbnails = false,
    ) {
        SampleContent()
    }
}

@Preview(name = "Miuix · Monet", showBackground = true)
@Composable
private fun PreviewMiuixMonet() {
    ArchiveTheme(
        colorMode = AppColorMode.MONET_LIGHT,
        monetStyle = AppMonetStyle.VIBRANT,
        seedColor = 0xFF3482FF,
        blurThumbnails = false,
    ) {
        SampleContent()
    }
}
