package com.archive.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.archive.app.R
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors

/** 官方站点地址（首条要点里的超链接指向这里）。 */
private const val PAWCHIVE_HOME_URL = "https://pawchive.pw/"

/** 要点行：勾选图标 + 内容（内容用 ColumnScope 里的普通 Composable，便于放富文本）。 */
@Composable
private fun Bullet(content: @Composable () -> Unit) {
    val colors = LocalPurrColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = LucideIcons.check,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp),
        )
        content()
    }
}

/**
 * 首条要点「本应用是 Pawchive 的第三方客户端。」——其中「Pawchive」为超链接。
 *
 * 实现与帖子正文里的链接一致（`AnnotatedString` + `LinkAnnotation.Url`，见 `HtmlText`），
 * 点击由 Compose 交给系统浏览器打开。文字样式直接沿用 `AppTextStyle.SECONDARY` 对应的
 * `body2` 文本样式、颜色用 `textPrimary`（主题里等于 Miuix 的 `onBackground`，即相邻要点的
 * 默认正文色），因此除了链接词换成主题色 + 下划线，这行与其余要点完全一致。
 */
@Composable
private fun PawchiveSentence() {
    val colors = LocalPurrColors.current
    val body = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2
    val sentence = buildAnnotatedString {
        append("本应用是 ")
        withLink(
            LinkAnnotation.Url(
                PAWCHIVE_HOME_URL,
                // 注意 TextLinkStyles 收的是 SpanStyle（不是 TextStyle）：
                // 不设 fontSize/字体族，链接词自动继承外层 body2
                TextLinkStyles(
                    style = androidx.compose.ui.text.SpanStyle(
                        color = colors.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        ) { append("Pawchive") }
        append(" 的第三方客户端。")
    }
    androidx.compose.material3.Text(text = sentence, style = body, color = colors.textPrimary)
}

/**
 * 首次启动的 18+ 年龄确认页。
 *
 * 只在**从未确认过**时出现一次：点「继续」会把结果写进 DataStore（`AppSettings.ageConfirmed`），
 * 之后每次启动都由 `ArchiveRoot` 直接进主界面（它会先等磁盘值读出来，所以也不会闪这一页）。
 *
 * 排版要点：原来的三行说明挤成一大段，扫读成本高；这里拆成「标识 → 18+ 徽章 → 分条要点 →
 * 主/次按钮」，并为小屏加了滚动，避免按钮被挤出屏幕。
 */
@Composable
fun AgeGateScreen(onConfirm: () -> Unit) {
    val colors = LocalPurrColors.current
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            // 内容不满一屏时居中，超出时可滚动（小屏 / 大字号都不会被裁掉）
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 应用标识
                // 与桌面图标同一张图：整块填满 72dp 圆角方块（原来是 48dp 的矢量爪印，
                // 那样在新图标下会变成「浅色方块里嵌一张小图」，观感不一致）。
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AppText(
                    "Archive",
                    style = AppTextStyle.TITLE,
                    modifier = Modifier.padding(top = 16.dp),
                )

                // 18+ 徽章：让这页的性质一眼可辨
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    AppText("18+", style = AppTextStyle.SMALL, color = colors.primary)
                }

                AppText(
                    "成人内容提示",
                    style = AppTextStyle.SECONDARY,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )

                // 分条要点：四句短句，代替原来的一整段
                Column(
                    Modifier.padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 首条要点里的「Pawchive」是可点击的超链接
                    Bullet { PawchiveSentence() }
                    listOf(
                        "站点内容来自 Patreon / Fanbox 等平台，可能包含成人内容（18+）。",
                        "继续使用即表示你确认已年满 18 周岁，且在所在地浏览此类内容合法。",
                        "仅作为客户端展示内容：不内置、不转存任何内容，不上传用户数据，不含统计 SDK。",
                    ).forEach { line ->
                        Bullet { AppText(line, style = AppTextStyle.SECONDARY) }
                    }
                }

                AppButton(
                    text = "我已年满 18 周岁，继续",
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                )
                // 次要动作：不接受就退出，而不是留在这个页面上（本页不可绕过）
                AppButton(
                    text = "未满 18 周岁，退出",
                    onClick = { (context as? Activity)?.finishAffinity() },
                    primary = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
        }
    }
}
