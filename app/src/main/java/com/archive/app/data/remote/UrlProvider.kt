package com.archive.app.data.remote

/**
 * File / thumbnail / icon URL rules, verified live on 2026-10-07.
 *
 * The site spreads one content tree across three hosts, and only one of them
 * serves real files — getting this wrong yields 404 on every original:
 *  - thumbnails: https://img.pawchive.pw/thumbnail/data{path}   (guest OK, CDN)
 *  - originals : https://file.pawchive.pw/data{path}            (guest OK;
 *                `img.pawchive.pw/data{path}` is 404 — thumbnails live on the
 *                img host, files on the file host, and Pawchive
 *                hardcodes `file.pawchive.pw/data{path}` for the same reason)
 *  - creator icon: https://pawchive.pw/icons/{service}/{id}
 *  - creator banner: https://pawchive.pw/banners/{service}/{id}
 *
 * Files the site has not imported yet (`deferred`) carry no `path` at all:
 * the detail endpoint hands out signed temporary links instead, which take
 * precedence over the host rules (see [playbackUrl] / [downloadUrl]).
 *
 * All hosts derive from the configurable base URL, so the rules keep working
 * when the site migrates domains.
 */
object UrlProvider {

    const val DEFAULT_BASE_URL = "https://pawchive.pw"

    fun apiV1(baseUrl: String): String = baseUrl.trimEnd('/') + "/api/v1"

    /** Derives a service subdomain (`{prefix}.pawchive.pw`) from the base URL. */
    private fun subdomainBase(baseUrl: String, prefix: String): String {
        val cleaned = baseUrl.trim().trimEnd('/')
        if (cleaned.isEmpty()) return subdomainBase(DEFAULT_BASE_URL, prefix)
        val schemeEnd = cleaned.indexOf("://")
        if (schemeEnd <= 0) return cleaned
        val scheme = cleaned.substring(0, schemeEnd)
        val rest = cleaned.substring(schemeEnd + 3)
        val host = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")
        val targetHost = if (host.startsWith("$prefix.")) host else "$prefix.$host"
        return "$scheme://$targetHost" + if (path.isEmpty()) "" else "/$path"
    }

    fun imgBase(baseUrl: String): String = subdomainBase(baseUrl, "img")

    /** File host that actually serves originals (`file.pawchive.pw`). */
    fun fileBase(baseUrl: String): String = subdomainBase(baseUrl, "file")

    /** {path} is what the API returns in file.path / attachment.path. */
    fun thumbnailUrl(baseUrl: String, path: String): String =
        imgBase(baseUrl).trimEnd('/') + "/thumbnail/data" + path

    fun fileUrl(baseUrl: String, path: String): String =
        fileBase(baseUrl).trimEnd('/') + "/data" + path

    /**
     * Playable URL for an attachment: the signed temporary stream (`temp_url`,
     * an HLS master playlist for deferred videos) when the site provides one,
     * otherwise the original file on the file host. Null when neither exists.
     */
    fun playbackUrl(baseUrl: String, path: String?, tempUrl: String?): String? =
        tempUrl?.takeIf { it.isNotBlank() }
            ?: path?.takeIf { it.isNotBlank() }?.let { fileUrl(baseUrl, it) }

    /**
     * Downloadable URL for an attachment: `temp_download_url` (a direct file,
     * also for deferred videos) takes precedence over the file host.
     */
    fun downloadUrl(baseUrl: String, path: String?, tempDownloadUrl: String?): String? =
        tempDownloadUrl?.takeIf { it.isNotBlank() }
            ?: path?.takeIf { it.isNotBlank() }?.let { fileUrl(baseUrl, it) }

    /** True when the URL is one of the site's signed temporary links. */
    fun isTemporaryUrl(url: String): Boolean = url.contains("?e=") || url.contains("&e=")

    fun iconUrl(baseUrl: String, service: String, creatorId: String): String =
        baseUrl.trimEnd('/') + "/icons/" + service + "/" + creatorId

    fun bannerUrl(baseUrl: String, service: String, creatorId: String): String =
        baseUrl.trimEnd('/') + "/banners/" + service + "/" + creatorId

    /** Content HTML may embed relative image paths — resolve against the base. */
    fun contentImageUrl(baseUrl: String, src: String): String = when {
        src.startsWith("http://") || src.startsWith("https://") -> src
        src.startsWith("/") -> baseUrl.trimEnd('/') + src
        else -> baseUrl.trimEnd('/') + "/" + src
    }
}
