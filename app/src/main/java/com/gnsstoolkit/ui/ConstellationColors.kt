package com.gnsstoolkit.ui

import androidx.compose.ui.graphics.Color
import com.gnsstoolkit.data.CelestialBody
import com.gnsstoolkit.data.Constellation

/**
 * 星座配色固定，不随主题变化——天空图与信号条上必须一眼区分系统。
 * 色值取自 Material You 之外的辅助色板，浅色/深色背景都有足够对比度。
 */
val Constellation.color: Color
    get() = when (this) {
        Constellation.GPS -> Color(0xFF3D7BFF)
        Constellation.GLONASS -> Color(0xFFE5484D)
        Constellation.GALILEO -> Color(0xFF17A34A)
        Constellation.BEIDOU -> Color(0xFFF09100)
        Constellation.QZSS -> Color(0xFF8E4EC6)
        Constellation.IRNSS -> Color(0xFF00A5A8)
        Constellation.SBAS -> Color(0xFF7C8794)
        Constellation.UNKNOWN -> Color(0xFF9AA0A6)
    }

/** 用于暗色背景上的文字/描边，略微提亮。 */
fun Constellation.onSurfaceColor(isLight: Boolean): Color =
    if (isLight) color else color.copy(red = (color.red * 1.15f).coerceAtMost(1f),
        green = (color.green * 1.15f).coerceAtMost(1f),
        blue = (color.blue * 1.15f).coerceAtMost(1f))

/** 星空图里的天体配色：暖色留给太阳与内行星，冷色给外行星。 */
fun CelestialBody.bodyColor(): Color = when (this) {
    CelestialBody.SUN -> Color(0xFFFFB300)
    CelestialBody.MOON -> Color(0xFFD7DDE3)
    CelestialBody.MERCURY -> Color(0xFF9AA6B2)
    CelestialBody.VENUS -> Color(0xFFFFE082)
    CelestialBody.MARS -> Color(0xFFE2603F)
    CelestialBody.JUPITER -> Color(0xFFD9A066)
    CelestialBody.SATURN -> Color(0xFFC9B37E)
    CelestialBody.URANUS -> Color(0xFF7FD8E0)
    CelestialBody.NEPTUNE -> Color(0xFF6C86E8)
}
