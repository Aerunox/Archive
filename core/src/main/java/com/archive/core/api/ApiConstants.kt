package com.archive.core.api

/**
 * 网络层全局常量单一来源（1.7.6 常量收口）。
 *
 * 主域、分页步长、缓存与过盾参数此前散落在 ApiClient / ClearanceInterceptor /
 * CloudflareManager / ApiMemoryCache / 各列表 ViewModel 中各自硬编码，
 * 收口到这里后，调整只需改这一处。各组件可保留本地别名以减小改动面。
 */
object ApiConstants {

    /** 平台主域根 URL（登录端点、过盾加载页、Referer 使用，含尾斜杠）。 */
    const val MAIN_BASE_URL = "https://pawchive.pw/"

    /** 平台主域 host（Cookie 注入与 CDN 子域判断使用）。 */
    const val MAIN_HOST = "pawchive.pw"

    /** REST API 根路径（Retrofit baseUrl）。 */
    const val API_BASE_URL = "${MAIN_BASE_URL}api/v1/"

    /** 服务端列表接口每页固定返回的条数（仅支持 offset 分页，无 limit 参数）。 */
    const val PAGE_SIZE = 50

    /** GET JSON 内存缓存有效期（ApiMemoryCache）。 */
    const val MEMORY_CACHE_TTL_MS = 5 * 60 * 1000L

    /** GET JSON 内存缓存最大条目数（ApiMemoryCache）。 */
    const val MEMORY_CACHE_MAX_ENTRIES = 200

    /** Cloudflare 挑战单次过盾超时（CloudflareManager）。 */
    const val CHALLENGE_TIMEOUT_MS = 30_000L

    /**
     * 过盾凭据 TTL。cf_clearance 通常有效期约 30 分钟，保守设 25 分钟（预留约 5 分钟余量），
     * 过期后主动重新过盾；前台恢复时 preheat 会提前刷新，避免页面加载等待。
     */
    const val CLEARANCE_TTL_MS = 25 * 60 * 1000L
}
