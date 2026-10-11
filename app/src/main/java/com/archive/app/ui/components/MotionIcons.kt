package com.archive.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archive.app.ui.theme.LocalPurrColors

/**
 * Morphing icons drawn on canvas so the transitions are true vector
 * deformations (hamburger↔close, play↔pause, plus↔close) — no crossfade of
 * unrelated bitmaps.
 *
 * Geometry is the official Lucide (https://lucide.dev) 24×24 stroke geometry,
 * expressed in normalized units so it scales with [iconSize]:
 *  - menu:   lines y=6/12/18, x 4→20
 *  - x:      M18 6 L6 18 / M6 6 L18 18
 *  - plus:   M5 12h14 / M12 5v14
 *  - play:   polygon 6,3 20,12 6,21
 *  - pause:  bars x=6 and x=14, width 4, y 4→20
 *  - check:  M20 6 L9 17 L4 12
 * Stroke width 2/24 and round caps/joins match the Lucide set.
 */
private const val UNIT = 1f / 24f

/** Three-line menu that deforms into an X when [checked]. */
@Composable
fun MenuCloseIcon(
    checked: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = LocalPurrColors.current.textPrimary,
) {
    val p by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "menuCloseMorph",
    )
    Canvas(modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = w * UNIT * 2f
        val leftX = w * 4f * UNIT
        val rightX = w * 20f * UNIT
        val topY = h * 6f * UNIT
        val midY = h * 12f * UNIT
        val bottomY = h * 18f * UNIT
        val cx = w / 2
        val cy = h / 2

        // top line slides to the middle then rotates to "M18 6 L6 18"
        rotate(degrees = -45f * p, pivot = Offset(cx, cy)) {
            drawLine(
                color = tint,
                start = Offset(leftX, topY + (midY - topY) * p),
                end = Offset(rightX, topY + (midY - topY) * p),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // bottom line mirrors it: "M6 6 L18 18"
        rotate(degrees = 45f * p, pivot = Offset(cx, cy)) {
            drawLine(
                color = tint,
                start = Offset(leftX, bottomY - (bottomY - midY) * p),
                end = Offset(rightX, bottomY - (bottomY - midY) * p),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // middle line fades out
        if (p < 0.99f) {
            drawLine(
                color = tint.copy(alpha = 1f - p),
                start = Offset(leftX, midY),
                end = Offset(rightX, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Plus sign (Lucide "plus") that rotates 45° into an X. */
@Composable
fun MorphPlusIcon(
    open: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    tint: Color = LocalPurrColors.current.onPrimary,
) {
    val p by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "plusMorph",
    )
    Canvas(modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * UNIT * 2.2f
        val half = w * 7f * UNIT
        val cx = w / 2
        val cy = size.height / 2
        rotate(degrees = 45f * p, pivot = Offset(cx, cy)) {
            drawLine(tint, Offset(cx - half, cy), Offset(cx + half, cy), stroke, StrokeCap.Round)
            drawLine(tint, Offset(cx, cy - half), Offset(cx, cy + half), stroke, StrokeCap.Round)
        }
    }
}

/** Lucide play triangle that morphs into Lucide pause bars. */
@Composable
fun PlayPauseIcon(
    playing: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    tint: Color = LocalPurrColors.current.textPrimary,
) {
    val p by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "playPauseMorph",
    )
    Canvas(modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = w * UNIT * 2f
        // Lucide play: polygon 6,3 20,12 6,21
        val playLeft = w * 6f * UNIT
        val playTop = h * 3f * UNIT
        val playRight = w * 20f * UNIT
        val playTip = h * 12f * UNIT
        val playBottom = h * 21f * UNIT
        // Lucide pause: bars centered at x=8 and x=16, y 4→20
        val barA = w * 8f * UNIT
        val barB = w * 16f * UNIT
        val barTop = if (playing) h * 4f * UNIT else playTop
        val barBottom = if (playing) h * 20f * UNIT else playBottom

        val triangleAlpha = 1f - p
        if (triangleAlpha > 0.01f) {
            val path = Path().apply {
                moveTo(playLeft, playTop + (barTop - playTop) * p)
                lineTo(playRight - (playRight - barB) * p, playTip)
                lineTo(playLeft, playBottom - (playBottom - barBottom) * p)
                close()
            }
            drawPath(
                path,
                color = tint.copy(alpha = triangleAlpha),
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        if (p > 0.01f) {
            drawLine(
                tint.copy(alpha = p),
                Offset(barA, barTop),
                Offset(barA, barBottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                tint.copy(alpha = p),
                Offset(barB, barTop),
                Offset(barB, barBottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Lucide check mark, drawn on progressively (M20 6 L9 17 L4 12). */
@Composable
fun CheckMorphIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    tint: Color = LocalPurrColors.current.primary,
) {
    val p by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "checkMorph",
    )
    Canvas(modifier.size(iconSize)) {
        if (p <= 0.01f) return@Canvas
        val w = size.width
        val h = size.height
        val stroke = w * UNIT * 2f
        val ax = w * 20f * UNIT
        val ay = h * 6f * UNIT
        val bx = w * 9f * UNIT
        val by = h * 17f * UNIT
        val cx = w * 4f * UNIT
        val cy = h * 12f * UNIT
        // segment lengths: A→B ≈ 15.56, B→C ≈ 7.07 (total 22.63)
        val first = 15.56f / 22.63f
        val out = Path()
        out.moveTo(ax, ay)
        if (p <= first) {
            val f1 = (p / first).coerceIn(0f, 1f)
            out.lineTo(ax + (bx - ax) * f1, ay + (by - ay) * f1)
        } else {
            out.lineTo(bx, by)
            val f2 = ((p - first) / (1f - first)).coerceIn(0f, 1f)
            out.lineTo(bx + (cx - bx) * f2, by + (cy - by) * f2)
        }
        drawPath(out, color = tint, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
