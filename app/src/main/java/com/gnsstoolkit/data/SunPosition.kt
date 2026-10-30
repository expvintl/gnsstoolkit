package com.gnsstoolkit.data

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** 太阳直射点：此刻太阳正好在天顶的位置。 */
data class SunPosition(
    val declinationDegrees: Double,
    val subsolarLongitudeDegrees: Double,
) {
    val subsolarLatitudeDegrees: Double get() = declinationDegrees
}

/**
 * 太阳位置与晨昏线的解析计算。
 *
 * 用天文年历的低精度公式（赤经 + 格林尼治恒星时），太阳赤纬误差 < 0.01°，
 * 比 [SolarCalculator] 里那套 NOAA 日角式（误差可达 0.5°）更准。
 */
object SunCalculator {

    private const val JULIAN_DAY_UNIX_EPOCH = 2440587.5
    private const val JULIAN_DAY_J2000 = 2451545.0
    private const val MILLIS_PER_DAY = 86_400_000.0

    fun position(instant: Instant = Instant.now()): SunPosition {
        val julianDay = instant.toEpochMilli() / MILLIS_PER_DAY + JULIAN_DAY_UNIX_EPOCH
        val n = julianDay - JULIAN_DAY_J2000

        val meanLongitude = 280.460 + 0.9856474 * n
        val meanAnomaly = Math.toRadians(357.528 + 0.9856003 * n)
        val eclipticLongitude = Math.toRadians(
            meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly),
        )
        val obliquity = Math.toRadians(23.439 - 0.0000004 * n)

        val declination = Math.toDegrees(
            asin((sin(obliquity) * sin(eclipticLongitude)).coerceIn(-1.0, 1.0)),
        )
        val rightAscension = Math.toDegrees(
            atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude)),
        )
        val siderealTime = normalize360(280.46061837 + 360.98564736629 * n)
        val longitude = normalizeSigned(rightAscension - siderealTime)

        return SunPosition(declinationDegrees = declination, subsolarLongitudeDegrees = longitude)
    }

    /** 太阳高度角（度）。大于 0 即白天。 */
    fun elevationDegrees(latitude: Double, longitude: Double, sun: SunPosition): Double {
        val lat = Math.toRadians(latitude)
        val decl = Math.toRadians(sun.declinationDegrees)
        val hourAngle = Math.toRadians(longitude - sun.subsolarLongitudeDegrees)
        val sinElevation = sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(hourAngle)
        return Math.toDegrees(asin(sinElevation.coerceIn(-1.0, 1.0)))
    }

    fun isDaylight(latitude: Double, longitude: Double, sun: SunPosition): Boolean =
        elevationDegrees(latitude, longitude, sun) >= 0.0

    /**
     * 某条经线上属于夜晚的纬度区间，[lo] 到 [hi]，角度制。
     *
     * 太阳高度角可以写成 `h(φ) = R · sin(φ + ψ)`，其中
     * `ψ = atan2(cos δ · cos H, sin δ)`、`H` 是该经线与直射点的经度差。
     * 因此夜半球落在两个零点之间，而这条经线上最多只有一段连续的夜。
     * 整条经线都是白天时返回 null。
     */
    fun nightBand(deltaLongitudeDegrees: Double, declinationDegrees: Double): ClosedFloatingPointRange<Double>? {
        val psi = Math.toDegrees(
            atan2(
                cos(Math.toRadians(declinationDegrees)) * cos(Math.toRadians(deltaLongitudeDegrees)),
                sin(Math.toRadians(declinationDegrees)),
            ),
        )

        // h = 0 的纬度：φ = k·180 − ψ
        val cuts = ArrayList<Double>(4)
        cuts.add(-90.0)
        var k = floor((-90.0 + psi) / 180.0).toInt() - 1
        while (true) {
            val phi = k * 180.0 - psi
            if (phi > 90.0) break
            if (phi > -90.0 + 1e-9 && phi < 90.0 - 1e-9) cuts.add(phi)
            k++
        }
        cuts.add(90.0)

        for (i in 0 until cuts.size - 1) {
            val lo = cuts[i]
            val hi = cuts[i + 1]
            if (hi - lo < 1e-9) continue
            val middle = (lo + hi) / 2.0
            if (sin(Math.toRadians(middle + psi)) < 0.0) return lo..hi
        }
        return null
    }

    private fun normalize360(degrees: Double): Double {
        val wrapped = degrees % 360.0
        return if (wrapped < 0.0) wrapped + 360.0 else wrapped
    }

    private fun normalizeSigned(degrees: Double): Double {
        val wrapped = normalize360(degrees)
        return if (wrapped > 180.0) wrapped - 360.0 else wrapped
    }
}
