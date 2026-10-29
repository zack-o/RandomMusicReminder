package com.muyex.randommusicreminder.domain.settings

import org.junit.Assert.*
import org.junit.Test

class AppearanceSettingsTest {
    @Test fun customRequiresFullRgbAndPresetsRemainStable() {
        assertEquals(0x345CE3, AppearanceSettings().rgb)
        assertEquals(0x356859, AppearanceSettings(palette = ThemePalette.FOREST).rgb)
        assertEquals(0x245F96, AppearanceSettings(palette = ThemePalette.OCEAN).rgb)
        assertEquals(0xABCDEF, AppearanceSettings(palette = ThemePalette.CUSTOM, customColor = "#abcdef").rgb)
        listOf("red", "#fff", "#FFFFFFFF", "#12XX00", "").forEach { value -> assertThrows(IllegalArgumentException::class.java) { AppearanceSettings(customColor = value) } }
    }
    @Test fun readableForegroundForLightAndDarkAccents() {
        assertFalse(accentUsesWhite(0xFFFFFF)); assertTrue(accentUsesWhite(0x000000))
        assertTrue(accentUsesWhite(0x356859)); assertFalse(accentUsesWhite(0xFFFF00))
    }
    @Test fun arbitraryAccentAlsoRemainsReadableAsTextOnPageBackground() {
        listOf(0x000000, 0xFFFFFF, 0xFFFF00, 0xFF00FF, 0x336699).forEach { input ->
            listOf(false, true).forEach { dark ->
                val ink = colorLuminance(themeAccentRgb(input, dark))
                val paper = colorLuminance(if (dark) 0x121722 else 0xF5F7FC)
                assertTrue((maxOf(ink, paper) + .05) / (minOf(ink, paper) + .05) >= 4.5)
            }
        }
    }
}
