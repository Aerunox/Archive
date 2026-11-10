package com.archive.app.data.remote

import com.archive.app.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Static headers every Pawchive request carries. */
@Singleton
class UserAgentInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Archive/1.0 (Android; third-party client, no tracking)",
                )
                .build(),
        )
}

/** Adds the session cookie the user pasted in settings (spec: cookieAuth "session"). */
@Singleton
class SessionInterceptor @Inject constructor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val cookie = settings.sessionCookieOnce()
        if (cookie.isBlank()) return chain.proceed(chain.request())
        // 用户可能粘贴完整 Cookie 头（含多个 cookie）：按分号拆段后取 session= 段，
        // 只在串中间时 substringAfter 会把后面的无关 cookie 一并塞进 session 值
        val value = cookie.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("session=") }
            ?.removePrefix("session=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: cookie
        return chain.proceed(
            chain.request().newBuilder()
                .header("Cookie", "session=$value")
                .build(),
        )
    }
}

/**
 * The site may migrate domains, so the base URL is a setting. Retrofit is built
 * against the default URL; this interceptor rewrites scheme/host/port per
 * request from the current setting while keeping the /api/v1 path intact.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = UrlProvider.apiV1(settings.baseUrlOnce()).toHttpUrl()
        val rewritten = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(
        userAgent: UserAgentInterceptor,
        session: SessionInterceptor,
        baseUrl: BaseUrlInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(userAgent)
        .addInterceptor(session)
        .addInterceptor(baseUrl)
        .addInterceptor(
            // 与 core 的 HttpClientFactory.buildSanitizedLogger 对齐：release 不打日志，
            // 否则列表页一次滚动几十个请求都在为 logcat 拼字符串、写字符串
            HttpLoggingInterceptor().apply {
                level = if (com.archive.app.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(UrlProvider.apiV1(UrlProvider.DEFAULT_BASE_URL) + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun pawchiveApi(retrofit: Retrofit): PawchiveApi = retrofit.create(PawchiveApi::class.java)
}
