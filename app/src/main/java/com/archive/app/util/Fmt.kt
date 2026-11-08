package com.archive.app.util

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Formatting helpers. java.time is available on minSdk 24 via core desugaring. */
object Fmt {

    fun relative(unixSeconds: Long?): String {
        if (unixSeconds == null || unixSeconds <= 0) return "未知"
        // 一步构造目标时间，避免 now() 两次取值不一致带来的偏差
        val time = java.time.Instant.ofEpochSecond(unixSeconds)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return relative(LocalDateTime.now(), time)
    }

    fun relative(now: LocalDateTime, time: LocalDateTime): String {
        val d = Duration.between(time, now)
        val seconds = d.seconds
        return when {
            // 未来时间（定时发布/时钟偏差）不应显示"刚刚"
            seconds < -60 -> "即将"
            seconds < 60 -> "刚刚"
            seconds < 3600 -> "${seconds / 60} 分钟前"
            seconds < 86400 -> "${seconds / 3600} 小时前"
            seconds < 86400 * 30 -> "${seconds / 86400} 天前"
            seconds < 86400L * 365 -> "${seconds / 86400 / 30} 个月前"
            else -> "${seconds / 86400 / 365} 年前"
        }
    }

    /** ISO strings from the API carry no timezone ("2026-10-06T07:00:00"). */
    fun relativeIso(iso: String?): String {
        if (iso.isNullOrBlank()) return "未知"
        val time = runCatching { OffsetDateTime.parse(iso).toLocalDateTime() }
            .getOrElse { runCatching { LocalDateTime.parse(iso) }.getOrNull() }
            ?: return iso.take(10)
        return relative(LocalDateTime.now(), time)
    }

    fun isoDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "未知"
        val time = runCatching { OffsetDateTime.parse(iso).toLocalDateTime() }
            .getOrElse { runCatching { LocalDateTime.parse(iso) }.getOrNull() }
            ?: return iso.take(10)
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    fun bytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    fun count(n: Int): String = when {
        n >= 10000 -> "%.1f 万".format(n / 10000.0)
        else -> n.toString()
    }
}
