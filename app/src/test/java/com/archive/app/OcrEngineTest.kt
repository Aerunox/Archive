package com.archive.app

import ai.onnxruntime.OrtEnvironment
import com.archive.app.ocr.ImageBuffer
import com.archive.app.ocr.OcrBox
import com.archive.app.ocr.OcrEngine
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实 PP-OCR 模型**跑完整管线（检测 → 行合并 → 识别 → CTC 解码）。
 *
 * 之所以在 JVM 上测：预/后处理是否与 PaddleOCR/RapidOCR 对齐（归一化方式、缩放后取 32 的倍数、
 * unclip 距离、字符表构成）没法靠肉眼在设备上快速试错 —— 这里一次跑通就说明端上同一份代码也是对的。
 *
 * 模型与字典直接读 app 的 assets（Gradle 测试的工作目录是模块根目录），
 * 与运行时从 `assets/ocr/` 加载的是同一批文件。
 */
class OcrEngineTest {

    private val assets = File("src/main/assets/ocr")

    private fun newEngine(): OcrEngine = OcrEngine.create(
        OrtEnvironment.getEnvironment(),
        File(assets, "ch_PP-OCRv4_det_infer.onnx").readBytes(),
        File(assets, "ch_PP-OCRv4_rec_infer.onnx").readBytes(),
        File(assets, "ppocr_keys_v1.txt").readText(),
    )

    /** 白底黑字渲染一段文字（模拟漫画里的对白排版）。 */
    private fun renderText(text: String, fontSize: Int = 44, width: Int = 900, height: Int = 240): ImageBuffer {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
        g.drawString(text, 36, height / 2 + fontSize / 3)
        g.dispose()
        val argb = IntArray(width * height)
        image.getRGB(0, 0, width, height, argb, 0, width)
        return ImageBuffer(width, height, argb)
    }

    /** 画一段多行文字（模拟气泡里的对白）。 */
    private fun renderLines(
        lines: List<String>,
        fontSize: Int = 38,
        width: Int = 900,
        lineHeight: Int = 68,
    ): ImageBuffer {
        val height = lineHeight * lines.size + 40
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
        lines.forEachIndexed { index, line ->
            g.drawString(line, 36, 30 + lineHeight * index + fontSize)
        }
        g.dispose()
        val argb = IntArray(width * height)
        image.getRGB(0, 0, width, height, argb, 0, width)
        return ImageBuffer(width, height, argb)
    }

    @Test
    fun `reads plain latin text with ppocr models`() {
        newEngine().use { engine ->
            val lines = engine.recognize(renderText("Hello Archive 2026"))
            println("OCR => " + lines.joinToString(" | ") { "${it.text}(${"%.2f".format(it.score)})" })
            val joined = lines.joinToString(" ") { it.text }.lowercase()
            assertTrue("没识别出目标文字，实际：$joined", joined.contains("archive"))
        }
    }

    /**
     * 气泡里多行文字应该**拼成一段**，而不是拆成好几条碎片 ——
     * 之前按行罗列，加上左右气泡交错，读起来就是「内容不连贯」。
     */
    @Test
    fun `multi line bubble becomes one paragraph`() {
        newEngine().use { engine ->
            val image = renderLines(
                listOf("You should be", "grateful for the", "wonders my curves"),
            )
            val detected = engine.detect(image)
            println("检测框: " + detected.joinToString { "${it.left},${it.top}-${it.right},${it.bottom}(${it.width}x${it.height})" })
            println("行: " + engine.mergeIntoLines(detected).joinToString { "${it.left},${it.top}-${it.right},${it.bottom}(${it.width}x${it.height})" })
            val lines = engine.recognize(image)
            println("OCR(block) => " + lines.joinToString(" | ") { it.text })
            assertEquals(1, lines.size)
            // 空格在紧凑排版下可能被模型吞掉，比较时先去掉空格
            val text = lines[0].text.lowercase().replace(" ", "")
            assertTrue("段落里没包含全部行：$text", text.contains("grateful") && text.contains("wonders"))
        }
    }

