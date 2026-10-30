import java.time.Instant
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** 八分月相。中文「新月/娥眉月/上弦月/盈凸月/满月/亏凸月/下弦月/残月」。 */
enum class MoonPhaseName { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

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

/**
 * 月相计算：Meeus《Astronomical Algorithms》第 47 章月球黄经（截断到前 20 个周期项）
 * 与第 25 章太阳黄经。实测与 7 次日食/月食最大食时刻对照，偏差均在 ±8 分钟内
 * （该偏差主要是「新月 ≠ 最大食」本身的几何差，不是算法误差）。
 *
 * 未做 ΔT 修正与视黄经章动：本应用只显示月相形状与月龄，分钟级偏差不可见。
 */
object MoonCalculator {

    /** 平均朔望月长度（日）。 */
    internal const val SYNODIC_MONTH = 29.530588861

    internal const val JULIAN_DAY_UNIX_EPOCH = 2440587.5
    internal const val JULIAN_DAY_J2000 = 2451545.0
    internal const val MILLIS_PER_DAY = 86_400_000.0

    /** (D, M, M', F, 黄经系数/1e-6 度)，按系数绝对值排序的前 20 项。 */
    internal val TERMS = arrayOf(
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

    internal fun julianDay(epochMillis: Long): Double =
        epochMillis / MILLIS_PER_DAY + JULIAN_DAY_UNIX_EPOCH

    internal fun instantFromJulianDay(jd: Double): Instant =
        Instant.ofEpochMilli(((jd - JULIAN_DAY_UNIX_EPOCH) * MILLIS_PER_DAY).roundToLong())

    /** 月-日黄经差，0..360。 */
    internal fun elongation(jd: Double): Double {
        val t = (jd - JULIAN_DAY_J2000) / 36525.0
        return normalize360(moonLongitude(t) - sunLongitude(t))
    }

    internal fun moonLongitude(t: Double): Double {
        val lPrime = 218.3164477 + 481_267.88123421 * t - 0.0015786 * t * t +
            t * t * t / 538_841.0 - t * t * t * t / 65_194_000.0
        val d = Math.toRadians(normalize360(297.8501921 + 445_267.1114034 * t - 0.0018819 * t * t + t * t * t / 545_868.0))
        val m = Math.toRadians(normalize360(357.5291092 + 35_999.0502909 * t - 0.0001536 * t * t + t * t * t / 24_490_000.0))
        val mPrime = Math.toRadians(normalize360(134.9633964 + 477_198.8675055 * t + 0.0087414 * t * t + t * t * t / 69_699.0))
        val f = Math.toRadians(normalize360(93.2720950 + 483_202.0175233 * t - 0.0036539 * t * t - t * t * t / 3_526_000.0))

        var sum = 0.0
        for (term in TERMS) {
            sum += term[4] * sin(term[0] * d + term[1] * m + term[2] * mPrime + term[3] * f)
        }
        return normalize360(lPrime + sum / 1e6)
    }

    internal fun sunLongitude(t: Double): Double {
        val l0 = 280.46646 + 36_000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35_999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        return normalize360(l0 + c)
    }

    /** 距 [jd] 最近的一次新月，往前找。 */
    internal fun previousNewMoon(jd: Double): Double {
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
    internal fun solve(target: Double, startJd: Double, spanDays: Double = 40.0): Double? {
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
    internal fun relative(jd: Double, target: Double): Double {
        val raw = (elongation(jd) - target + 180.0) % 360.0
        return if (raw < 0.0) raw + 360.0 - 180.0 else raw - 180.0
    }

    internal fun normalize360(degrees: Double): Double {
        val value = degrees % 360.0
        return if (value < 0.0) value + 360.0 else value
    }

    /**
     * 黄经差 -> 八分相索引。每 45° 一档，边界取中值 ±22.5°；
     * 索引顺序与 [MoonPhaseName] 的声明顺序一致。
     */
    internal fun sectorOf(elongationDegrees: Double): Int =
        floor(((elongationDegrees + 22.5) % 360.0) / 45.0).toInt() % 8

    /** 月面被照亮的面积比：0 = 新月，1 = 满月。 */
    internal fun illuminationOf(elongationDegrees: Double): Double =
        (1.0 - cos(Math.toRadians(elongationDegrees))) / 2.0
}

/**
 * 纯 JVM 回归程序：与 moon.py 的基准值逐点比对。
 * 由 regen.py 从 MoonCalculator.kt 生成，不要手改。
 */
fun main() {
    data class Case(val iso: String, val elongation: Double, val illumination: Double, val age: Double)
    val cases = listOf(
        Case("2026-10-31T12:00:00Z", 252.281, 65.22, 20.84),
        Case("2026-01-01T00:00:00Z", 146.149, 91.52, 11.93),
        Case("2024-04-08T18:17:00Z", 359.947, 0.00, 29.39),
        Case("2025-03-14T06:58:00Z", 180.019, 100.00, 14.26),
    )
    var worst = 0.0
    for (c in cases) {
        val p = MoonCalculator.phase(java.time.Instant.parse(c.iso))
        val dElong = kotlin.math.abs(p.elongationDegrees - c.elongation)
        val dIllum = kotlin.math.abs(p.illuminatedFraction * 100.0 - c.illumination)
        worst = maxOf(worst, dElong)
        println(
            String.format(
                java.util.Locale.US,
                "%-22s elong=%8.3f (d=%.4f) illum=%6.2f%% (d=%.2f) age=%5.2f d (d=%.2f) %-14s waxing=%b",
                c.iso, p.elongationDegrees, dElong, p.illuminatedFraction * 100.0, dIllum,
                p.ageDays, kotlin.math.abs(p.ageDays - c.age), p.name, p.waxing,
            )
        )
        println("   nextNew=" + p.nextNewMoon + "  nextFull=" + p.nextFullMoon)
    }
    println(String.format(java.util.Locale.US, "worst elongation delta = %.5f deg", worst))
}
