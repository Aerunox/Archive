plugins {
    id("com.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.archive.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        disable += "UnsafeOptInUsageError"
        disable += "SpecifyForegroundServiceType"
    }
}

dependencies {
    // 模块拆分（ARCH-002）：:data 依赖 :core 基础设施
    implementation(project(":core"))

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("com.google.android.material:material:1.13.0")

    // Retrofit - AuthRepository 直接解析 Response/ResponseBody
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")

    // WorkManager - @HiltWorker 下载/缓存任务
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Chrome Custom Tabs - UpdateChecker 打开下载页
    implementation("androidx.browser:browser:1.8.0")

    // Jetpack DataStore - BookmarkManager/SearchHistoryManager/BlockedCreatorManager 等持久化
    api("androidx.datastore:datastore-preferences:1.2.1")

    // Gson - BookmarkManager/DownloadHistoryManager legacy 数据迁移
    api("com.google.code.gson:gson:2.10.1")

    // SAF DocumentFile - CacheRepository / DownloadRepository 目录操作
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Coil 2 - AppCacheCleaner/CacheRepository 统计与清理图片缓存（与 app 层 Coil3 共存）
    implementation("io.coil-kt:coil:2.6.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("androidx.hilt:hilt-compiler:1.4.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