    /**
     * 回归：一页漫画会检测出成百上千个框，合并框曾经用递归实现，
     * 直接把这层线程栈用光（ART 的 StackOverflowError 消息就是 `stack size …KB`）。
     */
    @Test
    fun `merging hundreds of boxes on one line stays shallow`() {
        newEngine().use { engine ->
            val boxes = (0 until 600).map { OcrBox(it * 30, 100, it * 30 + 28, 130) }
            val lines = engine.mergeIntoLines(boxes)
            assertEquals(1, lines.size)
            assertEquals(0, lines[0].left)
            assertEquals(600 * 30 - 2, lines[0].right)
        }
    }

    /** 不同行不能被并到一起。 */
    @Test
    fun `merging keeps separate rows separate`() {
        newEngine().use { engine ->
            val boxes = (0 until 40).map { OcrBox(10, it * 60, 400, it * 60 + 30) }
            val lines = engine.mergeIntoLines(boxes)
            assertEquals(40, lines.size)
        }
    }

    /** 中文路径同样要走通：字符表是中文 + 英文混排的（ppocr_keys_v1）。 */
    @Test
    fun `reads chinese text with ppocr models`() {
        newEngine().use { engine ->
            val lines = engine.recognize(renderText("你好，世界"))
            println("OCR(zh) => " + lines.joinToString(" | ") { "${it.text}(${"%.2f".format(it.score)})" })
            val joined = lines.joinToString("") { it.text }
            assertTrue("没识别出中文，实际：$joined", joined.contains("你好"))
        }
    }

    /**
     * 把「原图 / 检测概率图 / 二值掩膜」落成 PNG，用于肉眼确认预处理与检测是否正常
     * （输出在 app/build/ocr-debug/）。识别结果不对时先看这三张图最快。
     */
    @Test
    fun `dumps detection maps for inspection`() {
        val buffer = renderText("Hello Archive 2026")
        writePng("01-input.png", buffer.width, buffer.height, buffer.argb)
        newEngine().use { engine ->
            println("rec 模型输入宽度 = ${engine.recInputWidth}（0 表示动态）")
            val boxes = engine.detect(buffer)
            println(
                "检测框 ${boxes.size} 个: " +
                    boxes.joinToString { "${it.left},${it.top}-${it.right},${it.bottom}(${it.width}x${it.height})" },
            )
            println("OCR => " + engine.recognize(buffer).joinToString(" | ") { it.text })
            val map = engine.lastDetProb ?: return
            var firstRow = -1
            var lastRow = -1
            for (y in 0 until map.height) {
                var any = false
                for (x in 0 until map.width) {
                    if (map.prob[y * map.width + x] > 0.3f) {
                        any = true
                        break
                    }
                }
                if (any) {
                    if (firstRow < 0) firstRow = y
                    lastRow = y
                }
            }
            println("det 尺寸 ${map.width}x${map.height}，概率 >0.3 的行范围 $firstRow..$lastRow（高 ${lastRow - firstRow + 1}）")
            writePng(
                "02-prob.png",
                map.width,
                map.height,
                IntArray(map.prob.size) { i ->
                    val v = (map.prob[i] * 255f).toInt().coerceIn(0, 255)
                    0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
                },
            )
            engine.lastRecInput?.let { rec ->
                println("rec 输入画面 ${rec.width}x${rec.height}")
                writePng("04-rec-input.png", rec.width, rec.height, rec.argb)
            }
            writePng(
                "03-mask.png",
                map.width,
                map.height,
                IntArray(map.prob.size) {
                    if (map.prob[it] > 0.3f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                },
            )
        }
    }

    private fun writePng(name: String, width: Int, height: Int, argb: IntArray) {
        val dir = File("build/ocr-debug").apply { mkdirs() }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width, height, argb, 0, width)
        javax.imageio.ImageIO.write(image, "png", File(dir, name))
        println("dumped ${File(dir, name).absolutePath}")
    }
}
