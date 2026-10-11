#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""合成「画板上被踩了脚印」融合图标：画板/画架 × 爪印（正踩/微斜）。素材：Remix artboard-line（Apache-2.0）、Bootstrap easel（MIT）、Material Symbols pets（Apache-2.0）。"""
import re, os, json

ICONS = r"C:\Users\21883\Desktop\Purrchive\docs\icons"
OUT = r"C:\Users\21883\Desktop\Purrchive\docs\icons\fusion"
os.makedirs(OUT, exist_ok=True)

def glyph_of(svg):
    inner = re.sub(r'<svg[^>]*>', '', svg).replace('</svg>', '')
    inner = re.sub(r'<path d="M0 0h\d+v\d+H0z"[^>]*/>', '', inner)
    return inner.strip()

def recolor(inner, new="#2B2B2B"):
    return inner.replace("currentColor", new)

def svg24(body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24">'
            f'<path d="M0 0h24v24H0z" fill="none"/>{body}</svg>')

paw_pets = glyph_of(open(os.path.join(ICONS, "material-symbols-pets.svg"), encoding="utf-8").read())

def recover_container(sample_path):
    """从已生成的 duo SVG 中反解容器（爪印 group 之前的内容），并把 #2B2B2B 还原为 currentColor。"""
    s = open(sample_path, encoding="utf-8").read()
    body = re.sub(r'<svg[^>]*>', '', s).replace('</svg>', '')
    body = re.sub(r'<path d="M0 0h\d+v\d+H0z"[^>]*/>', '', body)
    cut = body.split('<g transform="translate(')[0]
    return cut.replace("#2B2B2B", "currentColor").strip()

try:
    art = json.load(open(r"C:\Users\21883\Desktop\Purrchive\docs\_artboard_icons.json", encoding="utf-8"))
    board = glyph_of(art["ri_artboard"]["svg"])
    easel16 = glyph_of(art["bi_easel"]["svg"])
except FileNotFoundError:
    # 反解已交付的 SVG
    board = recover_container(os.path.join(OUT, "footprint-board-straight-duo.svg"))
    easel16 = recover_container(os.path.join(OUT, "footprint-easel-straight-duo.svg"))
easel = f'<g transform="scale(1.5)">{easel16}</g>'

def paw_on(tx, ty, sc, ang, color):
    rot = f' rotate({ang} 12 12)' if ang else ''
    return f'<g transform="translate({tx} {ty}) scale({sc}){rot}">{recolor(paw_pets, color)}</g>'

PINK = "#F472B6"

concepts = {}
def mk(board_inner, tx, ty, sc, ang, name, set_):
    return {
        "name": name, "set": set_,
        "duo":  recolor(board_inner) + paw_on(tx, ty, sc, ang, PINK),
        "mono": recolor(board_inner) + paw_on(tx, ty, sc, ang, "#2B2B2B"),
        "light": board_inner.replace("currentColor", "#FFFFFF") + paw_on(tx, ty, sc, ang, PINK),
    }

concepts["board-straight"] = mk(board, 7.8, 4.7, 0.35, None, "画板 × 脚印（正踩）", "Remix+Material · Apache 2.0")
concepts["board-tilt"] = mk(board, 7.8, 4.7, 0.35, -12, "画板 × 脚印（微斜踩）", "Remix+Material · Apache 2.0")
concepts["easel-straight"] = mk(easel, 6.96, 4.3, 0.42, None, "画架 × 脚印（正踩）", "Bootstrap+Material · MIT+Apache 2.0")
concepts["easel-tilt"] = mk(easel, 6.96, 4.3, 0.42, -12, "画架 × 脚印（微斜踩）", "Bootstrap+Material · MIT+Apache 2.0")

for key, v in concepts.items():
    for kind in ("duo", "mono", "light"):
        open(os.path.join(OUT, f"footprint-{key}-{kind}.svg"), "w", encoding="utf-8").write(svg24(v[kind]))
    v["duo_svg"] = svg24(v["duo"])
    v["mono_svg"] = svg24(v["mono"])
    v["light_svg"] = svg24(v["light"])

json.dump(concepts, open(r"C:\Users\21883\Desktop\Purrchive\docs\_footprint_data.json", "w", encoding="utf-8"), ensure_ascii=False)
print("saved", len(concepts))
for k, v in concepts.items():
    print(" ", k, "|", v["name"])
