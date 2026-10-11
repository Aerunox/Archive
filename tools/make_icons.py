#!/usr/bin/env python3
"""由一张方形素材生成全套应用图标资源。

用法（在工程根目录运行）::

    python tools/make_icons.py --source path/to/icon.png

生成/覆盖：

- ``mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.webp`` —— 自适应图标前台，画布 108dp×密度；
- ``mipmap-*/ic_launcher.webp``、``ic_launcher_round.webp`` —— 传统方形/圆形图标（48dp×密度）；
- ``drawable-nodpi/ic_app_logo.webp`` —— 应用内标识（18+ 页把它填进 72dp 圆角方块）。

两个关键约定（改动前请先读）：

1. **自适应前台按画布 70% 居中**。规范只保证画布中央 61%（安全区）一定可见，各家启动器
   还会再放大一截；素材若按 100% 铺满，缩放后下巴/嘴会被裁掉。缩到 70% 后实测正好看到
   整张脸。多出来的部分用**素材底色**填充（本图标是纯黑），与 ``@color/ic_launcher_background``
   同色，所以看不出边界。
2. **背景色要跟着素材底色改**：见 ``app/src/main/res/values/ic_launcher_background.xml``。
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# 与当前素材底色一致；换素材若底色不同，这里与 ic_launcher_background.xml 都要改
CANVAS_BG = (0, 0, 0)
FOREGROUND_RATIO = 0.70
WEBP = {"format": "WEBP", "quality": 92, "method": 6}


def circular(image: Image.Image) -> Image.Image:
    size = image.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(image.convert("RGBA"), (0, 0), mask)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="生成全套应用图标")
    parser.add_argument("--source", required=True, help="方形图标素材（建议 ≥512×512）")
    parser.add_argument("--ratio", type=float, default=FOREGROUND_RATIO,
                        help=f"自适应前台占画布比例（默认 {FOREGROUND_RATIO}）")
    args = parser.parse_args()

    art = Image.open(args.source).convert("RGB")
    print(f"素材 {art.size[0]}x{art.size[1]}")

    for name, scale in DENSITIES.items():
        out_dir = RES / f"mipmap-{name}"
        out_dir.mkdir(parents=True, exist_ok=True)

        canvas = round(108 * scale)
        inner = round(canvas * args.ratio)
        foreground = Image.new("RGB", (canvas, canvas), CANVAS_BG)
        foreground.paste(
            art.resize((inner, inner), Image.LANCZOS),
            ((canvas - inner) // 2, (canvas - inner) // 2),
        )
        foreground.save(out_dir / "ic_launcher_foreground.webp", **WEBP)

        legacy = art.resize((round(48 * scale),) * 2, Image.LANCZOS)
        legacy.save(out_dir / "ic_launcher.webp", **WEBP)
        circular(legacy).save(out_dir / "ic_launcher_round.webp", alpha_quality=100, **WEBP)
        print(f"  mipmap-{name:<8} 画布 {canvas}px  前台 {inner}px")

    logo_dir = RES / "drawable-nodpi"
    logo_dir.mkdir(parents=True, exist_ok=True)
    art.resize((512, 512), Image.LANCZOS).save(logo_dir / "ic_app_logo.webp", **WEBP)
    print("  drawable-nodpi/ic_app_logo.webp 512px（18+ 页 72dp 圆角方块内满幅显示）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
