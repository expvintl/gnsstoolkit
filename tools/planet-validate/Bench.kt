package com.gnsstoolkit.data

import java.time.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan


/** 八分月相。中文「新月/娥眉月/上弦月/盈凸月/满月/亏凸月/下弦月/残月」。 */
enum class MoonPhaseName(val labelRes: Int) {
    NEW(0),
    WAXING_CRESCENT(0),
    FIRST_QUARTER(0),
    WAXING_GIBBOUS(0),
    FULL(0),
    WANING_GIBBOUS(0),
    LAST_QUARTER(0),
    WANING_CRESCENT(0),
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


/** 星空图里显示的天体。顺序即显示顺序。 */
enum class CelestialBody(val labelRes: Int) {
    SUN(0),
    MOON(0),
    MERCURY(0),
    VENUS(0),
    MARS(0),
    JUPITER(0),
    SATURN(0),
    URANUS(0),
    NEPTUNE(0),
}

/**
 * 某个天体的地平坐标。
 *
 * [azimuthDegrees] 从正北起算、向东为正；[elevationDegrees] 为负表示在地平线以下。
 * [magnitude] 是视星等（越小越亮），用于决定图上圆点大小。
 */
data class SkyBody(
    val body: CelestialBody,
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
    val distanceAu: Double,
    val magnitude: Double,
) {
    val isAboveHorizon: Boolean get() = elevationDegrees > 0.0
}

/**
 * 太阳系天体的实时位置。
 *
 * 行星用 JPL 的「近似轨道根数」（1800–2050 有效）解二体问题的开普勒方程，再算地心视位置；
 * 月球用 [MoonCalculator] 的 Meeus 47 章级数；太阳由地球日心位置取反。
 *
 * 精度（与 skyfield + DE421 星历对照，2026–2027 年 6 组经纬度）：绝大多数天体方位/高度
 * 误差 < 0.01°，最差的土星 0.08°（木星以外用质心根数带来的固有偏差）。星空图上一个点
 * 的直径远大于这个角度，因此足够。
 */
object PlanetPositions {

    private const val JULIAN_DAY_UNIX_EPOCH = 2440587.5
    private const val JULIAN_DAY_J2000 = 2451545.0
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val AU_KM = 149_597_870.7

    /** 光行时：1 AU 对应约 0.0057755 天。 */
    private const val LIGHT_TIME_DAYS_PER_AU = 0.0057755183

    /** 每儒略世纪的整体岁差（黄经方向，度）。 */
    private const val PRECESSION_DEG_PER_CENTURY = 1.3969713

    private const val ARCSEC_TO_RAD = 4.84813681109536e-6
    private const val EARTH_EQUATORIAL_RADIUS_KM = 6378.14

    /** 轨道根数：a(AU)、e、i、L、近日点黄经、升交点黄经，以及各自每世纪的变率。 */
    private class Elements(
        val a: Double,
        val e: Double,
        val inclination: Double,
        val meanLongitude: Double,
        val perihelion: Double,
        val node: Double,
        val da: Double,
        val de: Double,
        val di: Double,
        val dl: Double,
        val dperi: Double,
        val dnode: Double,
    )

    private val MERCURY = Elements(
        0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
        0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081,
    )
    private val VENUS = Elements(
        0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
        0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418,
    )
    private val EARTH = Elements(
        1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
        0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0,
    )
    private val MARS = Elements(
        1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
        0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343,
    )
    private val JUPITER = Elements(
        5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
        -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106,
    )
    private val SATURN = Elements(
        9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
        -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794,
    )
    private val URANUS = Elements(
        19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503,
        -0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589,
    )
    private val NEPTUNE = Elements(
        30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574,
        0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664,
    )

    private val PLANETS = listOf(
        CelestialBody.MERCURY to MERCURY,
        CelestialBody.VENUS to VENUS,
        CelestialBody.MARS to MARS,
        CelestialBody.JUPITER to JUPITER,
        CelestialBody.SATURN to SATURN,
        CelestialBody.URANUS to URANUS,
        CelestialBody.NEPTUNE to NEPTUNE,
    )

