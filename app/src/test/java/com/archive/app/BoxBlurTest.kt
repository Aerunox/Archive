package com.archive.app

import com.archive.app.util.BoxBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 盒式模糊的算术正确性。
 *
 * 这是手写的滑动窗口实现，边界与差一（off-by-one）错误不会崩、只会让画面出现
 * 黑边或方向性拖影 —— 那种问题在设备上肉眼很容易漏掉，所以在这里钉住。
 */
class BoxBlurTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun redOf(c: Int) = (c ushr 16) and 0xFF
    private fun alphaOf(c: Int) = (c ushr 24) and 0xFF

    @Test
    fun uniformImageStaysUniform() {
        val w = 9
        val h = 7
        val pixels = IntArray(w * h) { argb(255, 120, 90, 200) }
        BoxBlur.apply(pixels, w, h, radius = 3, passes = 3)
        pixels.forEach { c ->
            assertEquals(120, redOf(c))
            assertEquals(255, alphaOf(c))
        }
    }

    @Test
    fun alphaIsPreserved() {
        val w = 6
        val h = 6
        val pixels = IntArray(w * h) { argb(128, 200, 200, 200) }
        BoxBlur.apply(pixels, w, h, radius = 2, passes = 2)
        pixels.forEach { c -> assertEquals(128, alphaOf(c)) }
    }

    @Test
    fun verticalLineSpreadsSymmetrically() {
        // 中间一条红色竖线，其余为黑：模糊后应当左右对称，且中心不再是纯红
        val w = 11
        val h = 5
        val pixels = IntArray(w * h) { argb(255, 0, 0, 0) }
        for (y in 0 until h) pixels[y * w + w / 2] = argb(255, 255, 0, 0)
        BoxBlur.apply(pixels, w, h, radius = 1, passes = 3)

        val mid = h / 2 * w
        for (d in 1..4) {
            assertEquals(
                "距离 $d 处左右应对称",
                redOf(pixels[mid + w / 2 - d]),
                redOf(pixels[mid + w / 2 + d]),
            )
        }
        assertTrue("中心应被周围拉低", redOf(pixels[mid + w / 2]) < 255)
        assertTrue("两侧应被点亮", redOf(pixels[mid + w / 2 - 1]) > 0)
    }

    @Test
    fun edgesDoNotFadeToBlack() {
        // 全白图：若边界处理成「窗外视为 0」，边缘会变暗 —— 这正是要防的黑边
        val w = 8
        val h = 8
        val pixels = IntArray(w * h) { argb(255, 255, 255, 255) }
        BoxBlur.apply(pixels, w, h, radius = 3, passes = 3)
        val corners = listOf(0, w - 1, (h - 1) * w, h * w - 1)
        corners.forEach { i ->
            assertEquals("边缘不应变暗（黑边）", 255, redOf(pixels[i]))
        }
    }

    @Test
    fun invalidRadiusIsNoOp() {
        val w = 4
        val h = 4
        val pixels = IntArray(w * h) { argb(255, 10, 20, 30) }
        val copy = pixels.copyOf()
        BoxBlur.apply(pixels, w, h, radius = 0, passes = 3)
        assertTrue(pixels.contentEquals(copy))
    }

    @Test
    fun blurActuallyChangesContent() {
        // 棋盘格：模糊后必然与原图不同（否则说明根本没生效）
        val w = 12
        val h = 12
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if ((x + y) % 2 == 0) argb(255, 20, 20, 20) else argb(255, 230, 230, 230)
        }
        val before = pixels.copyOf()
        BoxBlur.apply(pixels, w, h, radius = 2, passes = 3)
        assertNotEquals(before.toList(), pixels.toList())
        // 相邻像素差应显著变小（棋盘反差被抹平）
        val diffBefore = kotlin.math.abs(redOf(before[0]) - redOf(before[1]))
        val diffAfter = kotlin.math.abs(redOf(pixels[0]) - redOf(pixels[1]))
        assertTrue("相邻反差应被抹平：$diffBefore → $diffAfter", diffAfter < diffBefore)
    }
}
