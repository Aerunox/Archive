package com.archive.app.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 端上的 PP-OCR 文字识别（模型来自 PaddleOCR，ONNX 格式由 RapidOCR 转换，推理用 ONNX Runtime）。
 *
 * 流程与参数严格对齐 RapidOCR 的 `config.yaml`（即 PaddleOCR 默认配置）：
 *
 * 1. **检测（DB）**：短边缩到 ≥736 且两边取 32 的倍数 → 归一化 `(x/255 - 0.5)/0.5` → 推理得到概率图；
 *    阈值 0.3 二值化 → 2×2 膨胀 → 连通域取框 → 框内平均分 ≥ 0.5 才保留 → 按
 *    `distance = area * 1.6 / perimeter` 外扩（unclip）→ 映射回原图坐标。
 * 2. **行合并**：同一行被切碎的框并成一行（否则识别出的会是一堆碎片）。
 * 3. **识别（CRNN + CTC）**：每行缩放高度 48、宽度按比例（上限 320）→ 归一化 `(x/255 - 0.5)/0.5` →
 *    greedy CTC 解码：丢掉 blank 与连续重复 → 行平均置信度 < 0.5 的整行丢弃。
 *
 * 这里是**纯 Kotlin** 实现（只用 ONNX Runtime 的 Java API，不碰任何 Android 类），
 * 因此可以在 JVM 单元测试里用同一份模型跑真实推理 —— `OcrEngineTest` 就是这么验证的。
 *
 * 简化说明：检测后处理只取**轴对齐**包围盒（用连通域而不是 OpenCV 轮廓 + 最小外接矩形），
 * 因此明显倾斜/竖排的文字会被当作水平行处理。漫画气泡里的横排文字不受影响。
 */
