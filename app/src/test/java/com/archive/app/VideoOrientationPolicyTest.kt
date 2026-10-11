package com.archive.app

import android.content.pm.ActivityInfo
import com.archive.app.ui.components.PLAYBACK_SPEEDS
import com.archive.app.ui.components.speedRateLabel
import com.archive.app.ui.components.videoOrientationFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 视频页方向策略的回归测试。
 *
 * 背景：这里曾出过「进入全屏横屏后，再点按钮退不回竖屏」的问题 —— 根因不是按钮失效，
 * 而是退出全屏时只把方向交回了 SENSOR：系统自动旋转关闭的机型（MIUI 等）上屏幕会一直
 * 停在横屏。下面第二条用例把这次修复钉住，谁改回 SENSOR 谁就会看到测试失败。
 */
class VideoOrientationPolicyTest {

    @Test
    fun `首次进入视频页 跟随设备方向`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            videoOrientationFor(fullscreen = false, enteredFullscreenOnce = false),
        )
    }

    @Test
    fun `进入全屏 锁横屏`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            videoOrientationFor(fullscreen = true, enteredFullscreenOnce = false),
        )
    }

    @Test
    fun `退出全屏 显式要求竖屏（不能只交回 SENSOR）`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            videoOrientationFor(fullscreen = false, enteredFullscreenOnce = true),
        )
    }

    @Test
    fun `反复进出全屏 状态不残留`() {
        // 全屏 → 退出 → 再全屏 → 再退出：每次都要拿到确定的方向，不能有一次落回 SENSOR
        val sequence = listOf(
            videoOrientationFor(fullscreen = true, enteredFullscreenOnce = true),
            videoOrientationFor(fullscreen = false, enteredFullscreenOnce = true),
            videoOrientationFor(fullscreen = true, enteredFullscreenOnce = true),
            videoOrientationFor(fullscreen = false, enteredFullscreenOnce = true),
        )
        assertEquals(
            listOf(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ),
            sequence,
        )
    }

    /** 倍速档位与文案：菜单按钮上必须是「1.0x」这种倍率，不能是「倍速」这种相对说法。 */
    @Test
    fun `倍速档位文案是具体倍率`() {
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), PLAYBACK_SPEEDS)
        assertEquals("0.5x", speedRateLabel(0.5f))
        assertEquals("1.0x", speedRateLabel(1.0f))
        assertEquals("1.5x", speedRateLabel(1.5f))
        assertEquals("2.0x", speedRateLabel(2.0f))
        // 非标准档位（外部传入的其它倍率）也要有个像样的文案
        assertEquals("3.00x", speedRateLabel(3.0f))
    }
}