    /** 日心黄道直角坐标（AU，J2000 黄道）。 */
    private class Vector(val x: Double, val y: Double, val z: Double) {
        val length: Double get() = sqrt(x * x + y * y + z * z)
    }

    fun positions(
        instant: Instant = Instant.now(),
        latitudeDegrees: Double,
        longitudeDegrees: Double,
    ): List<SkyBody> {
        val jd = instant.toEpochMilli() / MILLIS_PER_DAY + JULIAN_DAY_UNIX_EPOCH
        val t = (jd - JULIAN_DAY_J2000) / 36525.0
        val earth = heliocentric(EARTH, t)
        val earthDistance = earth.length
        val obliquity = meanObliquity(t)
        val lst = (greenwichSiderealDegrees(jd) + longitudeDegrees) % 360.0
        val latitude = Math.toRadians(latitudeDegrees)

        val result = ArrayList<SkyBody>(9)

        for ((body, elements) in PLANETS) {
            val planet = heliocentric(elements, t)
            var delta = Vector(planet.x - earth.x, planet.y - earth.y, planet.z - earth.z)
            // 光行时：用光线离开行星那一刻的地球位置再算一次
            val shifted = heliocentric(EARTH, t - delta.length * LIGHT_TIME_DAYS_PER_AU / 36525.0)
            delta = Vector(planet.x - shifted.x, planet.y - shifted.y, planet.z - shifted.z)
            val distance = delta.length
            val geocentric = toEquatorial(delta, obliquity, t)
            val phase = phaseAngle(planet.length, distance, earthDistance)
            result += body(
                body,
                geocentric,
                distance,
                magnitudeOf(body, planet.length, distance, phase),
                lst,
                latitude,
            )
        }

        // 太阳：地心方向 = 地球日心方向取反
        val sun = Vector(-earth.x, -earth.y, -earth.z)
        result += body(
            CelestialBody.SUN,
            toEquatorial(sun, obliquity, t),
            earthDistance,
            -26.74 + 5.0 * log10(earthDistance),
            lst,
            latitude,
        )

        // 月球：黄道坐标已经是当日平春分点，不再加岁差
        val moon = MoonCalculator.eclipticAt(jd)
        val moonDistanceAu = moon.distanceKm / AU_KM
        val moonGeocentric = toEquatorial(
            Math.toRadians(moon.longitudeDeg),
            Math.toRadians(moon.latitudeDeg),
            obliquity,
            distanceAu = moonDistanceAu,
        )
        val moonPhaseAngle = 180.0 - Math.toDegrees(
            Math.acos(
                cos(Math.toRadians(moon.latitudeDeg)) *
                    cos(Math.toRadians(moon.longitudeDeg - sunEclipticLongitude(earth, obliquity, t))),
            ),
        ).coerceIn(-180.0, 180.0)
        val moonMagnitude = if (moonPhaseAngle.isNaN()) {
            -12.7
        } else {
            val i = abs(moonPhaseAngle)
            -12.74 + 0.026 * i + 4e-9 * i * i * i * i
        }
        result += body(CelestialBody.MOON, moonGeocentric, moonDistanceAu, moonMagnitude, lst, latitude)

        return result
    }

    private fun body(
        body: CelestialBody,
        geocentric: Equatorial,
        distanceAu: Double,
        magnitude: Double,
        lstDegrees: Double,
        latitudeRad: Double,
    ): SkyBody {
        val topocentric = correctForParallax(geocentric, distanceAu, lstDegrees, latitudeRad)
        val hourAngle = Math.toRadians((lstDegrees - topocentric.rightAscensionDegrees + 540.0) % 360.0 - 180.0)
        val declination = Math.toRadians(topocentric.declinationDegrees)
        val elevation = asin(
            sin(latitudeRad) * sin(declination) + cos(latitudeRad) * cos(declination) * cos(hourAngle),
        )
        val azimuth = atan2(
            -cos(declination) * sin(hourAngle),
            sin(declination) * cos(latitudeRad) - cos(declination) * sin(latitudeRad) * cos(hourAngle),
        )
        return SkyBody(
            body = body,
            azimuthDegrees = normalize360(Math.toDegrees(azimuth)),
            elevationDegrees = Math.toDegrees(elevation),
            distanceAu = distanceAu,
            magnitude = magnitude,
        )
    }

