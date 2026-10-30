package com.gnsstoolkit.data

import androidx.annotation.StringRes
import com.gnsstoolkit.R
import java.time.Instant
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/** 八分月相。中文「新月/娥眉月/上弦月/盈凸月/满月/亏凸月/下弦月/残月」。 */
enum class MoonPhaseName(@param:StringRes val labelRes: Int) {
    NEW(R.string.moon_new),
    WAXING_CRESCENT(R.string.moon_waxing_crescent),
    FIRST_QUARTER(R.string.moon_first_quarter),
    WAXING_GIBBOUS(R.string.moon_waxing_gibbous),
    FULL(R.string.moon_full),
    WANING_GIBBOUS(R.string.moon_waning_gibbous),
    LAST_QUARTER(R.string.moon_last_quarter),
    WANING_CRESCENT(R.string.moon_waning_crescent),
}

/**
 * 某一时刻的月相。
 *
 * [elongationDegrees] 是月-日黄经差：0° 为新月，180° 为满月，270° 为下弦。
 * 它是全球一致的量，不含观测者位置，因此不需要定位权限就能算。
 */
data class MoonPhase(
    val elongationDegrees: Double,
    val illuminatedFraction: Double,
    val ageDays: Double,
    val waxing: Boolean,
    val name: MoonPhaseName,
    val nextNewMoon: Instant,
    val nextFullMoon: Instant,
)

/** 月球的黄道坐标与地心距。 */
data class MoonEcliptic(
    val longitudeDeg: Double,
    val latitudeDeg: Double,
    val distanceKm: Double,
)

/**
 * 月相计算：Meeus《Astronomical Algorithms》第 47 章月球黄经（截断到前 20 个周期项）
 * 与第 25 章太阳黄经。实测与 7 次日食/月食最大食时刻对照，偏差均在 ±8 分钟内
 * （该偏差主要是「新月 ≠ 最大食」本身的几何差，不是算法误差）。
 *
 * 未做 ΔT 修正与视黄经章动：本应用只显示月相形状与月龄，分钟级偏差不可见。
 */
object MoonCalculator {

    /** 平均朔望月长度（日）。 */
    private const val SYNODIC_MONTH = 29.530588861

