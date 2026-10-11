#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""合成 Archive 融合图标：爪印 × 档案/书/漫画气泡。素材全部来自开源图标集，可再分发。"""
import re, os, json

ICONS = r"C:\Users\21883\Desktop\Purrchive\docs\icons"
OUT = r"C:\Users\21883\Desktop\Purrchive\docs\icons\fusion"
os.makedirs(OUT, exist_ok=True)

def read(name):
    return open(os.path.join(ICONS, name), encoding="utf-8").read()

def glyph_of(svg, keep="paths"):
    """去掉背景方框 path，返回 svg 标签内部剩余内容（不含外层 svg 标签）。"""
    inner = re.sub(r'<svg[^>]*>', '', svg)
    inner = inner.replace('</svg>', '')
    # 去掉 M0 0h24v24H0z 之类的背景框
    inner = re.sub(r'<path d="M0 0h\d+v\d+H0z"[^>]*/>', '', inner)
    return inner.strip()

def wrap_transform(inner, tx, ty, sc):
    return f'<g transform="translate({tx} {ty}) scale({sc})">{inner}</g>'

# ---- 素材 ----
paw_lucide_svg = read("lucide-paw-print.svg")          # stroke 爪印
paw_pets_svg = read("material-symbols-pets.svg")        # fill 爪印
archive_lucide_svg = read("lucide-archive.svg")         # stroke 档案盒
archive_mdi_svg = read("archive-mdi-outline.svg")       # fill 档案盒
book_mdi_svg = read("read-mdi-book-open-page.svg")      # fill 翻页书
book_lucide_svg = read("read-lucide-book-open.svg")     # stroke 开卷书
bubble_iconoir_svg = read("read-iconoir-chat-bubble.svg")  # stroke 气泡

# 爪印内部内容
paw_lucide_inner = glyph_of(paw_lucide_svg)   # stroke 爪印内容（含 circle + path，stroke=currentColor）
paw_pets_inner = glyph_of(paw_pets_svg)       # fill 爪印 path

def mono_svg(inner, view="0 0 24 24"):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="{view}">'
            f'<path d="M0 0h24v24H0z" fill="none"/>{inner}</svg>')

def recolor(inner, old="currentColor", new="#2B2B2B"):
    return inner.replace(old, new)

def build(container_inner, paw_inner, paw_style, tx, ty, sc,
          container_color, paw_color, container_is_fill, paw_is_fill):
    c = recolor(container_inner, "currentColor", container_color)
    p = wrap_transform(recolor(paw_inner, "currentColor", paw_color), tx, ty, sc)
    # 填充型爪印若容器为描边型：保持一致即可
    return f'<path d="M0 0h24v24H0z" fill="none"/>{c}{p}'

# ---- 各融合概念（双色：容器深墨 #2B2B2B / 爪印橙 #EA580C；单色：全深墨）----
concepts = {}

# A1 档案盒(stroke) × 爪印(stroke) —— paw 中心放盒口 (12, 12.8)，sc 0.52
c_arc_lucide = glyph_of(archive_lucide_svg)
p_lucide = paw_lucide_inner
concepts["box-stroke"] = {
    "duo":  build(c_arc_lucide, p_lucide, "stroke", 4.9, 6.6, 0.52, "#2B2B2B", "#EA580C", False, False),
    "mono": build(c_arc_lucide, p_lucide, "stroke", 4.9, 6.6, 0.52, "#2B2B2B", "#2B2B2B", False, False),
    "name": "档案盒 × 爪印（描边）", "set": "Lucide 组合 · ISC",
}

# A2 档案盒(fill) × 爪印(fill) —— paw 中心 (12, 14)，sc 0.42
c_arc_mdi = glyph_of(archive_mdi_svg)
p_pets = paw_pets_inner
concepts["box-fill"] = {
    "duo":  build(c_arc_mdi, p_pets, "fill", 6.96, 8.3, 0.42, "#2B2B2B", "#EA580C", True, True),
    "mono": build(c_arc_mdi, p_pets, "fill", 6.96, 8.3, 0.42, "#2B2B2B", "#2B2B2B", True, True),
    "name": "档案盒 × 爪印（实心）", "set": "MDI 组合 · Apache 2.0",
}

# B1 翻页书(fill) × 爪印(fill) —— paw 中心 (12, 13)，sc 0.4
c_book_mdi = glyph_of(book_mdi_svg)
concepts["book-fill"] = {
    "duo":  build(c_book_mdi, p_pets, "fill", 7.2, 7.9, 0.4, "#2B2B2B", "#EA580C", True, True),
    "mono": build(c_book_mdi, p_pets, "fill", 7.2, 7.9, 0.4, "#2B2B2B", "#2B2B2B", True, True),
    "name": "翻页书 × 爪印（实心）", "set": "MDI 组合 · Apache 2.0",
}

# B2 开卷书(stroke) × 爪印(stroke) —— paw 中心 (12, 10.8)，sc 0.42
c_book_lucide = glyph_of(book_lucide_svg)
concepts["book-stroke"] = {
    "duo":  build(c_book_lucide, p_lucide, "stroke", 6.8, 6.3, 0.42, "#2B2B2B", "#EA580C", False, False),
    "mono": build(c_book_lucide, p_lucide, "stroke", 6.8, 6.3, 0.42, "#2B2B2B", "#2B2B2B", False, False),
    "name": "开卷书 × 爪印（描边）", "set": "Lucide 组合 · ISC",
}

# C1 漫画气泡(stroke) × 爪印(stroke) —— paw 中心 (12, 11)，sc 0.48
c_bubble = glyph_of(bubble_iconoir_svg)
concepts["bubble-stroke"] = {
    "duo":  build(c_bubble, p_lucide, "stroke", 6.0, 6.0, 0.48, "#2B2B2B", "#EA580C", False, False),
    "mono": build(c_bubble, p_lucide, "stroke", 6.0, 6.0, 0.48, "#2B2B2B", "#2B2B2B", False, False),
    "name": "漫画气泡 × 爪印（描边）", "set": "Iconoir+Lucide 组合 · MIT+ISC",
}

# 落盘 + 收集
for key, v in concepts.items():
    for kind, body in [("duo", v["duo"]), ("mono", v["mono"])]:
        fn = os.path.join(OUT, f"fusion-{key}-{kind}.svg")
        open(fn, "w", encoding="utf-8").write(mono_svg(body))
    v["duo_svg"] = mono_svg(v["duo"])
    v["mono_svg"] = mono_svg(v["mono"])

json.dump(concepts, open(os.path.join(ICONS, "_fusion_data.json"), "w", encoding="utf-8"), ensure_ascii=False)
print("saved", len(concepts), "concepts ->", OUT)
for k, v in concepts.items():
    print(k, "|", v["name"], "| duo/mono done")
