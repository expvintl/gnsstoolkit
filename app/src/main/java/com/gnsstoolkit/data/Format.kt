package com.gnsstoolkit.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class UnitSystem { METRIC, IMPERIAL, NAUTICAL, AVIATION }

/** 带单位的数值，UI 负责排版。 */
data class Quantity(val value: String, val unit: String? = null)

private val METERS_PER_MILE = 1609.344
private val METERS_PER_NAUTICAL_MILE = 1852.0
private val FEET_PER_METER = 3.280839895

private fun round(value: Double, digits: Int): String =
    String.format(Locale.getDefault(), "%.${digits}f", value)

fun formatDistance(meters: Float?, units: UnitSystem): Quantity? {
    val m = meters?.toDouble() ?: return null
    return when (units) {
        UnitSystem.METRIC ->
            if (m < 1000.0) Quantity(round(m, 0), "m") else Quantity(round(m / 1000.0, 2), "km")
        UnitSystem.IMPERIAL -> {
            val feet = m * FEET_PER_METER
            if (feet < 5280.0) Quantity(round(feet, 0), "ft")
            else Quantity(round(m / METERS_PER_MILE, 2), "mi")
        }
        UnitSystem.NAUTICAL ->
            if (m < 1000.0) Quantity(round(m, 0), "m")
            else Quantity(round(m / METERS_PER_NAUTICAL_MILE, 2), "nmi")
        UnitSystem.AVIATION ->
            if (m < 1000.0) Quantity(round(m, 0), "m")
            else Quantity(round(m / METERS_PER_NAUTICAL_MILE, 1), "NM")
    }
}

fun formatAltitude(meters: Double?, units: UnitSystem): Quantity? {
    val m = meters ?: return null
    return when (units) {
        UnitSystem.IMPERIAL, UnitSystem.AVIATION -> Quantity(round(m * FEET_PER_METER, 0), "ft")
        UnitSystem.METRIC, UnitSystem.NAUTICAL -> Quantity(round(m, 1), "m")
    }
}

/**
 * 速度格式化。
 *
 * [compact] 为 true 时给「小单位」：公制 m/s、英制 ft/s、航海与航空 m/s，
 * 之外仍是 km/h / mph / kn / kt。位置页点一下速度卡就在两套之间切换。
 */
fun formatSpeed(metersPerSecond: Float?, units: UnitSystem, compact: Boolean = false): Quantity? {
    val v = metersPerSecond?.toDouble() ?: return null
    if (compact) {
        return when (units) {
            UnitSystem.METRIC -> Quantity(round(v, 1), "m/s")
            UnitSystem.IMPERIAL -> Quantity(round(v * 3.280840, 1), "ft/s")
            UnitSystem.NAUTICAL, UnitSystem.AVIATION -> Quantity(round(v, 1), "m/s")
        }
    }
    return when (units) {
        UnitSystem.METRIC -> Quantity(round(v * 3.6, 1), "km/h")
        UnitSystem.IMPERIAL -> Quantity(round(v * 2.236936, 1), "mph")
        UnitSystem.NAUTICAL -> Quantity(round(v * 1.943844, 1), "kn")
        UnitSystem.AVIATION -> Quantity(round(v * 1.943844, 0), "kt")
    }
}

fun formatAngle(degrees: Float?): Quantity? = degrees?.let {
    Quantity(round((((it % 360f) + 360f) % 360f).toDouble(), 0), "°")
}

/** 31°14'32.5"N 形式的度分秒。 */
fun formatDms(value: Double, isLatitude: Boolean): String {
    val hemisphere = when {
        isLatitude && value >= 0 -> "N"
        isLatitude -> "S"
        value >= 0 -> "E"
        else -> "W"
    }
    val abs = kotlin.math.abs(value)
    val degrees = abs.toInt()
    val minutesFull = (abs - degrees) * 60.0
    val minutes = minutesFull.toInt()
    val seconds = (minutesFull - minutes) * 60.0
    return String.format(Locale.getDefault(), "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, hemisphere)
}

fun formatDecimalDegrees(value: Double): String =
    String.format(Locale.getDefault(), "%.6f°", value)

private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(clockFormatter)

fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateFormatter)

/** 用于头部卡片的紧凑坐标展示。 */
fun formatCoordinatePair(latitude: Double, longitude: Double): String =
    String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude)
