package com.archive.app

import com.archive.app.ui.theme.AppColorMode
import com.archive.app.ui.theme.AppMonetStyle
import com.archive.app.ui.theme.isMonet
import com.archive.app.ui.theme.toMiuixMode
import com.archive.app.ui.theme.toPaletteStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

class ThemeSpecTest {

    @Test
    fun `color modes map to miuix scheme modes`() {
        assertEquals(ColorSchemeMode.System, AppColorMode.FOLLOW_SYSTEM.toMiuixMode())
        assertEquals(ColorSchemeMode.Light, AppColorMode.LIGHT.toMiuixMode())
        assertEquals(ColorSchemeMode.Dark, AppColorMode.DARK.toMiuixMode())
        assertEquals(ColorSchemeMode.MonetSystem, AppColorMode.MONET_SYSTEM.toMiuixMode())
        assertEquals(ColorSchemeMode.MonetLight, AppColorMode.MONET_LIGHT.toMiuixMode())
        assertEquals(ColorSchemeMode.MonetDark, AppColorMode.MONET_DARK.toMiuixMode())
    }

    @Test
    fun `monet styles map to palette styles`() {
        assertEquals(ThemePaletteStyle.TonalSpot, AppMonetStyle.TONAL_SPOT.toPaletteStyle())
        assertEquals(ThemePaletteStyle.Neutral, AppMonetStyle.NEUTRAL.toPaletteStyle())
        assertEquals(ThemePaletteStyle.Vibrant, AppMonetStyle.VIBRANT.toPaletteStyle())
        assertEquals(ThemePaletteStyle.Expressive, AppMonetStyle.EXPRESSIVE.toPaletteStyle())
    }

    @Test
    fun `monet detection matches the three monet modes`() {
        assertTrue(AppColorMode.MONET_SYSTEM.isMonet())
        assertTrue(AppColorMode.MONET_LIGHT.isMonet())
        assertTrue(AppColorMode.MONET_DARK.isMonet())
        assertFalse(AppColorMode.FOLLOW_SYSTEM.isMonet())
        assertFalse(AppColorMode.LIGHT.isMonet())
        assertFalse(AppColorMode.DARK.isMonet())
    }
}
