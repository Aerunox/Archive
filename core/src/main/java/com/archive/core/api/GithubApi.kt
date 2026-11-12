package com.archive.core.api

import com.archive.core.model.GithubRelease
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * 更新检查与「关于」页共用的仓库坐标 —— **单一来源**。
 *
 * 两个使用方都从这里取，改仓库时只需要改这一处：
 * - [GithubApi] / [GithubWebApi] 的请求地址；
 * - 「关于 → 来源与许可」里指向仓库主页的那条链接。
 */
object GithubRepo {
    const val OWNER = "Aerunox"
    const val NAME = "Archive"

    /** 浏览器可直接打开的仓库主页（关于页的 GitHub 链接用它）。 */
    const val URL = "https://github.com/Aerunox/Archive"

    /**
     * Releases 列表页（浏览器里能直接点 apk 下载的那个「发布」栏目）。
     *
     * 用作自动唤起浏览器时的**兜底落点**：正常情况下更该落到具体那条发布页
     * （页面里就有该版本的 APK 附件），只有拿不到单条发布地址时才退到这里。
     */
    const val RELEASES_URL = URL + "/releases"

    /** 单条发布的网页地址前缀（网页 feed 兜底时用它拼落地页）。 */
    const val TAG_URL_PREFIX = URL + "/releases/tag/"

    /**
     * 网页版「最新正式版」入口：服务端 302 到 `/releases/tag/<tag>`。
     *
     * 属于 github.com 的 HTML 端点，**不计入 api.github.com 的匿名配额**（60 次/小时/IP），
     * 因此被用作 API 限流时的兜底数据源。
     */
    const val LATEST_URL = URL + "/releases/latest"

    /** 发布 feed（Atom XML）：tag / 标题 / 发布时间 / 落地页都在里面，同样不计配额。 */
    const val ATOM_URL = URL + "/releases.atom"
}

/** GitHub 要求非空的 User-Agent：缺失会被直接拒绝（403），所以三个 client 都显式带上。 */
private const val USER_AGENT = "Archive-Android (+" + GithubRepo.URL + ")"

/** 请求 API 的接口（api.github.com，**受匿名配额限制**：60 次/小时/IP）。 */
interface GithubApi {

    /** 「关于 → 检查更新」按仓库的发布查版本。 */
    @GET("repos/" + GithubRepo.OWNER + "/" + GithubRepo.NAME + "/releases/latest")
    suspend fun getLatestRelease(): GithubRelease

    /** 全量发布列表（FEATURE：更新通道——Beta 通道需要在预发布版本中选取最新）。 */
    @GET("repos/" + GithubRepo.OWNER + "/" + GithubRepo.NAME + "/releases")
    suspend fun getReleases(): List<GithubRelease>

    companion object {
        private const val BASE_URL = "https://api.github.com/"
        private const val TIMEOUT_SECONDS = 10L

        fun create(): GithubApi {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }

            val userAgent = okhttp3.Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/vnd.github+json")
                        .build(),
                )
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(userAgent)
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GithubApi::class.java)
        }
    }
}

/**
 * 网页端兜底数据源（github.com 的 HTML / Atom 端点，**不计入 API 配额**）。
 *
 * 存在的理由：api.github.com 的匿名配额只有每小时 60 次，且是按**出口 IP** 算的 ——
 * 多人共用一个代理出口、或者用户反复点「检查更新」，很容易打到 403（rate limit），
 * 界面上就显示成「检查更新失败」。那不是故障，只是配额用尽；
 * 而这些网页端点不受该配额约束，拿到的信息（版本号 + 发布时间 + 落地页）足够判断有没有新版本。
 */
interface GithubWebApi {

    /**
     * releases.atom：发布 feed，最新几条发布的 tag / 标题 / 时间 / 落地页都在里面。
     *
     * 返回 String 而不是 ResponseBody：Retrofit 的内置 converter 不认裸 String，
     * 而这里的调用方也不需要流式读取（feed 只有几 KB），交给平台的标量转换反而要多引一个依赖。
     */
    @GET
    suspend fun releasesAtom(@Url url: String): ResponseBody

    companion object {
        private const val BASE_URL = "https://github.com/"
        private const val TIMEOUT_SECONDS = 10L

        fun create(): GithubWebApi {
            val userAgent = okhttp3.Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // 不跟随 302：Location 头本身就是答案
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(userAgent)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GithubWebApi::class.java)
        }
    }
}
