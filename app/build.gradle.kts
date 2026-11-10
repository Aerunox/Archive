plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** 版本号单点定义：`defaultConfig` 与 APK 文件名都用它，避免两处漂移。 */
val appVersionName = "1.0.2"

android {
    namespace = "com.archive.app"
    // navigation 2.10 / coil 3.6 / media3 1.11 AAR metadata require API 37 to compile.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.archive.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = appVersionName
    }

    // 多架构打包（永久生效）：按 ABI 拆分出四个单架构 APK + 一个 universal 全架构包。
    // OCR 依赖 ONNX Runtime 的原生库（四 ABI 全有，单个 ABI 的 so 约 15MB），拆包后
    // 单架构包各自只带自己那套 so，universal 包则同时含四套（体积大，供兼容安装用）。
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // 用调试签名打包 release，保证 APK 可直接安装（正式发布请替换为自己的 keystore）
    signingConfigs {
        create("debugSign") {
            // 优先用工作区内 keystore（沙箱环境写不了 ~/.android），退回系统默认
            val localKeystore = rootProject.file(".gradle-home/debug.keystore")
            storeFile = if (localKeystore.exists()) localKeystore
            else file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 代码裁剪/混淆 + 资源压缩：实测 APK 50.46 MB → 6.65 MB。
            // Gson 反射模型与备份 DTO 的 keep 规则见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debugSign")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 只保留应用真正提供的语言：resources.arsc 里原本带着依赖库的 88 个 locale
    // （ca / fa / ka / ta …），这些语言应用永远显示不到，纯占体积。
    // 默认 values/（中文）不受影响，永远保留。
    // 注意 `in` / `id` 都列上：印尼语在资源限定符里是历史写法 `in`，BCP-47 里是 `id`。
    androidResources {
        localeFilters += listOf(
            "zh", "zh-rTW", // 中文（默认 values/ 为其简体）与繁体
            "en", "de", "es", "fr", "in", "id", "ja", "ko", "ru", "tr", "ar",
        )
    }
}

// APK 输出文件名：AGP 默认按**模块名**命名（app-release.apk），这里改成项目名 + 版本号 + ABI，
// 方便在 Releases 页一眼认出（splits 拆包后每个 output 各对应一个 ABI，
// universal 包没有 versionCode 后缀差异，用 "universal" 标识）。用官方 Variant API，
// 不改模块名，也不影响安装与升级。
androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: "release"
        variant.outputs.forEach { output ->
            // splits 模式下 output.filters 带 ABI 过滤：filterType 是类型名（"ABI"），
            // 真正的架构名在 identifier 里（arm64-v8a 等）；universal 包 filters 为空。
            val abiSuffix = output.filters.firstOrNull()?.identifier?.let { "-$it" } ?: "-universal"
            output.outputFileName.set("Archive-$appVersionName-$buildType$abiSuffix.apk")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    // Pawchive 功能拓展模块（下载中心/订阅通知/多账号/离线归档/Cloudflare 通关）
    implementation(project(":core"))
    implementation(project(":data"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.media3.exoplayer)
    // 站点未导入原文件的视频走 HLS 临时流（master.m3u8），没有这个模块 ExoPlayer 播不了
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation("androidx.media3:media3-datasource-okhttp:${libs.versions.media3.get()}")

    implementation(libs.zoomable)

    // 端上文字识别：PP-OCR（PaddleOCR）模型 + ONNX Runtime 推理。
    // 模型与字典放在 app/src/main/assets/ocr/，管线见 com.archive.app.ocr.OcrEngine。
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)


    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.runtime)
    // 同一份 ONNX 模型在 JVM 上跑：OcrEngineTest 用它验证预/后处理与 PaddleOCR 是否对齐
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")
}
