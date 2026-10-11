package com.archive.core.util

/**
 * 创作者原订阅网站主页链接（FEAT-SOURCE-SITE-LINK）。
 *
 * Pawchive 的 creatorId 即原站用户 id，可按 service 推导原站主页 URL；
 * 未知 service 返回 null（菜单项随之隐藏）。
 */
object CreatorSiteLinks {

    fun profileUrl(service: String, creatorId: String): String? {
        return when (service.lowercase()) {
            "patreon" -> "https://www.patreon.com/user?u=$creatorId"
            "fanbox" -> "https://$creatorId.fanbox.cc/"
            "pixiv" -> "https://www.pixiv.net/users/$creatorId"
            else -> null
        }
    }
}
