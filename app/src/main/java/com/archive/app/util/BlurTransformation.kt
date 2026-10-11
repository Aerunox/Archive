package com.archive.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import coil3.size.Size
import coil3.transform.Transformation

/**
 * 缩略图模糊（隐私开关，默认开启）。
 *
 * 观感升级：早先只是「缩到 1/10 再放大」的双线性插值 —— 细节被抹掉的同时也留下了
 * 明显的涂抹痕与原图边缘残留，看起来廉价。现在改为：
 *
 * 1. **先缩小**：在小图上做卷积，代价可以忽略（长边通常只剩一两百像素）；
 * 2. **三次可分离盒式模糊**（横向 + 纵向各三次）—— 数学上已非常接近高斯模糊，
 *    过渡平滑、没有方块感；
 * 3. **磨砂玻璃调色**：放大回原尺寸时叠加约 -22% 饱和度与轻微压暗，
 *    这是「磨砂/毛玻璃」质感的关键一步，纯模糊不会有这种厚度感。
 *
 * 结果仍是**确定性**的（不依赖 RenderEffect / RenderScript），各 API 级别一致。
 *
 * @param factor 缩小倍数（越大越省，模糊也越强）。
 * @param passes 盒式模糊迭代次数（3 次即接近高斯）。
 * @param radius 在小图坐标系下的模糊半径（像素）。
 * @param saturation 保留的饱和度（<1 降饱和，磨砂感来源之一）。
 * @param dim 整体亮度系数（<1 轻微压暗，让色块更沉稳）。
 */
class BlurTransformation(
    private val factor: Int = 8,
    private val passes: Int = 3,
    private val radius: Int = 6,
    private val saturation: Float = 0.78f,
    private val dim: Float = 0.93f,
) : Transformation() {

    /** 参数全部进缓存键：调参后旧缓存不会继续命中（否则看不到新效果）。 */
    override val cacheKey: String = "archive-blur-$factor-$passes-$radius-$saturation-$dim"

    override suspend fun transform(bitmap: Bitmap, size: Size): Bitmap {
        val smallW = (bitmap.width / factor).coerceAtLeast(1)
        val smallH = (bitmap.height / factor).coerceAtLeast(1)
        // 已经足够小（或本来就是 1×1 占位）就不必处理
        if (smallW >= bitmap.width && smallH >= bitmap.height) return bitmap

        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        val pixels = IntArray(smallW * smallH)
        small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
        BoxBlur.apply(pixels, smallW, smallH, radius, passes)
        small.setPixels(pixels, 0, smallW, 0, 0, smallW, smallH)

        // 放大回原尺寸 + 降饱和/压暗（FILTER_BITMAP_FLAG = 双线性，让过渡更柔）
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    setSaturation(saturation)
                    postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                dim, 0f, 0f, 0f, 0f,
                                0f, dim, 0f, 0f, 0f,
                                0f, 0f, dim, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f,
                            ),
                        ),
                    )
                },
            )
        }
        canvas.drawBitmap(small, null, Rect(0, 0, output.width, output.height), paint)
        small.recycle()
        return output
    }

}

/**
 * 纯算术的盒式模糊（不依赖任何 Android 类型，因此可以单元测试）。
 *
 * 算法：可分离的滑动窗口均值，横向一遍、纵向一遍为一轮，多轮叠加逼近高斯。
 * 边界按最外侧像素延展，避免出现黑边。
 */
internal object BoxBlur {

    /** 对 [pixels]（ARGB_8888 行优先，宽 [w] 高 [h]）原地模糊 [passes] 轮。 */
    fun apply(pixels: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3) {
        if (radius <= 0 || w <= 0 || h <= 0) return
        val tmp = IntArray(pixels.size)
        repeat(passes) {
            blurPass(pixels, tmp, w, h, radius, horizontal = true)
            blurPass(tmp, pixels, w, h, radius, horizontal = false)
        }
    }

    /** 单向滑动窗口均值（含 alpha 通道）。 */
    private fun blurPass(
        input: IntArray,
        output: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        horizontal: Boolean,
    ) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        val window = radius * 2 + 1

        for (o in 0 until outer) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (k in -radius..radius) {
                val c = input[index(o, k.coerceIn(0, inner - 1), w, horizontal)]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (i in 0 until inner) {
                output[index(o, i, w, horizontal)] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)

                val leaving = input[index(o, (i - radius).coerceIn(0, inner - 1), w, horizontal)]
                val entering = input[index(o, (i + radius + 1).coerceIn(0, inner - 1), w, horizontal)]
                sumA += ((entering ushr 24) and 0xFF) - ((leaving ushr 24) and 0xFF)
                sumR += ((entering ushr 16) and 0xFF) - ((leaving ushr 16) and 0xFF)
                sumG += ((entering ushr 8) and 0xFF) - ((leaving ushr 8) and 0xFF)
                sumB += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    /** 把 (o, i) 映射回 ARGB 数组下标：横向扫描时 o 是行、i 是列。 */
    private fun index(o: Int, i: Int, width: Int, horizontal: Boolean): Int =
        if (horizontal) o * width + i else i * width + o
}
