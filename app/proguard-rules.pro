# Archive ProGuard rules (release build currently ships unminified;
# these rules keep things safe if minification is enabled later).

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.archive.app.**$$serializer { *; }
-keepclassmembers class com.archive.app.** { *** Companion; }
-keepclasseswithmembers class com.archive.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# ---------------------------------------------------------------------------
# R8 混淆保护：ONNX Runtime（「提取文字」识别的运行时）
#
# 实测依据：release 包点「提取文字」必崩，logcat 现场为
#   NoSuchMethodError: no non-static method
#     "Lai/onnxruntime/NodeInfo;.<init>(Ljava/lang/String;Lai/onnxruntime/ValueInfo;)V"
#       at ai.onnxruntime.OrtSession.getInputInfo(...)
#       at ai.onnxruntime.OrtSession.b()
#
# 关键在于它是 **native 崩溃**：ORT 的 JNI 层用反射构造 NodeInfo / ValueInfo 等 Java 对象，
# R8 把类名与构造器改名裁剪后反射查不到；异常从 native 调用内部抛出，ART 判定
# "No pending exception expected" 直接 SIGABRT 终止进程（栈上连 Java 异常都抓不到）。
# JVM 单测不经过 R8、debug 包不混淆，所以**只有 release 会崩** —— 必须整包保住。
# ---------------------------------------------------------------------------
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ---------------------------------------------------------------------------
# R8 混淆保护：Gson 反射模型
#
# 实测依据（release mapping.txt）：不加下面的规则时，core 模型的字段会被改名成
# a/b/c（Post.id -> a、DownloadRecord.url -> b、BackupBundle -> ds），而 Gson 是按
# 字段名映射 JSON 的 —— 结果是收藏、下载记录、离线归档、订阅、GitHub 更新检查、
# 备份导入导出**全部静默失效**（不崩溃，只是数据全空），最难排查的一类问题。
# ---------------------------------------------------------------------------

# core 的 REST 模型（字段名即 JSON key）
-keep class com.archive.core.model.** { *; }

# 备份文件的 DTO（BackupManager 用 Gson 读写；新增字段类型时记得一并加进来）
-keep class com.archive.data.repository.BackupBundle { *; }
-keep class com.archive.data.repository.CreatorRef { *; }
-keep class com.archive.data.repository.CreatorSubscriptionRef { *; }
-keep class com.archive.data.repository.BackupSettings { *; }
-keep class com.archive.data.repository.ReadingProgressSnapshot { *; }

# 保底：任何用 @SerializedName 标注的字段都保住名字（将来新增 Gson 模型时免踩坑）
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 崩溃栈可读：保留行号并隐藏真实源文件名（配合 mapping.txt 反混淆）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