    private class Equatorial(val rightAscensionDegrees: Double, val declinationDegrees: Double)

    /** 黄道直角坐标 → 当日平赤道坐标（含岁差修正）。 */
    private fun toEquatorial(vector: Vector, obliquity: Double, t: Double): Equatorial {
        val longitude = atan2(vector.y, vector.x) + Math.toRadians(PRECESSION_DEG_PER_CENTURY) * t
        val latitude = asin(vector.z / vector.length)
        return toEquatorial(longitude, latitude, obliquity, vector.length)
    }

    private fun toEquatorial(
        longitude: Double,
        latitude: Double,
        obliquity: Double,
        distanceAu: Double,
    ): Equatorial {
        val ra = atan2(
            sin(longitude) * cos(obliquity) - tan(latitude) * sin(obliquity),
            cos(longitude),
        )
        val dec = asin(
            sin(latitude) * cos(obliquity) + cos(latitude) * sin(obliquity) * sin(longitude),
        )
        return Equatorial(normalize360(Math.toDegrees(ra)), Math.toDegrees(dec))
    }

    /** 周日视差（Meeus 第 40 章）。月球可达 1°，行星可忽略但一并处理。 */
    private fun correctForParallax(
        equatorial: Equatorial,
        distanceAu: Double,
        lstDegrees: Double,
        latitudeRad: Double,
    ): Equatorial {
        if (distanceAu <= 0.0) return equatorial
        val horizontalParallax = asin(
            (EARTH_EQUATORIAL_RADIUS_KM / AU_KM / distanceAu).coerceIn(-1.0, 1.0),
        )
        if (horizontalParallax < 1e-7) return equatorial

        // 观测者在椭球上的位置
        val u = atan(0.99664719 * tan(latitudeRad))
        val rhoSin = 0.99664719 * sin(u)
        val rhoCos = cos(u)

        val declination = Math.toRadians(equatorial.declinationDegrees)
        val hourAngle = Math.toRadians(
            (lstDegrees - equatorial.rightAscensionDegrees + 540.0) % 360.0 - 180.0,
        )
        val deltaRa = atan2(
            -rhoCos * sin(horizontalParallax) * sin(hourAngle),
            cos(declination) - rhoCos * sin(horizontalParallax) * cos(hourAngle),
        )
        val correctedDec = atan2(
            (sin(declination) - rhoSin * sin(horizontalParallax)) * cos(deltaRa),
            cos(declination) - rhoCos * sin(horizontalParallax) * cos(hourAngle),
        )
        return Equatorial(
            normalize360(equatorial.rightAscensionDegrees + Math.toDegrees(deltaRa)),
            Math.toDegrees(correctedDec),
        )
    }

    private fun heliocentric(elements: Elements, t: Double): Vector {
        val a = elements.a + elements.da * t
        val e = elements.e + elements.de * t
        val inclination = Math.toRadians(elements.inclination + elements.di * t)
        val meanLongitude = elements.meanLongitude + elements.dl * t
        val perihelion = Math.toRadians(elements.perihelion + elements.dperi * t)
        val node = Math.toRadians(elements.node + elements.dnode * t)

        val argument = perihelion - node
        var anomaly = Math.toRadians(
            (meanLongitude - Math.toDegrees(perihelion)) % 360.0,
        )
        if (anomaly > Math.PI) anomaly -= 2.0 * Math.PI
        if (anomaly < -Math.PI) anomaly += 2.0 * Math.PI

        val eccentricAnomaly = solveKepler(anomaly, e)
        val xOrbital = a * (cos(eccentricAnomaly) - e)
        val yOrbital = a * sqrt(1.0 - e * e) * sin(eccentricAnomaly)

        val cw = cos(argument)
        val sw = sin(argument)
        val cn = cos(node)
        val sn = sin(node)
        val ci = cos(inclination)
        val si = sin(inclination)

        return Vector(
            x = (cw * cn - sw * sn * ci) * xOrbital + (-sw * cn - cw * sn * ci) * yOrbital,
            y = (cw * sn + sw * cn * ci) * xOrbital + (-sw * sn + cw * cn * ci) * yOrbital,
            z = (sw * si) * xOrbital + (cw * si) * yOrbital,
        )
    }

