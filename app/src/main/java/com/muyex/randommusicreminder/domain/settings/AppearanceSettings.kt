package com.muyex.randommusicreminder.domain.settings

import kotlin.math.pow

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class ThemePalette(val label: String, val hex: String) {
    FOREST("森林", "#356859"), OCEAN("海蓝", "#245F96"), DAWN("晨蓝", "#345CE3"), VIOLET("紫罗兰", "#72579B"), AMBER("琥珀", "#835B22"), CUSTOM("自定义", "#356859")
}
data class AppearanceSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val palette: ThemePalette = ThemePalette.DAWN, val customColor: String = "#356859") {
    init { require(Regex("^#[0-9a-fA-F]{6}$").matches(customColor)) { "自定义颜色须为#RRGGBB" } }
    val rgb: Int get() = (if (palette == ThemePalette.CUSTOM) customColor else palette.hex).drop(1).toInt(16)
}

fun accentUsesWhite(rgb: Int): Boolean {
    val luminance = colorLuminance(rgb)
    return 1.05 / (luminance + .05) >= (luminance + .05) / .05
}

internal fun colorLuminance(rgb: Int): Double {
    fun linear(value: Int): Double = (value / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
    return .2126 * linear(rgb shr 16 and 255) + .7152 * linear(rgb shr 8 and 255) + .0722 * linear(rgb and 255)
}

fun themeAccentRgb(rgb: Int, dark: Boolean): Int {
    val background = colorLuminance(if (dark) 0x121722 else 0xF5F7FC)
    for (step in 0..100) {
        fun channel(shift: Int): Int { val original = rgb shr shift and 255; return (original + ((if (dark) 255 else 0) - original) * step / 100).coerceIn(0, 255) }
        val candidate = (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        val luminance = colorLuminance(candidate)
        if ((maxOf(luminance, background) + .05) / (minOf(luminance, background) + .05) >= 4.5) return candidate
    }
    return if (dark) 0xFFFFFF else 0
}
