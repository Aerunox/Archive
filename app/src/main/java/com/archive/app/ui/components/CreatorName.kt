package com.archive.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.archive.app.ui.LocalDisplayNames

/**
 * 作者昵称：帖子接口只给作者 id，这里做一次解析（本地作者库 → 内存缓存 → 线上兜底），
 * 解析完成前先显示 id，避免列表出现空白。
 */
@Composable
fun rememberCreatorDisplayName(service: String, creatorId: String): String {
    val resolver = LocalDisplayNames.current
    var name by remember(service, creatorId) { mutableStateOf(creatorId) }
    LaunchedEffect(service, creatorId, resolver) {
        val resolved = resolver?.resolve(service, creatorId)
        if (!resolved.isNullOrBlank()) name = resolved
    }
    return name
}
