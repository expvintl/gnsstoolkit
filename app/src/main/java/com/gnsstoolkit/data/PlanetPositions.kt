package com.gnsstoolkit.data

import androidx.annotation.StringRes
import com.gnsstoolkit.R
import java.time.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 星空图里显示的天体。顺序即显示顺序。 */
enum class CelestialBody(@param:StringRes val labelRes: Int) {
    SUN(R.string.body_sun),
    MOON(R.string.body_moon),
    MERCURY(R.string.body_mercury),
    VENUS(R.string.body_venus),
    MARS(R.string.body_mars),
    JUPITER(R.string.body_jupiter),
    SATURN(R.string.body_saturn),
    URANUS(R.string.body_uranus),
    NEPTUNE(R.string.body_neptune),
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
