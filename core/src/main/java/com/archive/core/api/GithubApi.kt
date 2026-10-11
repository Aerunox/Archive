package com.archive.core.api

import com.archive.core.model.GithubRelease
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * 更新检查指向的仓库坐标 —— **刻意留空**：源码与安装包里都不再带任何具体仓库标识。
 *
 * 需要启用「关于 → 检查更新」时，把 owner / repo 填在这里（两行），
 * STABLE/BETA 通道、语义化版本比较、更新弹窗等流程都不用改。
 *
 * 留空时请求会命中 `repos//releases/latest` 这种无效路径，GitHub 返回 404，
 * 界面按「仓库还没有发布版本」呈现 —— 走的是与「仓库确实暂无 Release」同一条分支，
 * 不报错、不弹失败提示。
 */
private const val REPO_OWNER = ""
private const val REPO_NAME = ""

interface GithubApi {

    /** 「关于 → 检查更新」按仓库的发布查版本。 */
    @GET("repos/$REPO_OWNER/$REPO_NAME/releases/latest")
    suspend fun getLatestRelease(): GithubRelease

    /** 全量发布列表（FEATURE：更新通道——Beta 通道需要在预发布版本中选取最新）。 */
    @GET("repos/$REPO_OWNER/$REPO_NAME/releases")
    suspend fun getReleases(): List<GithubRelease>

    companion object {
        private const val BASE_URL = "https://api.github.com/"
        private const val TIMEOUT_SECONDS = 10L

        fun create(): GithubApi {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