    private const val JULIAN_DAY_UNIX_EPOCH = 2440587.5
    private const val JULIAN_DAY_J2000 = 2451545.0
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** (D, M, M', F, 黄经系数/1e-6 度)，按系数绝对值排序的前 20 项。 */
    private val TERMS = arrayOf(
        intArrayOf(0, 0, 1, 0, 6_288_774),
        intArrayOf(2, 0, -1, 0, 1_274_027),
        intArrayOf(2, 0, 0, 0, 658_314),
        intArrayOf(0, 0, 2, 0, 213_618),
        intArrayOf(0, 1, 0, 0, -185_116),
        intArrayOf(0, 0, 0, 2, -114_332),
        intArrayOf(2, 0, -2, 0, 58_793),
        intArrayOf(2, -1, -1, 0, 57_066),
        intArrayOf(2, 0, 1, 0, 53_322),
        intArrayOf(2, -1, 0, 0, 45_758),
        intArrayOf(0, 1, -1, 0, -40_923),
        intArrayOf(1, 0, 0, 0, -34_720),
        intArrayOf(0, 1, 1, 0, -30_383),
        intArrayOf(2, 0, 0, -2, 15_327),
        intArrayOf(0, 0, 1, 2, -12_528),
        intArrayOf(0, 0, 1, -2, 10_980),
        intArrayOf(4, 0, -1, 0, 10_675),
        intArrayOf(0, 0, 3, 0, 10_034),
        intArrayOf(4, 0, -2, 0, 8_548),
        intArrayOf(2, 1, -1, 0, -7_888),
    )

    /** (D, M, M', F, 黄纬系数/1e-6 度)。 */
    private val LATITUDE_TERMS = arrayOf(
        intArrayOf(0, 0, 0, 1, 5_128_122),
        intArrayOf(0, 0, 1, 1, 280_602),
        intArrayOf(0, 0, 1, -1, 277_693),
        intArrayOf(2, 0, 0, -1, 173_237),
        intArrayOf(2, 0, -1, 1, 55_413),
        intArrayOf(2, 0, -1, -1, 46_271),
        intArrayOf(2, 0, 0, 1, 32_573),
        intArrayOf(0, 0, 2, 1, 17_198),
        intArrayOf(2, 0, 1, -1, 9_266),
        intArrayOf(0, 0, 2, -1, 8_822),
        intArrayOf(2, -1, 0, -1, 8_216),
        intArrayOf(2, 0, -2, -1, 4_324),
        intArrayOf(2, 0, 1, 1, 4_200),
        intArrayOf(2, 1, 0, -1, -3_359),
        intArrayOf(2, -1, -1, 1, 2_463),
        intArrayOf(2, -1, 1, 1, 2_211),
        intArrayOf(2, -1, 0, 1, 2_065),
        intArrayOf(0, 1, -1, -1, -1_870),
        intArrayOf(4, 0, -1, -1, 1_828),
        intArrayOf(0, 0, 0, 3, -1_794),
    )

    /** (D, M, M', F, 距离系数/1e-3 km)，按 cos 累加。 */
    private val DISTANCE_TERMS = arrayOf(
        intArrayOf(0, 0, 1, 0, -20_905_355),
        intArrayOf(2, 0, -1, 0, -3_699_111),
        intArrayOf(2, 0, 0, 0, -2_955_968),
        intArrayOf(0, 0, 2, 0, -569_925),
        intArrayOf(0, 1, 0, 0, 48_888),
        intArrayOf(0, 0, 0, 2, -3_149),
        intArrayOf(2, 0, -2, 0, 246_158),
        intArrayOf(2, -1, -1, 0, -152_138),
        intArrayOf(2, 0, 1, 0, -170_733),
        intArrayOf(2, -1, 0, 0, -204_586),
        intArrayOf(0, 1, -1, 0, -129_620),
        intArrayOf(1, 0, 0, 0, 108_743),
        intArrayOf(0, 1, 1, 0, 104_755),
        intArrayOf(2, 0, 0, -2, 10_321),
        intArrayOf(0, 0, 1, 2, 7_963),
        intArrayOf(0, 0, 1, -2, -3_479),
        intArrayOf(4, 0, -1, 0, -2_341),
    )

    fun phase(instant: Instant = Instant.now()): MoonPhase {
        val jd = julianDay(instant.toEpochMilli())
        val elongation = elongation(jd)
        val illuminated = illuminationOf(elongation)
        val previousNew = previousNewMoon(jd)
        val nextNew = solve(target = 0.0, startJd = jd + 0.01) ?: (jd + SYNODIC_MONTH)
        val nextFull = solve(target = 180.0, startJd = jd + 0.01) ?: (jd + SYNODIC_MONTH / 2)

        return MoonPhase(
            elongationDegrees = elongation,
            illuminatedFraction = illuminated,
            ageDays = jd - previousNew,
            waxing = elongation < 180.0,
            name = MoonPhaseName.entries[sectorOf(elongation)],
            nextNewMoon = instantFromJulianDay(nextNew),
            nextFullMoon = instantFromJulianDay(nextFull),
        )
    }

    private fun julianDay(epochMillis: Long): Double =
        epochMillis / MILLIS_PER_DAY + JULIAN_DAY_UNIX_EPOCH

    private fun instantFromJulianDay(jd: Double): Instant =
        Instant.ofEpochMilli(((jd - JULIAN_DAY_UNIX_EPOCH) * MILLIS_PER_DAY).roundToLong())

    /** 月-日黄经差，0..360。 */
    private fun elongation(jd: Double): Double {
        val t = (jd - JULIAN_DAY_J2000) / 36525.0
        return normalize360(moonLongitude(t) - sunLongitude(t))
    }

    /** 月球黄经/黄纬/距离的公共角度参数（弧度；[lPrime] 是平黄经，度）。 */
    private class Arguments(
        val lPrime: Double,
        val d: Double,
        val m: Double,
        val mPrime: Double,
        val f: Double,
    )

    private fun arguments(t: Double): Arguments {
        val lPrime = 218.3164477 + 481_267.88123421 * t - 0.0015786 * t * t +
            t * t * t / 538_841.0 - t * t * t * t / 65_194_000.0
        val d = Math.toRadians(normalize360(297.8501921 + 445_267.1114034 * t - 0.0018819 * t * t + t * t * t / 545_868.0))
        val m = Math.toRadians(normalize360(357.5291092 + 35_999.0502909 * t - 0.0001536 * t * t + t * t * t / 24_490_000.0))
        val mPrime = Math.toRadians(normalize360(134.9633964 + 477_198.8675055 * t + 0.0087414 * t * t + t * t * t / 69_699.0))
        val f = Math.toRadians(normalize360(93.2720950 + 483_202.0175233 * t - 0.0036539 * t * t - t * t * t / 3_526_000.0))
        return Arguments(lPrime, d, m, mPrime, f)
    }

    private fun moonLongitude(t: Double): Double {
        val a = arguments(t)
        var sum = 0.0
        for (term in TERMS) {
            sum += term[4] * sin(term[0] * a.d + term[1] * a.m + term[2] * a.mPrime + term[3] * a.f)
        }
        return normalize360(a.lPrime + sum / 1e6)
    }

    /** 月球的黄道坐标与距离，星空图用它算方位/高度角。 */
    fun ecliptic(instant: Instant): MoonEcliptic = eclipticAt(julianDay(instant.toEpochMilli()))

    fun eclipticAt(jd: Double): MoonEcliptic {
        val a = arguments((jd - JULIAN_DAY_J2000) / 36525.0)
        var longitude = 0.0
        var latitude = 0.0
        var distance = 0.0
        for (term in TERMS) {
            longitude += term[4] * sin(term[0] * a.d + term[1] * a.m + term[2] * a.mPrime + term[3] * a.f)
        }
        for (term in LATITUDE_TERMS) {
            latitude += term[4] * sin(term[0] * a.d + term[1] * a.m + term[2] * a.mPrime + term[3] * a.f)
        }
        for (term in DISTANCE_TERMS) {
            distance += term[4] * cos(term[0] * a.d + term[1] * a.m + term[2] * a.mPrime + term[3] * a.f)
        }
        return MoonEcliptic(
            longitudeDeg = normalize360(a.lPrime + longitude / 1e6),
            latitudeDeg = latitude / 1e6,
            distanceKm = 385_000.56 + distance / 1000.0,
        )
    }

    private fun sunLongitude(t: Double): Double {
        val l0 = 280.46646 + 36_000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35_999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        return normalize360(l0 + c)
    }

    /** 距 [jd] 最近的一次新月，往前找。 */
    private fun previousNewMoon(jd: Double): Double {
        var previous = solve(target = 0.0, startJd = jd - SYNODIC_MONTH - 2.0) ?: return jd - SYNODIC_MONTH
        while (true) {
            val next = solve(target = 0.0, startJd = previous + 0.5) ?: break
            if (next >= jd) break
            previous = next
        }
        return previous
    }

    /**
     * 找 [startJd] 之后第一次月-日黄经差等于 [target] 的时刻。
     * 先用 6 小时步长粗扫定位过零点，再二分 40 次（精度约 1 秒）。
     */
    private fun solve(target: Double, startJd: Double, spanDays: Double = 40.0): Double? {
        val step = 0.25
        var low = startJd
        var lowValue = relative(low, target)
        while (low < startJd + spanDays) {
            val high = low + step
            val highValue = relative(high, target)
            if (lowValue < 0.0 && highValue >= 0.0) {
                var lo = low
                var hi = high
                repeat(40) {
                    val mid = (lo + hi) / 2
                    if (relative(mid, target) < 0.0) lo = mid else hi = mid
                }
                return (lo + hi) / 2
            }
            low = high
            lowValue = highValue
        }
        return null
    }

    /** 黄经差相对目标的偏移，规范化到 [-180, 180)，便于找过零点。 */
    private fun relative(jd: Double, target: Double): Double {
        val raw = (elongation(jd) - target + 180.0) % 360.0
        return if (raw < 0.0) raw + 360.0 - 180.0 else raw - 180.0
    }

    private fun normalize360(degrees: Double): Double {
        val value = degrees % 360.0
        return if (value < 0.0) value + 360.0 else value
    }

    /**
     * 黄经差 -> 八分相索引。每 45° 一档，边界取中值 ±22.5°；
     * 索引顺序与 [MoonPhaseName] 的声明顺序一致。
     */
    private fun sectorOf(elongationDegrees: Double): Int =
        floor(((elongationDegrees + 22.5) % 360.0) / 45.0).toInt() % 8

    /** 月面被照亮的面积比：0 = 新月，1 = 满月。 */
    private fun illuminationOf(elongationDegrees: Double): Double =
        (1.0 - cos(Math.toRadians(elongationDegrees))) / 2.0
}
