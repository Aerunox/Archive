package com.archive.data.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.archive.core.store.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载过程的 Toast 反馈（FEATURE：下载反馈）。
 *
 * - 开始 / 完成 / 失败各给一次 Toast，让用户不用去看通知栏也知道发生了什么；
 * - 连续入队（「保存全部图片」可能一次几十个）与连续完成会**合并且去抖**成一条提示，
 *   否则系统会把几十个 Toast 依次排队弹完，等于刷屏；
 * - Toast 统一回主线程弹（下载协程都在 IO 线程）；
 * - 完成提示跟随设置页的「下载完成通知」开关（与通知栏的完成通知同一个开关，
 *   关掉之后不再打扰）。
 */
@Singleton
class DownloadFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
) {

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    /** 待合并的文件名（按提示种类分开攒）。 */
    private val buckets = mutableMapOf(
        KIND_START to mutableListOf<String>(),
        KIND_FINISH to mutableListOf<String>(),
        KIND_FAIL to mutableListOf<String>(),
    )

    /** 已经安排了合并回调的种类，保证同一波只排一次。 */
    private val scheduled = mutableSetOf<String>()

    // ---------------------------------------------------------------- 对外入口

    /** 用户点了下载、任务已入队。 */
    fun started(fileName: String) = collect(KIND_START, fileName, START_DEBOUNCE_MS)

    /** 下载完成。 */
    fun finished(fileName: String) {
        if (!settingsManager.isDownloadCompleteNotificationEnabled()) return
        collect(KIND_FINISH, fileName, FINISH_DEBOUNCE_MS)
    }

    /** 下载失败（含站点没有原文件、链接过期等）。 */
    fun failed(fileName: String) = collect(KIND_FAIL, fileName, FINISH_DEBOUNCE_MS)

    /** 同一文件已在下载队列里，本次点击不会重复下载。 */
    fun alreadyQueued(fileName: String) = toast("已在下载队列中：$fileName")

    /** preview_only 的文件：站点只有预览、原图未导入，直接告知而不是让用户以为在下。 */
    fun skippedPreviewOnly(fileName: String) =
        toast("该文件站点仅有预览（原图未导入），未加入下载：$fileName")

    // ---------------------------------------------------------------- 合并与弹出

    /** 攒进 bucket；同一波里第一个到的负责安排一次延迟合并输出。 */
    private fun collect(kind: String, fileName: String, delayMs: Long) {
        val first = synchronized(lock) {
            buckets.getValue(kind).add(fileName)
            scheduled.add(kind)
        }
        if (!first) return
        main.postDelayed({ flush(kind) }, delayMs)
    }

    private fun flush(kind: String) {
        val names = synchronized(lock) {
            scheduled.remove(kind)
            buckets.getValue(kind).toList().also { buckets.getValue(kind).clear() }
        }
        if (names.isEmpty()) return
        toast(
            when (kind) {
                KIND_START -> if (names.size == 1) {
                    "已开始下载：${names.first()}"
                } else {
                    "已开始下载 ${names.size} 个文件"
                }

                KIND_FINISH -> if (names.size == 1) {
                    "下载完成：${names.first()}"
                } else {
                    "已完成 ${names.size} 个文件的下载"
                }

                else -> if (names.size == 1) {
                    "下载失败：${names.first()}"
                } else {
                    "${names.size} 个文件下载失败"
                }
            },
        )
    }

    private fun toast(message: String) {
        main.post {
            runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private companion object {
        const val KIND_START = "start"
        const val KIND_FINISH = "finish"
        const val KIND_FAIL = "fail"

        /** 入队去抖窗口：一次「保存全部」的连续入队会落在同一窗口里，合成一条。 */
        const val START_DEBOUNCE_MS = 350L

        /** 完成/失败合并窗口：略长一点，批量下载的收尾也尽量合成一条。 */
        const val FINISH_DEBOUNCE_MS = 800L
    }
}
