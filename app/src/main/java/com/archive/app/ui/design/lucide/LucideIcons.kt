package com.archive.app.ui.design.lucide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (https://lucide.dev) as Compose [ImageVector]s.
 *
 * GENERATED FILE — do not edit by hand. Regenerate with tools/gen_lucide_icons.py,
 * which downloads the official SVGs and converts their geometry verbatim.
 * Icons are stroke based (24x24 viewport, stroke-width 2, round caps/joins), so
 * Icon(tint = …) recolours them like any other vector.
 */
object LucideIcons {

    private var _back: ImageVector? = null
    val back: ImageVector
        get() = _back ?: stroke(
            name = "arrow-left",
            paths = listOf(
            "m12 19-7-7 7-7",
            "M19 12H5",
            ),
        ).also { _back = it }

    private var _arrowUp: ImageVector? = null
    val arrowUp: ImageVector
        get() = _arrowUp ?: stroke(
            name = "arrow-up",
            paths = listOf(
            "m5 12 7-7 7 7",
            "M12 19V5",
            ),
        ).also { _arrowUp = it }

    private var _chevronForward: ImageVector? = null
    val chevronForward: ImageVector
        get() = _chevronForward ?: stroke(
            name = "chevron-right",
            paths = listOf(
            "m9 18 6-6-6-6",
            ),
        ).also { _chevronForward = it }

    private var _expander: ImageVector? = null
    val expander: ImageVector
        get() = _expander ?: stroke(
            name = "chevron-down",
            paths = listOf(
            "m6 9 6 6 6-6",
            ),
        ).also { _expander = it }

    private var _close: ImageVector? = null
    val close: ImageVector
        get() = _close ?: stroke(
            name = "x",
            paths = listOf(
            "M18 6 6 18",
            "m6 6 12 12",
            ),
        ).also { _close = it }

    private var _search: ImageVector? = null
    val search: ImageVector
        get() = _search ?: stroke(
            name = "search",
            paths = listOf(
            "m21 21-4.34-4.34",
            "M 3,11 a 8,8 0 1,0 16,0 a 8,8 0 1,0 -16,0",
            ),
        ).also { _search = it }

    private var _link: ImageVector? = null
    val link: ImageVector
        get() = _link ?: stroke(
            name = "link",
            paths = listOf(
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
            ),
        ).also { _link = it }

    private var _externalLink: ImageVector? = null
    val externalLink: ImageVector
        get() = _externalLink ?: stroke(
            name = "external-link",
            paths = listOf(
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
            ),
        ).also { _externalLink = it }

    private var _home: ImageVector? = null
    val home: ImageVector
        get() = _home ?: stroke(
            name = "house",
            paths = listOf(
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            ),
        ).also { _home = it }

    private var _discover: ImageVector? = null
    val discover: ImageVector
        get() = _discover ?: stroke(
            name = "compass",
            paths = listOf(
            "M 2,12 a 10,10 0 1,0 20,0 a 10,10 0 1,0 -20,0",
            "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z",
            ),
        ).also { _discover = it }

    private var _favorites: ImageVector? = null
    val favorites: ImageVector
        get() = _favorites ?: stroke(
            name = "heart",
            paths = listOf(
            "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
            ),
        ).also { _favorites = it }

    private var _alarm: ImageVector? = null
    val alarm: ImageVector
        get() = _alarm ?: stroke(
            name = "bell",
            paths = listOf(
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
            ),
        ).also { _alarm = it }

    private var _messages: ImageVector? = null
    val messages: ImageVector
        get() = _messages ?: stroke(
            name = "message-circle",
            paths = listOf(
            "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
            ),
        ).also { _messages = it }

    private var _settings: ImageVector? = null
    val settings: ImageVector
        get() = _settings ?: stroke(
            name = "settings",
            paths = listOf(
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M 9,12 a 3,3 0 1,0 6,0 a 3,3 0 1,0 -6,0",
            ),
        ).also { _settings = it }

    private var _info: ImageVector? = null
    val info: ImageVector
        get() = _info ?: stroke(
            name = "info",
            paths = listOf(
            "M 2,12 a 10,10 0 1,0 20,0 a 10,10 0 1,0 -20,0",
            "M12 16v-4",
            "M12 8h.01",
            ),
        ).also { _info = it }

    private var _help: ImageVector? = null
    val help: ImageVector
        get() = _help ?: stroke(
            name = "circle-question-mark",
            paths = listOf(
            "M 2,12 a 10,10 0 1,0 20,0 a 10,10 0 1,0 -20,0",
            "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3",
            "M12 17h.01",
            ),
        ).also { _help = it }

    private var _folder: ImageVector? = null
    val folder: ImageVector
        get() = _folder ?: stroke(
            name = "folder",
            paths = listOf(
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
            ),
        ).also { _folder = it }

    private var _downloads: ImageVector? = null
    val downloads: ImageVector
        get() = _downloads ?: stroke(
            name = "folder-down",
            paths = listOf(
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
            "M12 10v6",
            "m15 13-3 3-3-3",
            ),
        ).also { _downloads = it }

    private var _download: ImageVector? = null
    val download: ImageVector
        get() = _download ?: stroke(
            name = "download",
            paths = listOf(
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5",
            ),
        ).also { _download = it }

    private var _tasks: ImageVector? = null
    val tasks: ImageVector
        get() = _tasks ?: stroke(
            name = "list-clock",
            paths = listOf(
            "M16 13v2.2l1.6 1",
            "M3 12h3.458",
            "M3 19h3.832",
            "M3 5h18",
            "M 10,15 a 6,6 0 1,0 12,0 a 6,6 0 1,0 -12,0",
            ),
        ).also { _tasks = it }

    private var _delete: ImageVector? = null
    val delete: ImageVector
        get() = _delete ?: stroke(
            name = "trash",
            paths = listOf(
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
            ),
        ).also { _delete = it }

    private var _refresh: ImageVector? = null
    val refresh: ImageVector
        get() = _refresh ?: stroke(
            name = "refresh-cw",
            paths = listOf(
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
            ),
        ).also { _refresh = it }

    private var _gridView: ImageVector? = null
    val gridView: ImageVector
        get() = _gridView ?: stroke(
            name = "layout-grid",
            paths = listOf(
            "M 4,3 L 9,3 A 1,1 0 0 1 10,4 L 10,9 A 1,1 0 0 1 9,10 L 4,10 A 1,1 0 0 1 3,9 L 3,4 A 1,1 0 0 1 4,3 Z",
            "M 15,3 L 20,3 A 1,1 0 0 1 21,4 L 21,9 A 1,1 0 0 1 20,10 L 15,10 A 1,1 0 0 1 14,9 L 14,4 A 1,1 0 0 1 15,3 Z",
            "M 15,14 L 20,14 A 1,1 0 0 1 21,15 L 21,20 A 1,1 0 0 1 20,21 L 15,21 A 1,1 0 0 1 14,20 L 14,15 A 1,1 0 0 1 15,14 Z",
            "M 4,14 L 9,14 A 1,1 0 0 1 10,15 L 10,20 A 1,1 0 0 1 9,21 L 4,21 A 1,1 0 0 1 3,20 L 3,15 A 1,1 0 0 1 4,14 Z",
            ),
        ).also { _gridView = it }

    private var _listView: ImageVector? = null
    val listView: ImageVector
        get() = _listView ?: stroke(
            name = "list",
            paths = listOf(
            "M3 5h.01",
            "M3 12h.01",
            "M3 19h.01",
            "M8 5h13",
            "M8 12h13",
            "M8 19h13",
            ),
        ).also { _listView = it }

    private var _play: ImageVector? = null
    val play: ImageVector
        get() = _play ?: stroke(
            name = "play",
            paths = listOf(
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
            ),
        ).also { _play = it }

    private var _check: ImageVector? = null
    val check: ImageVector
        get() = _check ?: stroke(
            name = "check",
            paths = listOf(
            "M20 6 9 17l-5-5",
            ),
        ).also { _check = it }

    private var _expand: ImageVector? = null
    val expand: ImageVector
        get() = _expand ?: stroke(
            name = "maximize",
            paths = listOf(
            "M8 3H5a2 2 0 0 0-2 2v3",
            "M21 8V5a2 2 0 0 0-2-2h-3",
            "M3 16v3a2 2 0 0 0 2 2h3",
            "M16 21h3a2 2 0 0 0 2-2v-3",
            ),
        ).also { _expand = it }

    private var _collapse: ImageVector? = null
    val collapse: ImageVector
        get() = _collapse ?: stroke(
            name = "minimize",
            paths = listOf(
            "M8 3v3a2 2 0 0 1-2 2H3",
            "M21 8h-3a2 2 0 0 1-2-2V3",
            "M3 16h3a2 2 0 0 1 2 2v3",
            "M16 21v-3a2 2 0 0 1 2-2h3",
            ),
        ).also { _collapse = it }

    private var _pause: ImageVector? = null
    val pause: ImageVector
        get() = _pause ?: stroke(
            name = "pause",
            paths = listOf(
            "M 15,3 L 18,3 A 1,1 0 0 1 19,4 L 19,20 A 1,1 0 0 1 18,21 L 15,21 A 1,1 0 0 1 14,20 L 14,4 A 1,1 0 0 1 15,3 Z",
            "M 6,3 L 9,3 A 1,1 0 0 1 10,4 L 10,20 A 1,1 0 0 1 9,21 L 6,21 A 1,1 0 0 1 5,20 L 5,4 A 1,1 0 0 1 6,3 Z",
            ),
        ).also { _pause = it }

    private var _mine: ImageVector? = null
    val mine: ImageVector
        get() = _mine ?: stroke(
            name = "user-round",
            paths = listOf(
            "M 7,8 a 5,5 0 1,0 10,0 a 5,5 0 1,0 -10,0",
            "M20 21a8 8 0 0 0-16 0",
            ),
        ).also { _mine = it }

    private var _forward: ImageVector? = null
    val forward: ImageVector
        get() = _forward ?: stroke(
            name = "forward",
            paths = listOf(
            "m15 17 5-5-5-5",
            "M4 18v-2a4 4 0 0 1 4-4h12",
            ),
        ).also { _forward = it }

    private var _ellipsis: ImageVector? = null
    val ellipsis: ImageVector
        get() = _ellipsis ?: stroke(
            name = "ellipsis",
            paths = listOf(
            "M 11,12 a 1,1 0 1,0 2,0 a 1,1 0 1,0 -2,0",
            "M 18,12 a 1,1 0 1,0 2,0 a 1,1 0 1,0 -2,0",
            "M 4,12 a 1,1 0 1,0 2,0 a 1,1 0 1,0 -2,0",
            ),
        ).also { _ellipsis = it }

    private var _palette: ImageVector? = null
    val palette: ImageVector
        get() = _palette ?: stroke(
            name = "palette",
            paths = listOf(
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
            "M 13,6.5 a 0.5,0.5 0 1,0 1,0 a 0.5,0.5 0 1,0 -1,0",
            "M 17,10.5 a 0.5,0.5 0 1,0 1,0 a 0.5,0.5 0 1,0 -1,0",
            "M 6,12.5 a 0.5,0.5 0 1,0 1,0 a 0.5,0.5 0 1,0 -1,0",
            "M 8,7.5 a 0.5,0.5 0 1,0 1,0 a 0.5,0.5 0 1,0 -1,0",
            ),
        ).also { _palette = it }

    private var _shield: ImageVector? = null
    val shield: ImageVector
        get() = _shield ?: stroke(
            name = "shield",
            paths = listOf(
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            ),
        ).also { _shield = it }

    private var _globe: ImageVector? = null
    val globe: ImageVector
        get() = _globe ?: stroke(
            name = "globe",
            paths = listOf(
            "M 2,12 a 10,10 0 1,0 20,0 a 10,10 0 1,0 -20,0",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20",
            ),
        ).also { _globe = it }

    private var _sparkles: ImageVector? = null
    val sparkles: ImageVector
        get() = _sparkles ?: stroke(
            name = "sparkles",
            paths = listOf(
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M 2,20 a 2,2 0 1,0 4,0 a 2,2 0 1,0 -4,0",
            ),
        ).also { _sparkles = it }

    private var _languages: ImageVector? = null
    val languages: ImageVector
        get() = _languages ?: stroke(
            name = "languages",
            paths = listOf(
            "m5 8 6 6",
            "m4 14 6-6 2-3",
            "M2 5h12",
            "M7 2h1",
            "m22 22-5-10-5 10",
            "M14 18h6",
            ),
        ).also { _languages = it }

    private var _ban: ImageVector? = null
    val ban: ImageVector
        get() = _ban ?: stroke(
            name = "ban",
            paths = listOf(
            "M 2,12 a 10,10 0 1,0 20,0 a 10,10 0 1,0 -20,0",
            "M4.929 4.929 19.07 19.071",
            ),
        ).also { _ban = it }

    private var _hardDrive: ImageVector? = null
    val hardDrive: ImageVector
        get() = _hardDrive ?: stroke(
            name = "hard-drive",
            paths = listOf(
            "M10 16h.01",
            "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
            "M21.946 12.013H2.054",
            "M6 16h.01",
            ),
        ).also { _hardDrive = it }

    private var _favoritesFill: ImageVector? = null
    val favoritesFill: ImageVector
        get() = _favoritesFill ?: stroke(
            name = "heart-filled",
            paths = listOf(
            "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
            ),
            filled = true,
        ).also { _favoritesFill = it }

    private fun stroke(
        name: String,
        paths: List<String>,
        filled: Boolean = false,
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
