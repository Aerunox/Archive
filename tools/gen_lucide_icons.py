#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fetch official Lucide SVGs and generate a Compose ImageVector registry.
Lucide is ISC licensed; geometry is used verbatim from the upstream sources.
"""
import os, re, urllib.request

ICONS = {
    # kotlin property  -> lucide file
    "back": "arrow-left",
    "arrowUp": "arrow-up",
    "chevronForward": "chevron-right",
    "expander": "chevron-down",
    "close": "x",
    "search": "search",
    "link": "link",
    "externalLink": "external-link",
    "home": "house",
    "discover": "compass",
    "favorites": "heart",
    "alarm": "bell",
    "messages": "message-circle",
    "settings": "settings",
    "info": "info",
    "help": "circle-question-mark",
    "folder": "folder",
    "downloads": "folder-down",
    "download": "download",
    "tasks": "list-clock",
    "delete": "trash",
    "refresh": "refresh-cw",
    "gridView": "layout-grid",
    "listView": "list",
    "play": "play",
    "check": "check",
    "expand": "maximize",
    "collapse": "minimize",
    "pause": "pause",
    "mine": "user-round",
    # 图片查看器（对照微信看图界面：分享/转发、更多）
    "forward": "forward",
    "ellipsis": "ellipsis",
}

CACHE = "/tmp/lucide_svg"
os.makedirs(CACHE, exist_ok=True)

def fetch(name: str) -> str:
    path = os.path.join(CACHE, name + ".svg")
    if not os.path.exists(path):
        url = f"https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/{name}.svg"
        with urllib.request.urlopen(url, timeout=30) as r:
            data = r.read().decode("utf-8")
        open(path, "w", encoding="utf-8").write(data)
    return open(path, encoding="utf-8").read()

def attrs(tag: str) -> dict:
    return dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', tag))

def num(d: dict, key: str, default=0.0) -> float:
    v = d.get(key)
    if v is None:
        return default
    try:
        return float(v)
    except ValueError:
        return default

def f(x: float) -> str:
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return s if s else "0"

def svg_to_paths(svg: str):
    """Return list of (pathData, closed_fill_hint)."""
    body = svg[svg.index(">", svg.index("<svg")) + 1: svg.rindex("</svg>")]
    out = []
    for tag in re.findall(r"<(path|circle|line|rect|polyline|polygon)\b[^>]*/?>", body):
        pass
    for m in re.finditer(r"<(path|circle|line|rect|polyline|polygon)\b([^>]*?)/?>", body):
        kind, raw = m.group(1), m.group(2)
        a = attrs(raw)
        if kind == "path":
            out.append(a.get("d", ""))
        elif kind == "circle":
            cx, cy, r = num(a, "cx"), num(a, "cy"), num(a, "r")
            out.append(
                f"M {f(cx - r)},{f(cy)} a {f(r)},{f(r)} 0 1,0 {f(2 * r)},0 "
                f"a {f(r)},{f(r)} 0 1,0 {f(-2 * r)},0"
            )
        elif kind == "line":
            out.append(
                f"M {f(num(a, 'x1'))},{f(num(a, 'y1'))} L {f(num(a, 'x2'))},{f(num(a, 'y2'))}"
            )
        elif kind == "rect":
            x, y, w, h, rx = num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height"), num(a, "rx")
            if rx <= 0:
                out.append(f"M {f(x)},{f(y)} L {f(x + w)},{f(y)} L {f(x + w)},{f(y + h)} L {f(x)},{f(y + h)} Z")
            else:
                out.append(
                    f"M {f(x + rx)},{f(y)} L {f(x + w - rx)},{f(y)} "
                    f"A {f(rx)},{f(rx)} 0 0 1 {f(x + w)},{f(y + rx)} "
                    f"L {f(x + w)},{f(y + h - rx)} "
                    f"A {f(rx)},{f(rx)} 0 0 1 {f(x + w - rx)},{f(y + h)} "
                    f"L {f(x + rx)},{f(y + h)} "
                    f"A {f(rx)},{f(rx)} 0 0 1 {f(x)},{f(y + h - rx)} "
                    f"L {f(x)},{f(y + rx)} "
                    f"A {f(rx)},{f(rx)} 0 0 1 {f(x + rx)},{f(y)} Z"
                )
        elif kind in ("polyline", "polygon"):
            pts = [p for p in re.split(r"[ ,]+", a.get("points", "").strip()) if p]
            pairs = list(zip(pts[0::2], pts[1::2]))
            if pairs:
                d = f"M {pairs[0][0]},{pairs[0][1]}"
                for px, py in pairs[1:]:
                    d += f" L {px},{py}"
                if kind == "polygon":
                    d += " Z"
                out.append(d)
    return [p for p in out if p]

def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")

header = '''package com.archive.app.ui.design.lucide

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
'''

footer = '''
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
'''

entries = []
for prop, lucide in ICONS.items():
    svg = fetch(lucide)
    paths = svg_to_paths(svg)
    assert paths, f"no paths parsed for {lucide}"
    body = ",\n".join(f'            "{esc(p)}"' for p in paths)
    entries.append(f'''
    private var _{prop}: ImageVector? = null
    val {prop}: ImageVector
        get() = _{prop} ?: stroke(
            name = "{lucide}",
            paths = listOf(
{body},
            ),
        ).also {{ _{prop} = it }}
''')

# filled heart variant (same geometry, filled)
heart = svg_to_paths(fetch("heart"))
heart_body = ",\n".join(f'            "{esc(p)}"' for p in heart)
entries.append(f'''
    private var _favoritesFill: ImageVector? = null
    val favoritesFill: ImageVector
        get() = _favoritesFill ?: stroke(
            name = "heart-filled",
            paths = listOf(
{heart_body},
            ),
            filled = true,
        ).also {{ _favoritesFill = it }}
''')

out_dir = "app/src/main/java/com/archive/app/ui/design/lucide"
os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, "LucideIcons.kt"), "w", encoding="utf-8", newline="") as fp:
    fp.write(header)
    fp.writelines(entries)
    fp.write(footer)
print("generated", os.path.join(out_dir, "LucideIcons.kt"), "icons:", len(ICONS) + 1)
