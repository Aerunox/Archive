package com.archive.core.model

/**
 * 文件哈希反查（FEAT-HASH-SEARCH）：把 search_hash 结果映射为 [Post] 列表，
 * 复用搜索页既有的 PostAdapter 渲染、排序与屏蔽/筛选管线。
 *
 * - 站点帖子：[FileSearchPost] 携带 file / attachments，可完整渲染缩略图并直达帖子详情；
 * - Discord 帖子：仅有服务器 / 频道 / 摘要，映射为 service="discord"、user=服务器、
 *   userName=频道的纯文本帖子（无附件，详情页加载失败时走既有错误态降级）。
 */
fun FileSearchResult.toPosts(): List<Post> {
    val sitePosts = posts.orEmpty().map { p ->
        Post(
            id = p.id,
            user = p.user,
            userName = null,
            service = p.service,
            title = p.title,
            content = p.substring,
            embed = null,
            added = null,
            published = p.published,
            edited = null,
            file = p.file,
            attachments = p.attachments,
            sharedFile = null
        )
    }
    val discordPosts = discordPosts.orEmpty().map { d ->
        Post(
            id = d.id,
            user = d.server,
            userName = d.channel,
            service = "discord",
            title = d.substring,
            content = null,
            embed = null,
            added = null,
            published = d.published,
            edited = null,
            file = null,
            attachments = null,
            sharedFile = null
        )
    }
    return sitePosts + discordPosts
}
