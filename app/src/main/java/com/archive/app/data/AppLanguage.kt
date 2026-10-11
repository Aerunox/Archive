package com.archive.app.data

/**
 * 应用界面语言。
 *
 * 只影响**应用自身的界面文案**（资源字符串：菜单、按钮、设置项等），
 * 绝不触碰用户生成内容 —— 帖子标题、正文、作者信息始终按原文显示。
 *
 * [label] 用该语言自己的写法（约定俗成，用户不必先看懂当前语言）；
 * [tag] 是 BCP-47 语言标签，空串表示「跟随系统」。
 */
enum class AppLanguage(val tag: String, val label: String) {
    SYSTEM("", ""),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    RUSSIAN("ru", "Русский"),
    INDONESIAN("id", "Bahasa Indonesia");

    val isSystem: Boolean get() = this == SYSTEM

    companion object {
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
