package com.archive.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archive.app.data.remote.UrlProvider
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.HtmlNode
import com.archive.app.util.HtmlParser

/**
 * Renders the small HTML subset used by post bodies (p/br/b/i/u/a/img).
 * Links open through the system browser; embedded images resolve relative
 * paths against the configured base URL.
 */
@Composable
fun HtmlText(html: String, baseUrl: String, modifier: Modifier = Modifier) {
    val nodes = remember(html) { HtmlParser.parse(html) }
    val colors = LocalPurrColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        nodes.forEach { node ->
            when (node) {
                is HtmlNode.Image -> ThumbImage(
                    url = UrlProvider.contentImageUrl(baseUrl, node.src),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentDescription = "正文图片",
                    blurredOverride = false,
                )
                is HtmlNode.Paragraph -> Text(
                    buildAnnotatedString {
                        node.spans.forEach { span ->
                            val style = SpanStyle(
                                fontWeight = if (span.bold) FontWeight.Bold else null,
                                fontStyle = if (span.italic) FontStyle.Italic else null,
                                textDecoration = when {
                                    span.underline -> TextDecoration.Underline
                                    span.link != null -> TextDecoration.Underline
                                    else -> null
                                },
                                color = if (span.link != null) colors.primary else Color.Unspecified,
                            )
                            val link = span.link
                            if (link != null) {
                                withLink(
                                    LinkAnnotation.Url(
                                        link,
                                        TextLinkStyles(style = style),
                                    ),
                                ) { append(span.text) }
                            } else {
                                withStyle(style) { append(span.text) }
                            }
                        }
                    },
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    color = colors.textPrimary,
                )
            }
        }
    }
}
