#!/usr/bin/env python3
"""把 `app/src/main/assets/ocr` 下的 PP-OCR ONNX 模型做 INT8 量化，压缩安装包体积。

用法（在工程根目录运行）::

    python tools/quantize_ocr.py             # 量化到 build/ocr-quantized/，只输出体积对比
    python tools/quantize_ocr.py --inplace   # 额外替换 assets 里的模型（FP32 原件备份到 build/ocr-quantized/*.fp32.bak）

量化后**必须回归**：``./gradlew :app:testDebugUnitTest --tests OcrEngineTest``
（测试用自渲染文本跑 det + rec，读的就是 assets 里这批文件，能直接判定量化模型能不能用）。
不通过就用 `build/ocr-quantized/*.fp32.bak` 覆盖回 assets 还原。

为什么必须是**静态 QDQ**而不是动态量化：

- 这两个模型（PP-OCRv4 的 det / rec）权重几乎全在 **Conv** 上，
  而 ONNX Runtime 的 `quantize_dynamic` 只压 MatMul / Gemm 的权重 ——
  实测下来产物反而略大（4.53→4.56 MiB、10.35→10.39 MiB），完全无效。
- 静态量化（QDQ）能压 Conv，但需要**校准集**。这里用 PIL 现场渲染
  中英混排文本图当校准数据，前处理参数与 `OcrEngine.kt` 保持一致：
  RGB 通道序、`(x/255 - 0.5) / 0.5` 归一化、det 边长为 32 的倍数、rec 高 48 宽 ≤320。
  校准只影响激活量化区间，是启发式的，不需要与真实截图逐像素一致。

**必须先做 pre-process（本脚本第一步）**：这两个 ONNX 的 `initializer` 数量是 **0**，
4.47 / 10.26 MiB 权重全都塞在 `Constant` 节点里（paddle2onnx 的常见导出形态）。
量化器只认 initializer，直接量化只会往图里插 Q/DQ 节点，**产物反而变大**
（实测静态量化 4.53→4.77、10.35→10.63 MiB）。`quant_pre_process` 会把 Constant
搬成 initializer 并做形状推断/算子融合（Conv+BN 折叠，量化前必须做），
之后再量化才真正压得下来。

- `ch_ppocr_mobile_v2.0_cls_infer.onnx` 不在量化名单里：应用根本没加载它
  （见 `OcrService` 的 DET_MODEL / REC_MODEL / KEYS），属于可直接删除的死资源。
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from onnxruntime.quantization import (
    CalibrationDataReader,
    CalibrationMethod,
    QuantFormat,
    QuantType,
    quantize_static,
)

ASSETS = Path("app/src/main/assets/ocr")
OUT = Path("build/ocr-quantized")
CALIB = Path("build/ocr-calib")
MODELS = ("ch_PP-OCRv4_det_infer.onnx", "ch_PP-OCRv4_rec_infer.onnx")

DET_SHAPE = (896, 1984)  # (w, h)，均满足 32 的倍数，接近截图经 MAX_SIDE 限制后的尺寸
REC_HEIGHT = 48
REC_MAX_WIDTH = 320

CN_FONTS = ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf")
EN_FONTS = ("C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/segoeui.ttf")

CN_LINES = (
    "这是一段用于校准的中文文本，包含常见标点，以及数字 1234567890。",
    "更新通知与使用交流，点击加入即可查看最新版本信息。",
    "作者主页：横幅、公告、标签筛选、作者内搜索，全部可用。",
    "下载中心支持断点续传，进度会在通知栏显示。",
    "视频播放器支持倍速、全屏与悬浮菜单操作。",
    "收藏的帖子与作者来自账号云端，登录后可查看。",
)
EN_LINES = (
    "The quick brown fox jumps over the lazy dog, 0123456789.",
    "Archive is a third-party client with Miuix design.",
    "Batch operations, blocking, and offline archive are supported.",
    "Playback speed, fullscreen, and download all work offline.",
    "Search by keyword, MD5 or SHA-256 hash.",
    "This line exists to calibrate the recognizer.",
)


def pick_font(candidates: tuple[str, ...], size: int) -> ImageFont.FreeTypeFont:
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise SystemExit(f"找不到可用的字体：{candidates}")


def render_samples() -> tuple[list[Path], list[Path]]:
    """渲染校准图：det 用大块多行文本，rec 用单行文本。返回 (det 图, rec 图)。"""
    CALIB.mkdir(parents=True, exist_ok=True)
    det_images: list[Path] = []
    rec_images: list[Path] = []

    # --- det：大画布 + 多行文本，白/浅灰底两种 ---
    width, height = DET_SHAPE
    for idx in range(6):
        bg = (255, 255, 255) if idx % 3 else (238, 238, 240)
        canvas = Image.new("RGB", (width, height), bg)
        draw = ImageDraw.Draw(canvas)
        y = 40
        for row, (text, size, font_paths) in enumerate(
            [(t, s, CN_FONTS) for t, s in zip(CN_LINES, (26, 30, 34, 38, 42, 46))]
            + [(t, s, EN_FONTS) for t, s in zip(EN_LINES, (24, 28, 32, 36, 40, 44))]
        ):
            if y > height - 60:
                break
            draw.text((40, y), text, fill=(17, 17, 17), font=pick_font(font_paths, size))
            y += size + 18
        path = CALIB / f"det_{idx}.png"
        canvas.save(path)
        det_images.append(path)

    # --- rec：单行文本，宽度覆盖窄/宽两档 ---
    for idx, (lines, font_paths, size) in enumerate(
        ((CN_LINES, CN_FONTS, 34), (EN_LINES, EN_FONTS, 30))
    ):
        for j, text in enumerate(lines):
            font = pick_font(font_paths, size)
            box = font.getbbox(text)
            w = max(64, min(REC_MAX_WIDTH, box[2] - box[0] + 8))
            h = max(24, box[3] - box[1] + 8)
            line = Image.new("RGB", (w, h), (255, 255, 255))
            ImageDraw.Draw(line).text((4, 4), text, fill=(17, 17, 17), font=font)
            path = CALIB / f"rec_{idx}_{j}.png"
            line.save(path)
            rec_images.append(path)
    return det_images, rec_images


def to_chw(image: Image.Image, width: int, height: int) -> np.ndarray:
    """与 OcrEngine 一致：RGB 通道序 + (x/255 - 0.5) / 0.5 → [1,3,H,W]。"""
    resized = image.convert("RGB").resize((width, height), Image.BILINEAR)
    arr = np.asarray(resized, dtype=np.float32) / 255.0
    arr = (arr - 0.5) / 0.5
    return np.transpose(arr, (2, 0, 1))[None, ...].astype(np.float32)


class _Reader(CalibrationDataReader):
    def __init__(self, tensors: list[np.ndarray], input_name: str) -> None:
        self._tensors = tensors
        self._input_name = input_name
        self._iter = iter(tensors)

    def get_next(self):
        tensor = next(self._iter, None)
        if tensor is None:
            return None
        return {self._input_name: tensor}

    def rewind(self) -> None:
        self._iter = iter(self._tensors)


def input_name_of(model: Path) -> str:
    import onnx

    return onnx.load(str(model)).graph.input[0].name


def constants_to_initializers(model) -> int:
    """兜底：把 Constant 节点里的张量搬成 initializer，返回搬走的节点数。"""
    moved = 0
    keep = []
    for node in model.graph.node:
        tensor = None
        if node.op_type == "Constant":
            for attr in node.attribute:
                if attr.name == "value":
                    tensor = attr.t
                    break
        if tensor is None:
            keep.append(node)
            continue
        tensor.name = node.output[0]
        model.graph.initializer.append(tensor)
        moved += 1
    del model.graph.node[:]
    model.graph.node.extend(keep)
    return moved


def preprocess(src: Path, dst: Path) -> str:
    """量化前的必要处理。

    1. **升 opset 到 17**（原模型是 12）：量化器生成的 `DequantizeLinear` 会带 `axis`
       属性，那是 opset 13 才有的 —— 不升的话推理会话直接报
       `Unrecognized attribute: axis for operator DequantizeLinear`。
       ORT-Android 1.22 支持到 opset 21，升到 17 安全。
    2. 可选 `onnxsim` 做常量折叠 + **Conv+BN 融合**（未融合的 BN 被量化会明显掉精度）。
    3. `Constant` → `initializer`（原模型 initializer 数为 0，权重全在 Constant 里）。
    """
    import onnx
    from onnx import version_converter

    model = onnx.load(str(src))
    notes = []

    if any(opset.version < 17 for opset in model.opset_import):
        model = version_converter.convert_version(model, 17)
        notes.append("opset→17")

    try:
        import onnxsim  # 可选依赖，没装就跳过

        simplified, ok = onnxsim.simplify(model)
        if ok:
            model = simplified
            notes.append("onnxsim(含 BN 融合)")
    except Exception:  # noqa: BLE001 - 纯优化步骤，失败不影响正确性
        notes.append("onnxsim 不可用")

    moved = constants_to_initializers(model)
    notes.append(f"{moved} 个 Constant→initializer")
    onnx.save(model, str(dst))
    return "、".join(notes)


def human(num: int) -> str:
    return f"{num / 1024 / 1024:.2f} MiB"


def main() -> int:
    parser = argparse.ArgumentParser(description="PP-OCR 模型 INT8 静态量化")
    parser.add_argument("--inplace", action="store_true", help="量化后直接替换 assets 里的模型")
    args = parser.parse_args()

    if not ASSETS.is_dir():
        print(f"找不到 {ASSETS}，请在工程根目录运行", file=sys.stderr)
        return 1
    OUT.mkdir(parents=True, exist_ok=True)

    det_images, rec_images = render_samples()
    det_tensors = [
        to_chw(Image.open(p), DET_SHAPE[0], DET_SHAPE[1]) for p in det_images
    ]
    rec_tensors = []
    for path in rec_images:
        image = Image.open(path)
        height = REC_HEIGHT
        width = max(8, min(REC_MAX_WIDTH, round(image.width * height / image.height)))
        rec_tensors.append(to_chw(image, width, height))
    print(f"校准集：det {len(det_tensors)} 张 ({DET_SHAPE[0]}x{DET_SHAPE[1]})、"
          f"rec {len(rec_tensors)} 行（高 {REC_HEIGHT}，宽 ≤{REC_MAX_WIDTH}）")

    readers = {"det": det_tensors, "rec": rec_tensors}
    total_before = total_after = 0
    print(f"\n{'模型':<38}{'量化前':>12}{'量化后':>12}{'比例':>8}")
    for name in MODELS:
        src = ASSETS / name
        if not src.exists():
            print(f"{name:<38}{'缺失':>12}")
            continue
        dst = OUT / name
        pre = OUT / f"{name}.pre.onnx"
        how = preprocess(src, pre)
        quantize_static(
            model_input=str(pre),
            model_output=str(dst),
            calibration_data_reader=_Reader(readers["det" if "det" in name else "rec"],
                                            input_name_of(pre)),
            quant_format=QuantFormat.QDQ,
            per_channel=True,
            weight_type=QuantType.QInt8,
            activation_type=QuantType.QUInt8,
            calibrate_method=CalibrationMethod.MinMax,
            # 只量化真正带权重的 Conv / Gemm / MatMul：
            # 这两个模型的归一化是**内嵌在图里**的（Mul/Add/Div + Constant），
            # 一旦把 Add/Mul 也纳入量化范围，等于把「除以 255 再减 0.5」这种常量压成 INT8，
            # 检测输出会直接废掉（实测 6 项 OcrEngineTest 全失败）。
            op_types_to_quantize=["Conv", "Gemm", "MatMul"],
            extra_options={"ActivationSymmetric": False, "WeightSymmetric": True},
        )
        before, after = src.stat().st_size, dst.stat().st_size
        total_before += before
        total_after += after
        print(f"{name:<38}{human(before):>12}{human(after):>12}{after / before:>8.0%}")
        print(f"    预处理：{how} → 中间产物 {human(pre.stat().st_size)}")
        if args.inplace:
            shutil.copy2(src, OUT / f"{name}.fp32.bak")
            shutil.copy2(dst, src)

    if total_before:
        print(f"{'合计':<38}{human(total_before):>12}{human(total_after):>12}"
              f"{total_after / total_before:>8.0%}")
        if args.inplace:
            print(f"FP32 原件备份在 {OUT}（*.fp32.bak）")
            print("下一步必须跑：./gradlew :app:testDebugUnitTest --tests OcrEngineTest")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
