#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""合成 Archive 画廊融合图标：爪印 × 画框/相框/相册堆。素材来自开源图标集（Lucide ISC / Remix Apache-2.0 / MDI Apache-2.0 / Bootstrap MIT）。"""
import re, os, json

ICONS = r"C:\Users\21883\Desktop\Purrchive\docs\icons"
OUT = r"C:\Users\21883\Desktop\Purrchive\docs\icons\fusion"
os.makedirs(OUT, exist_ok=True)

def read(name):
    return open(os.path.join(ICONS, name), encoding="utf-8").read()

def glyph_of(svg):
    inner = re.sub(r'<svg[^>]*>', '', svg).replace('</svg>', '')
    inner = re.sub(r'<path d="M0 0h\d+v\d+H0z"[^>]*/>', '', inner)
    return inner.strip()

def recolor(inner, new="#2B2B2B"):
    return inner.replace("currentColor", new)

def wrap(inner, tx, ty, sc):
    return f'<g transform="translate({tx} {ty}) scale({sc})">{inner}</g>'

def svg24(body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24">'
            f'<path d="M0 0h24v24H0z" fill="none"/>{body}</svg>')

# 素材
paw_lucide = glyph_of(read("lucide-paw-print.svg"))
paw_pets = glyph_of(read("material-symbols-pets.svg"))
gallery = json.load(open(r"C:\Users\21883\Desktop\Purrchive\docs\_gallery_icons.json", encoding="utf-8"))

concepts = {}

# 1) 画框（描边）: lucide:image 去掉太阳圆与山形，只留圆角框 + 爪印
img_svg = gallery["lucide_image"]["svg"]
frame_inner = re.sub(r'<circle[^>]*/>', '', glyph_of(img_svg))
frame_inner = re.sub(r'<path d="m21 15[^"]*"[^>]*/>', '', frame_inner)
concepts["frame-stroke"] = {
    "name": "画框 × 爪印（描边）", "set": "Lucide 组合 · ISC",
    "duo":  frame_inner.replace("currentColor", "#2B2B2B") + wrap(recolor(paw_lucide, "#EA580C"), 7.2, 7.9, 0.5),
    "mono": frame_inner.replace("currentColor", "#2B2B2B") + wrap(recolor(paw_lucide, "#2B2B2B"), 7.2, 7.9, 0.5),
}

# 2) 画廊画框（实心）: ri:gallery-line 整图保留，爪印盖在画面中央
ri_inner = glyph_of(gallery["ri_gallery"]["svg"])
concepts["frame-fill"] = {
    "name": "画廊画框 × 爪印（实心）", "set": "Remix 组合 · Apache 2.0",
    "duo":  recolor(ri_inner, "#2B2B2B") + wrap(recolor(paw_pets, "#EA580C"), 7.2, 8.0, 0.45),
    "mono": recolor(ri_inner, "#2B2B2B") + wrap(recolor(paw_pets, "#2B2B2B"), 7.2, 8.0, 0.45),
}

# 3) 双画框（实心）: mdi:image-multiple，爪印在前框中央偏下
multi_inner = glyph_of(gallery["mdi_imagemulti"]["svg"])
concepts["multi"] = {
    "name": "双画框 × 爪印（实心）", "set": "MDI 组合 · Apache 2.0",
    "duo":  recolor(multi_inner, "#2B2B2B") + wrap(recolor(paw_pets, "#EA580C"), 10.4, 10.6, 0.32),
    "mono": recolor(multi_inner, "#2B2B2B") + wrap(recolor(paw_pets, "#2B2B2B"), 10.4, 10.6, 0.32),
}

# 4) 相册堆（实心）: bi:collection（16 单位 -> 缩放到 24），爪印在底层大卡上
col_inner = glyph_of(gallery["bi_collection"]["svg"])
col24 = f'<g transform="scale(1.5)">{col_inner}</g>'
concepts["stack"] = {
    "name": "相册堆 × 爪印（实心）", "set": "Bootstrap 组合 · MIT",
    "duo":  recolor(col24, "#2B2B2B") + wrap(recolor(paw_pets, "#EA580C"), 7.0, 9.2, 0.42),
    "mono": recolor(col24, "#2B2B2B") + wrap(recolor(paw_pets, "#2B2B2B"), 7.0, 9.2, 0.42),
}

for key, v in concepts.items():
    for kind in ("duo", "mono"):
        open(os.path.join(OUT, f"gallery-{key}-{kind}.svg"), "w", encoding="utf-8").write(svg24(v[kind]))
    v["duo_svg"] = svg24(v["duo"])
    v["mono_svg"] = svg24(v["mono"])

json.dump(concepts, open(r"C:\Users\21883\Desktop\Purrchive\docs\_gallery_fusion_data.json", "w", encoding="utf-8"), ensure_ascii=False)
print("saved", len(concepts), "concepts")
for k, v in concepts.items():
    print(" ", k, "|", v["name"])