class OcrEngine private constructor(
    private val env: OrtEnvironment,
    private val det: OrtSession,
    private val rec: OrtSession,
    private val charset: List<String>,
    /** 识别模型输入的宽度；固定宽度（导出的 ONNX 常见）时用它，动态宽度为 0。 */
    internal val recInputWidth: Int,
) : Closeable {

    /** 识别结果的一行：文本 + 平均置信度 + 在原图上的位置。 */
    data class Line(val text: String, val score: Float, val box: OcrBox)

    /** 检测网络的输出概率图（尺寸为检测输入尺寸，非原图尺寸）。 */
    class DetMap(val prob: FloatArray, val width: Int, val height: Int)

    /** 诊断用：最近一次 [detect] 的概率图（排查检测问题时把它落成图片看）。 */
    var lastDetProb: DetMap? = null
        private set

    /** 诊断用：最近一次识别输入的画面（缩放到 48 高、按比例取宽之后的那一行）。 */
    var lastRecInput: ImageBuffer? = null
        private set

    /**
     * 识别整张图，返回按阅读顺序（上→下、左→右）排好的**段落**：
     * 同一个气泡里的多行会拼成一条，避免出现「一句话被拆成好几条、左右气泡交错」的碎片。
     */
    fun recognize(image: ImageBuffer): List<Line> {
        val boxes = detect(image)
        if (boxes.isEmpty()) return emptyList()
        val blocks = groupIntoBlocks(mergeIntoLines(boxes))
        val out = ArrayList<Line>(blocks.size)
        for (block in blocks) {
            val parts = ArrayList<String>(block.size)
            var scoreSum = 0f
            for (box in block.sortedWith(compareBy({ it.top }, { it.left }))) {
                val (text, score) = recognizeLine(image, box)
                // RapidOCR config.yaml 的 Global.text_score：整行平均置信度过低
                // （多为检测框里的噪声/水印）直接丢弃，不让乱码进入段落
                if (text.isNotBlank() && score >= REC_MIN_LINE_SCORE) {
                    parts += text
                    scoreSum += score
                }
            }
            if (parts.isEmpty()) continue
            val union = block.reduce { a, b ->
                OcrBox(
                    left = min(a.left, b.left),
                    top = min(a.top, b.top),
                    right = max(a.right, b.right),
                    bottom = max(a.bottom, b.bottom),
                )
            }
            out += Line(joinLines(parts), scoreSum / parts.size, union)
        }
        return out.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    // ────────────────────────────── 检测 ──────────────────────────────

    internal fun detect(image: ImageBuffer): List<OcrBox> {
        val detW: Int
        val detH: Int
        val input: FloatArray
        run {
            // RapidOCR Det：limit_type=min，短边不足 736 就放大到 736，随后取 32 的倍数
            val shortSide = min(image.width, image.height)
            val ratio = if (shortSide < DET_LIMIT_SIDE) DET_LIMIT_SIDE.toFloat() / shortSide else 1f
            val h = (image.height * ratio / 32f).roundToInt() * 32
            val w = (image.width * ratio / 32f).roundToInt() * 32
            detH = h.coerceAtLeast(32)
            detW = w.coerceAtLeast(32)
            val resized = image.resized(detW, detH)
            input = FloatArray(3 * detH * detW)
            val plane = detH * detW
            for (i in 0 until plane) {
                val p = resized.argb[i]
                // (x/255 - 0.5) / 0.5，RGB 三个通道
                // 通道顺序是 BGR：模型按 cv2 的 BGR 图训练/导出（RapidOCR 全流程都走 BGR）
                input[i] = ((p and 0xFF) / 255f - 0.5f) / 0.5f
                input[plane + i] = (((p shr 8) and 0xFF) / 255f - 0.5f) / 0.5f
                input[2 * plane + i] = (((p shr 16) and 0xFF) / 255f - 0.5f) / 0.5f
            }
        }

        val prob = FloatArray(detH * detW)
        val name = det.inputNames.first()
        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, detH.toLong(), detW.toLong()),
        ).use { tensor ->
            det.run(mapOf(name to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val value = result.get(0).value as Array<Array<Array<FloatArray>>>
                for (y in 0 until detH) {
                    val row = value[0][0][y]
                    System.arraycopy(row, 0, prob, y * detW, detW)
                }
            }
        }

        // 二值化（阈值 0.3）+ 2×2 膨胀（对齐 cv2.dilate 的 2×2 核、anchor 落在右下）
        val mask = BooleanArray(detH * detW)
        for (y in 0 until detH) {
            for (x in 0 until detW) {
                if (prob[y * detW + x] > DET_THRESH) mask[y * detW + x] = true
            }
        }
        val dilated = BooleanArray(detH * detW)
        for (y in 0 until detH) {
            for (x in 0 until detW) {
                val i = y * detW + x
                if (!mask[i]) continue
                dilated[i] = true
                if (x > 0) dilated[i - 1] = true
                if (y > 0) dilated[i - detW] = true
                if (x > 0 && y > 0) dilated[i - detW - 1] = true
            }
        }

        // 连通域 → 轴对齐包围盒（8 邻域，迭代式洪泛，避免递归爆栈）
        val visited = BooleanArray(detH * detW)
        val queue = IntArray(detH * detW)
        val boxes = ArrayList<OcrBox>(16)
        for (start in 0 until detH * detW) {
            if (!dilated[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = detW
            var minY = detH
            var maxX = 0
            var maxY = 0
            while (head < tail) {
                val i = queue[head++]
                val x = i % detW
                val y = i / detW
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= detW || ny >= detH) continue
                        val ni = ny * detW + nx
                        if (dilated[ni] && !visited[ni]) {
                            visited[ni] = true
                            queue[tail++] = ni
                        }
                    }
                }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            if (min(bw, bh) < DET_MIN_SIZE) continue

            // box_score_fast 的等价物：框内（含背景）概率均值
            var sum = 0f
            for (y in minY..maxY) {
                for (x in minX..maxX) sum += prob[y * detW + x]
            }
            if (sum / (bw * bh) < DET_BOX_THRESH) continue

            val sx = image.width.toFloat() / detW
            val sy = image.height.toFloat() / detH

            // 一块连通域可能盖住气泡里紧挨着的几行文字，按概率的「行剖面」切成一行一行再送识别。
            // 不切的话整块缩成 48 高的一行输入，模型会把两行挤着看，出来就是 Thishodsensita 这类乱码。
            for (row in splitIntoTextRows(prob, detW, minX, maxX, minY, maxY)) {
                val rowLeft = tightX(prob, detW, row, minX, maxX, fromLeft = true)
                val rowRight = tightX(prob, detW, row, minX, maxX, fromLeft = false)
                val rowW = rowRight - rowLeft + 1
                val rowH = row.last - row.first + 1
                if (min(rowW, rowH) < DET_MIN_SIZE) continue

                // unclip：矩形外扩 area * ratio / perimeter（pyclipper 对矩形的等价形式）
                val area = rowW.toFloat() * rowH
                val perimeter = 2f * (rowW + rowH)
                val pad = (area * DET_UNCLIP_RATIO / perimeter).roundToInt()
                if (min(rowW + 2 * pad, rowH + 2 * pad) < DET_MIN_SIZE + 2) continue

                boxes += OcrBox(
                    left = ((rowLeft - pad) * sx).roundToInt().coerceIn(0, image.width - 1),
                    top = ((row.first - pad) * sy).roundToInt().coerceIn(0, image.height - 1),
                    right = ((rowRight + pad + 1) * sx).roundToInt().coerceIn(1, image.width),
                    bottom = ((row.last + pad + 1) * sy).roundToInt().coerceIn(1, image.height),
                )
            }
        }

        lastDetProb = DetMap(prob, detW, detH)

        boxes.removeAll { it.width < 3 || it.height < 3 }
        return boxes
    }

    /**
     * 把一块连通域按概率的「行剖面」切成若干文字行：逐行取框内概率均值，
     * 均值明显低于峰值的位置就是行与行之间的空隙。返回每行的 y 闭区间。
     */
    private fun splitIntoTextRows(
        prob: FloatArray,
        detW: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): List<IntRange> {
        val span = bottom - top + 1
        val profile = FloatArray(span)
        for (y in top..bottom) {
            var sum = 0f
            for (x in left..right) sum += prob[y * detW + x]
            profile[y - top] = sum / (right - left + 1)
        }
        val peak = profile.maxOrNull() ?: 0f
        if (peak <= 0f) return listOf(top..bottom)

        // 行间空隙：行均值掉到峰值的 35% 以下；太薄的碎片（< 6px）不算一行
        val threshold = peak * 0.35f
        val rows = ArrayList<IntRange>()
        var start = -1
        for (i in 0 until span) {
            if (profile[i] >= threshold) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (i - start >= 6) rows += (top + start)..(top + i - 1)
                start = -1
            }
        }
        if (start >= 0 && span - start >= 6) rows += (top + start)..(top + span - 1)
        return rows.ifEmpty { listOf(top..bottom) }
    }

    /**
     * 某一行的水平边界：该 y 区间里过阈值像素的最小 / 最大 x。
     * 切出来的每一行各自收紧，短行不会带上旁边空背景。
     */
    private fun tightX(
        prob: FloatArray,
        detW: Int,
        row: IntRange,
        from: Int,
        to: Int,
        fromLeft: Boolean,
    ): Int {
        val xs = if (fromLeft) from..to else to downTo from
        for (x in xs) {
            for (y in row) {
                if (prob[y * detW + x] > DET_THRESH) return x
            }
        }
        return if (fromLeft) from else to
    }

    /**
     * 把行归成「段」：同一气泡里的若干行算一段。
     * 逐行输出会把一句话拆成好几条，左右两个气泡还会交错在一起 —— 这是「内容不连贯」的根源。
     */
    internal fun groupIntoBlocks(lines: List<OcrBox>): List<List<OcrBox>> {
        val remaining = lines.sortedWith(compareBy({ it.top }, { it.left })).toMutableList()
        val blocks = ArrayList<List<OcrBox>>()
        while (remaining.isNotEmpty()) {
            val block = arrayListOf(remaining.removeAt(0))
            var grew = true
            while (grew) {
                grew = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val box = iterator.next()
                    if (block.any { adjacent(it, box) }) {
                        block += box
                        iterator.remove()
                        grew = true
                    }
                }
            }
            blocks += block
        }
        return blocks
    }

    /**
     * 两行算「同一段」的条件：水平明显重叠（≥30% 窄边宽），且竖直间隙不超过 1.4 倍行高。
     * 1.4 倍是给「行距略松」的排版（字幕、截图里的文字）留的余量；
     * 左右并排的气泡靠水平重叠这一条被排除在外。
     */
    private fun adjacent(a: OcrBox, b: OcrBox): Boolean {
        val overlapX = min(a.right, b.right) - max(a.left, b.left)
        if (overlapX <= 0) return false
        if (overlapX < 0.3f * min(a.width, b.width)) return false
        val gap = max(a.top, b.top) - min(a.bottom, b.bottom)
        return gap <= 1.4f * min(a.height, b.height)
    }

    /** 段内相邻行拼接：英文之间补空格，中文之间不补（否则会多出一堆空格）。 */
    private fun joinLines(parts: List<String>): String {
        val sb = StringBuilder(parts.first())
        for (i in 1 until parts.size) {
            val prev = sb.last()
            val next = parts[i].first()
            if (prev.code < 128 && next.code < 128 && (prev.isLetterOrDigit() || next.isLetterOrDigit())) {
                sb.append(' ')
            }
            sb.append(parts[i])
        }
        return sb.toString()
    }

    /**
     * 把检测框并成「行」：同一行里被切碎的框（如按词分开）合起来，
     * 否则识别结果会是一堆碎片。判定用「竖直重叠够多 + 水平间隙够小」。
     * 返回按阅读顺序排好的行框。
     *
     * 这里必须用**循环**而不是递归：一页漫画可能检测出成百上千个框，逐轮合并的递归会
     * 把线程栈（Android 工作线程只有约 1MB）用光，抛出的 StackOverflowError 在界面上
     * 只剩一句 `stack size ...KB`。每轮合并至少少一个框，所以循环一定收敛。
     */
    internal fun mergeIntoLines(boxes: List<OcrBox>): List<OcrBox> {
        var current = boxes
        while (true) {
            val merged = ArrayList<OcrBox>(current.size)
            for (box in current.sortedWith(compareBy({ it.centerY }, { it.left }))) {
                val hit = merged.indexOfFirst { line ->
                    val overlap = min(line.bottom, box.bottom) - max(line.top, box.top)
                    val minHeight = min(line.height, box.height)
                    val gap = max(line.left, box.left) - min(line.right, box.right)
                    overlap > 0.6f * minHeight && gap <= minHeight
                }
                if (hit < 0) {
                    merged += box
                } else {
                    val line = merged[hit]
                    merged[hit] = OcrBox(
                        left = min(line.left, box.left),
                        top = min(line.top, box.top),
                        right = max(line.right, box.right),
                        bottom = max(line.bottom, box.bottom),
                    )
                }
            }
            if (merged.size == current.size) {
                return merged.sortedWith(compareBy({ it.top }, { it.left }))
            }
            current = merged
        }
    }

    // ────────────────────────────── 识别 ──────────────────────────────

    private fun recognizeLine(image: ImageBuffer, box: OcrBox): Pair<String, Float> {
        val targetH = REC_HEIGHT
        val ratio = box.width.toFloat() / box.height
        val natural = ceil(targetH * ratio).toInt()
        // 宽的那一维按比例算，**不能压缩**：固定宽度模型要求把内容放左侧、右侧补零
        val targetW = recInputWidth.takeIf { it > 0 } ?: min(REC_MAX_WIDTH, max(REC_MIN_WIDTH, natural))
        val contentW = natural.coerceIn(REC_MIN_WIDTH, targetW)

        val cropped = image.crop(box.left, box.top, box.right, box.bottom).resized(contentW, targetH)
        lastRecInput = cropped
        val plane = targetH * targetW
        // 右侧补零：PaddleOCR 的 padding 在归一化空间就是 0
        val input = FloatArray(3 * plane)
        for (y in 0 until targetH) {
            for (x in 0 until contentW) {
                val p = cropped.argb[y * contentW + x]
                val i = y * targetW + x
                // 同检测：BGR 顺序
                input[i] = (p and 0xFF) / 127.5f - 1f
                input[plane + i] = ((p shr 8) and 0xFF) / 127.5f - 1f
                input[2 * plane + i] = ((p shr 16) and 0xFF) / 127.5f - 1f
            }
        }

        val name = rec.inputNames.first()
        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, targetH.toLong(), targetW.toLong()),
        ).use { tensor ->
            rec.run(mapOf(name to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = result.get(0).value as Array<Array<FloatArray>>
                return decodeCtc(logits[0])
            }
        }
    }

    /**
     * greedy CTC：逐时间步取最大类别，丢掉 blank（下标 0）与连续重复，
     * 字符表 = `["blank"] + 字典 + [" "]`（与 PaddleOCR 的 CTCLabelDecode 一致）。
     */
    private fun decodeCtc(logits: Array<FloatArray>): Pair<String, Float> {
        val sb = StringBuilder()
        var sum = 0f
        var count = 0
        var prev = -1
        for (step in logits) {
            var best = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (c in step.indices) {
                if (step[c] > bestScore) {
                    bestScore = step[c]
                    best = c
                }
            }
            if (best == 0) {
                prev = best
                continue
            }
            if (best == prev) continue
            // 输出**已经是 softmax 概率**（PaddleOCR 的 rec 导出如此，它的 text_score 也是按概率比较）。
            // 若在这里再做一次 softmax，每个类别会被压到 1/6625，字符会被整段丢光 —— 一个字都不剩。
            val prob = bestScore.coerceIn(0f, 1f)
            sb.append(charset.getOrElse(best) { "" })
            sum += prob
            count++
            prev = best
        }
        if (count == 0) return "" to 0f
        return sb.toString() to sum / count
    }

    override fun close() {
        rec.close()
        det.close()
    }

    companion object {
        /** RapidOCR config.yaml → Det */
        private const val DET_LIMIT_SIDE = 736
        private const val DET_THRESH = 0.3f
        private const val DET_BOX_THRESH = 0.5f
        private const val DET_UNCLIP_RATIO = 1.6f
        private const val DET_MIN_SIZE = 3

        /** RapidOCR config.yaml → Rec */
        private const val REC_HEIGHT = 48

        /**
         * 识别输入的宽度按 `48 * 宽高比` 算（PaddleOCR 用 batch 内的最大宽高比，不设 320 上限：
         * 那个 320 只是默认形状提示，卡住它会把长行横向压扁成垃圾）。
         * 这里只加一个内存上限。
         */
        private const val REC_MAX_WIDTH = 2048
        private const val REC_MIN_WIDTH = 16

        /** Global.text_score：整行平均置信度低于它就丢弃（recognize() 里的行级过滤）。 */
        private const val REC_MIN_LINE_SCORE = 0.5f

        /**
         * 建引擎。[detModel] / [recModel] 是 ONNX 模型字节（assets 或文件都行），
         * [charsetText] 是 PaddleOCR 的 `ppocr_keys_v1.txt` 原文（每行一个字符）。
         */
        fun create(
            env: OrtEnvironment,
            detModel: ByteArray,
            recModel: ByteArray,
            charsetText: String,
        ): OcrEngine {
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
            val det = env.createSession(detModel, options)
            val rec = env.createSession(recModel, options)
            val charset = ArrayList<String>()
            charset += "blank"
            // 字典一行一个字符；末尾空行必须跳过，否则字符表整体错位一格，
            // 模型最后一类（空格）会落到表外，识别结果里的空格就全没了。
            charsetText.lineSequence()
                .map { it.trimEnd('\r', '\n') }
                .filter { it.isNotEmpty() }
                .forEach { charset += it }
            // PaddleOCR 在字典末尾追加空格字符
            charset += " "
            val info = rec.inputInfo.values.firstOrNull()?.info as? TensorInfo
            val width = info?.shape?.getOrNull(3)?.toInt()?.takeIf { it > 0 } ?: 0
            return OcrEngine(env, det, rec, charset, width)
        }
    }
}

