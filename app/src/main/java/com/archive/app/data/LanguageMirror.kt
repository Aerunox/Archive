package com.archive.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 界面语言的「同步镜像」。
 *
 * 为什么需要它：`Activity.attachBaseContext` 必须**同步**拿到语言，而 DataStore 是异步的，
 * 在 attachBaseContext 里等它会把启动卡住。所以选定的语言在写入 DataStore 的同时镜像到
 * SharedPreferences —— 读取零成本。
 *
 * 语言的实际生效方式是标准 Android 路径：Activity 在 attachBaseContext 里套用语言，
 * 于是 Activity 自身就是本地化后的 Context，`stringResource` 自然按该语言解析，
 * **Hilt 也能照常拿到 Activity**（早先换成 `createConfigurationContext` 生成的 ContextImpl
 * 会让 `hiltViewModel()` 直接抛异常，启动即崩）。
 */
private const val PREFS = "archive_language"
private const val KEY = "language"

fun Context.writeLanguageMirror(language: AppLanguage) {
    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, language.name)
        .apply()
}

/** 镜像里当前记录的语言（没写过就是「跟随系统」）。 */
fun Context.readLanguageMirror(): AppLanguage =
    AppLanguage.fromName(
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
    )

/** 把语言套到任意 Context 上（只在 `attachBaseContext` 里对一个 Activity 基类使用）。 */
fun Context.withAppLanguage(language: AppLanguage): Context {
    if (language.isSystem) return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(language.tag))
    return createConfigurationContext(configuration)
}

/** 供 `attachBaseContext` 使用：按镜像里记录的语言返回一个已本地化的 Context。 */
fun Context.localizedForAppLanguage(): Context = withAppLanguage(readLanguageMirror())
