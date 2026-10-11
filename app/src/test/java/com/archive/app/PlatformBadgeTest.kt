package com.archive.app

import androidx.compose.ui.graphics.Color
import com.archive.app.ui.theme.serviceBrandColor
import com.archive.app.ui.theme.servicePlatformLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 平台徽标的品牌色与文案映射。
 *
 * 钉住「Patreon 红 / Fanbox 蓝」这条约定 —— 徽标的文字色与底色都取自这里，
 * 一旦有人把它改回「所有平台共用主题色」，或改了归一化规则，这里会立刻失败。
 */
class PlatformBadgeTest {

    @Test
    fun patreonUsesItsBrandColor() {
        assertEquals(Color(0xFFF96854), serviceBrandColor("patreon"))
    }

    @Test
    fun fanboxUsesPixivBlue() {
        assertEquals(Color(0xFF0096FA), serviceBrandColor("fanbox"))
    }

    @Test
    fun unknownServiceHasNoBrandColor() {
        assertNull(serviceBrandColor("gumroad"))
        assertNull(serviceBrandColor(null))
    }

    @Test
    fun brandColorToleratesCaseAndWhitespace() {
        assertEquals(Color(0xFFF96854), serviceBrandColor(" Patreon "))
        assertEquals(Color(0xFF0096FA), serviceBrandColor("FANBOX"))
    }

    @Test
    fun labelIsNormalized() {
        assertEquals("PATREON", servicePlatformLabel("patreon"))
        assertEquals("PATREON", servicePlatformLabel(" Patreon "))
        assertEquals("FANBOX", servicePlatformLabel("FANBOX"))
    }

    @Test
    fun unknownServiceKeepsRawNameUppercased() {
        assertEquals("GUMROAD", servicePlatformLabel("gumroad"))
    }

    /** 该字段由 Gson 填充，字段缺失时运行时会是 null —— 不能崩。 */
    @Test
    fun blankServiceRendersEmptyInsteadOfCrashing() {
        assertEquals("", servicePlatformLabel(null))
        assertEquals("", servicePlatformLabel("   "))
    }
}
