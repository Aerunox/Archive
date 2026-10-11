package com.archive.app.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.archive.app.R
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.core.api.CloudflareManager

/** 官方注册页。站点注册必须过人机验证，所以只能交给浏览器内核，而不是原生表单。 */
const val REGISTER_URL = "https://pawchive.pw/account/register"

/**
 * 应用内嵌的注册表单：直接加载站点的 `/account/register` 页面。
 *
 * 为什么必须用 WebView：站点注册要求人机验证（实测响应里写着
 * `Captcha verification failed. Please try again.`），原生表单 POST 无法完成验证码，
 * 因此改为在应用内嵌入官方页面，验证码在其中可正常显示与通过。
 *
 * 为让页面尽量一次打开成功，做了两件事（与 [CloudflareManager] 的约定一致）：
 * 1. User-Agent 用 [CloudflareManager.currentUserAgent] —— `cf_clearance` 与 UA 强绑定，
 *    换 UA 会让 Cloudflare 重新挑战；
 * 2. 把已拿到的过盾 cookie 预先写入 WebView 的 CookieManager，并允许第三方 cookie
 *    （验证码组件常依赖它）。
 *
 * 失败出口：加载失败给出明确原因，并提供「重试」与「在浏览器中打开」两个动作；
 * 设备没有可用系统 WebView 时直接引导到浏览器，而不是留一个空白框。
 */
@Composable
fun RegisterWebForm(
    modifier: Modifier = Modifier,
    /**
     * 页面里出现 `session` cookie 时回调（注册或登录成功后站点会 Set-Cookie），
     * 由调用方落库实现自动登录。
     */
    onSessionCookie: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    // 去重：同一次会话只回调一次，避免重复落库与重复提示
    var importedCookie by remember { mutableStateOf<String?>(null) }

    val webViewAvailable = remember {
        runCatching { WebView.getCurrentWebViewPackage() }.getOrNull() != null
    }

    if (!webViewAvailable) {
        // 极少数精简 ROM 没有系统 WebView：不给空白框，直接说明并引导到浏览器
        RegisterWebError(
            reason = stringResource(R.string.register_web_no_webview),
            modifier = modifier,
            onRetry = null,
        )
        return
    }

    val reason = error
    if (reason != null) {
        RegisterWebError(
            reason = reason,
            modifier = modifier,
            onRetry = {
                error = null
                loading = true
                reloadToken++
            },
        )
        return
    }

    Box(modifier) {
        // reloadToken 变化时重建 WebView：比重用旧实例更可靠（旧实例可能停在错误页）
        key(reloadToken) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    buildRegisterWebView(
                        context = ctx,
                        onFinished = { loading = false },
                        onMainFrameError = { description ->
                            // 这里在 WebView 回调里（非 Composable），只能用 context.getString
                            error = context.getString(R.string.register_web_failed, description)
                            loading = false
                        },
                        onSessionCookie = { cookie ->
                            if (cookie != importedCookie) {
                                importedCookie = cookie
                                onSessionCookie(cookie)
                            }
                        },
                    ).also { it.loadUrl(REGISTER_URL) }
                },
                // 自动登录后本组件会被移出组合树：销毁 WebView，避免泄漏
                onRelease = { web -> web.destroy() },
            )
        }
        if (loading) {
            AppLoading(Modifier.align(Alignment.Center))
        }
    }
}

/** 加载失败 / 无法内嵌时的提示块：原因 + 重试 + 浏览器出口。 */
@Composable
private fun RegisterWebError(reason: String, modifier: Modifier, onRetry: (() -> Unit)?) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(reason, style = AppTextStyle.SECONDARY)
        if (onRetry != null) {
            AppButton(stringResource(R.string.action_retry), onClick = onRetry)
        }
        AppButton(
            text = stringResource(R.string.register_web_open_browser),
            onClick = { openInExternalBrowser(context, REGISTER_URL) },
            primary = onRetry == null,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildRegisterWebView(
    context: Context,
    onFinished: () -> Unit,
    onMainFrameError: (String) -> Unit,
    onSessionCookie: (String) -> Unit,
): WebView = WebView(context).apply {
    val web = this
    settings.javaScriptEnabled = true // 站点表单与验证码都依赖 JS
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    // cf_clearance 与 UA 强绑定：必须用过盾时同一个 UA
    settings.userAgentString = CloudflareManager.currentUserAgent()

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(web, true) // 验证码组件常依赖第三方 cookie
        CloudflareManager.currentCookie()?.let { setCookie(REGISTER_URL, it) }
    }

    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            // 关键：该回调可能**在 Compose 正应用组合的过程中**被调用，此时直接写 Compose 状态
            // 会抛「Compose Runtime internal error … pending composition has not been applied」
            // 并整机闪退（实测堆栈：removeViewInternal → rootViewRequestFocus → requestFocus）。
            // 用 view.post 把回调挪到下一个消息循环，离开这个调用栈再改状态。
            view?.post {
                onFinished()
                // 注册/登录成功后站点会写入 `session` cookie —— 取出来交给调用方自动登录
                sessionCookie(url)?.let(onSessionCookie)
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            // 只把主文档失败当失败：图片/脚本等子资源出错不该判整页失败
            if (request?.isForMainFrame == true) {
                val description = error?.description?.toString() ?: "unknown"
                view?.post { onMainFrameError(description) }
            }
        }
    }
}

/**
 * 取出名为 `session` 的 cookie（站点用它做登录会话），形如 `session=xxxx`。
 *
 * 只取这一条、而不是整个 cookie 串：`cf_clearance` 由 [CloudflareManager] 单独管理，
 * 把它一起存进来会出现两份 clearance 互相覆盖。返回的字符串可直接作为
 * `Cookie` 请求头使用（与会话的手动导入格式一致）。
 */
private fun sessionCookie(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val raw = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() ?: return null
    return raw.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("session=") && it.length > "session=".length }
}

/**
 * 用系统浏览器打开链接。
 *
 * 系统没有浏览器、或没有任何应用能处理该链接时给出明确提示（并带上地址，
 * 用户至少可以复制访问），而不是点了没反应。
 */
fun openInExternalBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.open_browser_failed, url), Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.open_browser_failed, url), Toast.LENGTH_LONG).show()
    }
}