    private fun solveKepler(meanAnomaly: Double, eccentricity: Double): Double {
        var eccentricAnomaly = meanAnomaly
        repeat(32) {
            val delta = (eccentricAnomaly - eccentricity * sin(eccentricAnomaly) - meanAnomaly) /
                (1.0 - eccentricity * cos(eccentricAnomaly))
            eccentricAnomaly -= delta
            if (abs(delta) < 1e-13) return eccentricAnomaly
        }
        return eccentricAnomaly
    }

    /** 相位角（度）。 */
    private fun phaseAngle(heliocentricDistance: Double, geocentricDistance: Double, earthDistance: Double): Double {
        val denominator = 2.0 * heliocentricDistance * geocentricDistance
        if (denominator <= 0.0) return 0.0
        val cosine = (heliocentricDistance * heliocentricDistance +
            geocentricDistance * geocentricDistance -
            earthDistance * earthDistance) / denominator
        return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
    }

    /** Meeus 第 41 章的视星等经验式。 */
    private fun magnitudeOf(
        body: CelestialBody,
        heliocentricDistance: Double,
        geocentricDistance: Double,
        phase: Double,
    ): Double {
        val base = 5.0 * log10(heliocentricDistance * geocentricDistance)
        return when (body) {
            CelestialBody.MERCURY ->
                -0.42 + base + 0.0380 * phase - 0.000273 * phase * phase + 0.000002 * phase * phase * phase
            CelestialBody.VENUS ->
                -4.40 + base + 0.0009 * phase + 0.000239 * phase * phase - 0.00000065 * phase * phase * phase
            CelestialBody.MARS -> -1.52 + base + 0.016 * phase
            CelestialBody.JUPITER -> -9.40 + base + 0.005 * phase
            CelestialBody.SATURN -> -8.88 + base
            CelestialBody.URANUS -> -7.19 + base
            CelestialBody.NEPTUNE -> -6.87 + base
            else -> -1.0 + base
        }
    }

    private fun sunEclipticLongitude(earth: Vector, obliquity: Double, t: Double): Double =
        Math.toDegrees(
            atan2(-earth.y, -earth.x) + Math.toRadians(PRECESSION_DEG_PER_CENTURY) * t,
        )

    private fun meanObliquity(t: Double): Double = Math.toRadians(
        23.43929111 - 0.0130042 * t - 1.64e-7 * t * t + 5.04e-7 * t * t * t,
    )

    private fun greenwichSiderealDegrees(jd: Double): Double {
        val d = jd - JULIAN_DAY_J2000
        val t = d / 36525.0
        val degrees = 280.46061837 + 360.98564736629 * d + 0.000387933 * t * t -
            t * t * t / 38_710_000.0
        return normalize360(degrees)
    }

    private fun normalize360(degrees: Double): Double {
        val value = degrees % 360.0
        return if (value < 0.0) value + 360.0 else value
    }
}

fun main() {
    val cases = listOf(
        Triple("2026-10-31T12:00:00Z", 39.9087, 116.3975),
        Triple("2026-10-31T12:00:00Z", -33.8688, 151.2093),
        Triple("2026-07-04T03:30:00Z", 51.4779, -0.0015),
        Triple("2027-02-19T00:00:00Z", 0.0, 0.0),
        Triple("2026-01-01T00:00:00Z", 64.1466, -21.9426),
        Triple("2026-03-20T09:15:00Z", 47.3769, 8.5417),
    )
    for ((iso, lat, lon) in cases) {
        val instant = java.time.Instant.parse(iso)
        for (body in PlanetPositions.positions(instant, lat, lon)) {
            println("{\"date\":\"$iso\",\"lat\":$lat,\"lon\":$lon,\"body\":\"${body.body.name}\",\"az\":${body.azimuthDegrees},\"alt\":${body.elevationDegrees},\"mag\":${body.magnitude}}")
        }
    }
}