/** 轴对齐矩形（原图像素坐标，右下开区间）。 */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * 一张 ARGB 图片（`0xAARRGGBB`，与 `Bitmap.getPixels` 一致）。
 * 之所以不用 `android.graphics.Bitmap`，是为了让整条 OCR 管线能在 JVM 单测里跑。
 */
class ImageBuffer(val width: Int, val height: Int, val argb: IntArray) {

    init {
        require(argb.size == width * height) { "argb 尺寸与 $width x $height 不符" }
    }

    fun crop(left: Int, top: Int, right: Int, bottom: Int): ImageBuffer {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = right.coerceIn(l + 1, width)
        val b = bottom.coerceIn(t + 1, height)
        val w = r - l
        val h = b - t
        val out = IntArray(w * h)
        for (y in 0 until h) {
            System.arraycopy(argb, (t + y) * width + l, out, y * w, w)
        }
        return ImageBuffer(w, h, out)
    }

    /** 双线性缩放（对齐 cv2.INTER_LINEAR 的像素中心约定）。 */
    fun resized(newWidth: Int, newHeight: Int): ImageBuffer {
        val w = newWidth.coerceAtLeast(1)
        val h = newHeight.coerceAtLeast(1)
        if (w == width && h == height) return this
        val out = IntArray(w * h)
        val scaleX = width.toFloat() / w
        val scaleY = height.toFloat() / h
        for (y in 0 until h) {
            val fy = (y + 0.5f) * scaleY - 0.5f
            val y0 = kotlin.math.floor(fy).toInt().coerceIn(0, height - 1)
            val y1 = (y0 + 1).coerceAtMost(height - 1)
            val wy = (fy - y0).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val fx = (x + 0.5f) * scaleX - 0.5f
                val x0 = kotlin.math.floor(fx).toInt().coerceIn(0, width - 1)
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val wx = (fx - x0).coerceIn(0f, 1f)
                val p00 = argb[y0 * width + x0]
                val p01 = argb[y0 * width + x1]
                val p10 = argb[y1 * width + x0]
                val p11 = argb[y1 * width + x1]
                out[y * w + x] = blend(p00, p01, p10, p11, wx, wy)
            }
        }
        return ImageBuffer(w, h, out)
    }

    private fun blend(p00: Int, p01: Int, p10: Int, p11: Int, wx: Float, wy: Float): Int {
        var result = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            val c00 = (p00 shr shift) and 0xFF
            val c01 = (p01 shr shift) and 0xFF
            val c10 = (p10 shr shift) and 0xFF
            val c11 = (p11 shr shift) and 0xFF
            val top = c00 + (c01 - c00) * wx
            val bottom = c10 + (c11 - c10) * wx
            val v = (top + (bottom - top) * wy).roundToInt().coerceIn(0, 255)
            result = result or (v shl shift)
        }
        return result
    }
}
