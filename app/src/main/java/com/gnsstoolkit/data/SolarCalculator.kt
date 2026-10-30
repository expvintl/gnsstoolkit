package com.gnsstoolkit.data

import com.gnsstoolkit.R

import androidx.annotation.StringRes

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** 一段时间区间，起止都在同一天内。 */
data class TimeRange(val start: LocalTime, val end: LocalTime)

/** 按太阳高度角划分的光照时段，顺序即 UI 展示顺序。 */
enum class LightPeriod(@param:StringRes val labelRes: Int) {
    GOLDEN(R.string.light_golden),
    CIVIL(R.string.light_civil),
    NAUTICAL(R.string.light_nautical),
    ASTRONOMICAL(R.string.light_astronomical),
    BLUE(R.string.light_blue),
}

/**
 * 一个光照时段当日落在带内的时间区间，按时间升序，最多两段（上午一段、傍晚一段）。
 *
 * 平常日子是两段；极夜时太阳整天都到不了地平线，整段就只剩一个区间；白夜/午夜太阳时
 * 带内时间挤在午夜前后，同样只有一个跨零点的区间。该时段当日根本不出现时为空列表。
 */
data class LightBand(
    val period: LightPeriod,
    val intervals: List<TimeRange>,
)

data class SolarEvents(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val solarNoon: LocalTime?,
    val dayLengthMinutes: Int?,
    /** 清晨蓝调时刻（太阳高度 −6° → −4°）。高纬整日不经过这一区间时为 null。 */
    val morningBlueHour: TimeRange? = null,
    /** 黄昏蓝调时刻（太阳高度 −4° → −6°）。 */
    val eveningBlueHour: TimeRange? = null,
    /** 黄金、民用、航海、天文、蓝调五段的上午/傍晚区间。 */
    val lightBands: List<LightBand> = emptyList(),
)

/**
 * NOAA 太阳位置算法（简化版），计算本地日出、日落、正午与蓝调时刻。
 * 该天顶角当日不可达（极昼极夜、高纬无蓝调）时对应字段为 null。
 *
 * 赤纬与均时差按事件**当刻**反代迭代三次求解。只用正午值的话，高纬地区误差可以
 * 到十几分钟——太阳在地平线附近走得极慢，赤纬差 0.1° 就能让时刻差出好几分钟。
 */
object SolarCalculator {

    private const val SUN_ZENITH = 90.833

    /** 黄金时刻上界：太阳高度 +6°。 */
    private const val GOLDEN_ZENITH = 84.0

    /** 民用晨昏：太阳高度 −6°。蓝调时刻的下界与它重合。 */
    private const val CIVIL_ZENITH = 96.0

    /** 航海晨昏：太阳高度 −12°。 */
    private const val NAUTICAL_ZENITH = 102.0

    /** 天文晨昏：太阳高度 −18°。 */
    private const val ASTRONOMICAL_ZENITH = 108.0

    /**
     * 蓝调时刻取太阳高度 −4° ~ −6°（天顶角 94° / 96°，几何值，不含折射与视半径修正）。
     * 摄影上这一段天空呈饱和的深蓝：之前是金色时刻，之后进入黑暗。
     */
    private const val BLUE_HOUR_UPPER_ZENITH = 94.0

    private const val ITERATIONS = 3

    fun events(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SolarEvents {
        val latRad = Math.toRadians(latitude)

        fun rise(zenith: Double): Double? =
            crossingMinutes(zenith, date, latRad, longitude, rising = true)

        fun set(zenith: Double): Double? =
            crossingMinutes(zenith, date, latRad, longitude, rising = false)

        fun range(from: Double?, to: Double?): TimeRange? =
            if (from == null || to == null) {
                null
            } else {
                TimeRange(toLocalTime(date, from, zone), toLocalTime(date, to, zone))
            }

        /**
         * 太阳高度介于 [lowDegrees, highDegrees] 之间的时间区间。
         *
         * 正常日子是「升进该带 → 升出该带」与「降进该带 → 降出该带」两段。高纬会退化：
         * 极夜时太阳最高也到不了 upper 界，只能给出「升到 low 界 → 又降回 low 界」这一整段；
         * 白夜/午夜太阳时太阳从不低于 lower 界，带内时间挤在午夜前后，是一段跨零点的区间。
         */
        fun band(lowDegrees: Double, highDegrees: Double): List<TimeRange> {
            val lowZenith = 90.0 - lowDegrees
            val highZenith = 90.0 - highDegrees
            val riseLow = rise(lowZenith)
            val riseHigh = rise(highZenith)
            val setHigh = set(highZenith)
            val setLow = set(lowZenith)

            val intervals = mutableListOf<TimeRange>()
            range(riseLow, riseHigh)?.let(intervals::add)
            range(setHigh, setLow)?.let(intervals::add)
            if (intervals.isEmpty()) {
                range(riseLow, setLow)?.let(intervals::add)
                range(setHigh, riseHigh)?.let(intervals::add)
            }
            return intervals
        }

        val sunrise = rise(SUN_ZENITH)
        val sunset = set(SUN_ZENITH)
        val bands = listOf(
            LightBand(LightPeriod.GOLDEN, band(-0.833, 6.0)),
            LightBand(LightPeriod.CIVIL, band(-6.0, -0.833)),
            LightBand(LightPeriod.NAUTICAL, band(-12.0, -6.0)),
            LightBand(LightPeriod.ASTRONOMICAL, band(-18.0, -12.0)),
            LightBand(LightPeriod.BLUE, band(-6.0, -4.0)),
        )
        val blue = bands.first { it.period == LightPeriod.BLUE }

        return SolarEvents(
            sunrise = sunrise?.let { toLocalTime(date, it, zone) },
            sunset = sunset?.let { toLocalTime(date, it, zone) },
            solarNoon = toLocalTime(date, solarNoonMinutes(date, longitude), zone),
            dayLengthMinutes = if (sunrise != null && sunset != null) {
                (sunset - sunrise).roundToLong().toInt()
            } else {
                null
            },
            morningBlueHour = blue.intervals.firstOrNull(),
            eveningBlueHour = blue.intervals.getOrNull(1),
            lightBands = bands,
        )
    }

    /** 太阳正午（当地 UTC 分钟数）。 */
    private fun solarNoonMinutes(date: LocalDate, longitude: Double): Double =
        720.0 - 4.0 * longitude - solarParams(720.0, date).equationOfTimeMinutes

    /**
     * 求太阳升到（或降到）指定天顶角的 UTC 分钟数。
     *
     * 先用正午参数取初值，再把「事件时刻」代回去重算赤纬与均时差，迭代三次。
     * 某次迭代发现该天顶角不可达时，返回上一轮已求出的值（在极昼边缘那天，
     * 首轮可达、精修后不可达是真实情形，不该把事件吞掉）。
     */
    private fun crossingMinutes(
        zenith: Double,
        date: LocalDate,
        latitudeRad: Double,
        longitude: Double,
        rising: Boolean,
    ): Double? {
        var referenceMinutes = 720.0
        var result: Double? = null
        repeat(ITERATIONS) {
            val params = solarParams(referenceMinutes, date)
            val hourAngle = hourAngleDegrees(zenith, latitudeRad, params.declinationRadians)
                ?: return result
            val noon = 720.0 - 4.0 * longitude - params.equationOfTimeMinutes
            val minutes = noon + if (rising) -4.0 * hourAngle else 4.0 * hourAngle
            result = minutes
            referenceMinutes = minutes
        }
        return result
    }

    /** 太阳处于某天顶角时的时角（度）。该天顶角整日不可达时返回 null。 */
    private fun hourAngleDegrees(zenith: Double, latRad: Double, declination: Double): Double? {
        val cosHourAngle = (cos(Math.toRadians(zenith)) - sin(latRad) * sin(declination)) /
            (cos(latRad) * cos(declination))
        if (abs(cosHourAngle) > 1.0) return null
        return Math.toDegrees(acos(cosHourAngle))
    }

    private data class SolarParams(
        val declinationRadians: Double,
        val equationOfTimeMinutes: Double,
    )

    /**
     * Meeus《天文算法》第 25 章的太阳位置（低精度式，误差约 0.01°、均时差 0.1 分钟）。
     *
     * 早前用的是 NOAA 那张三项三角级数，在分点附近赤纬会有 0.4° 的系统偏差——高纬地区
     * 太阳贴着地平线走，0.4° 就能让日出时刻差出十分钟以上（和 astral 对表实测）。
     */
    private fun solarParams(utcMinutes: Double, date: LocalDate): SolarParams {
        val instant = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plusSeconds((utcMinutes * 60.0).roundToLong())
        val julianDay = 2440587.5 + instant.toEpochMilli() / 86_400_000.0
        val t = (julianDay - 2451545.0) / 36525.0

        val meanLongitude = Math.toRadians(
            (280.46646 + 36000.76983 * t + 0.0003032 * t * t) % 360.0
        )
        val meanAnomaly = Math.toRadians(
            (357.52911 + 35999.05029 * t - 0.0001537 * t * t) % 360.0
        )
        val equationOfCenter = Math.toRadians(
            (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(meanAnomaly) +
                (0.019993 - 0.000101 * t) * sin(2 * meanAnomaly) +
                0.000289 * sin(3 * meanAnomaly)
        )
        val omega = Math.toRadians((125.04 - 1934.136 * t) % 360.0)
        val apparentLongitude = meanLongitude + equationOfCenter -
            Math.toRadians(0.00569) - Math.toRadians(0.00478) * sin(omega)

        val secondsPerDegree = 3600.0
        val meanObliquity = 23.0 + 26.0 / 60.0 + 21.448 / secondsPerDegree -
            (46.8150 * t + 0.00059 * t * t - 0.001813 * t * t * t) / secondsPerDegree
        val obliquity = Math.toRadians(meanObliquity) + Math.toRadians(0.00256) * cos(omega)

        val declination = asin(sin(obliquity) * sin(apparentLongitude))

        // 均时差（Meeus 28.3）
        val eccentricity = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t
        val y = tan(obliquity / 2.0).let { it * it }
        val y2 = y * y
        val equationOfTime = Math.toDegrees(
            y * sin(2 * meanLongitude) -
                2 * eccentricity * sin(meanAnomaly) +
                4 * eccentricity * y * sin(meanAnomaly) * cos(2 * meanLongitude) -
                0.5 * y2 * sin(4 * meanLongitude) -
                1.25 * eccentricity * eccentricity * sin(2 * meanAnomaly)
        ) * 4.0

        return SolarParams(declination, equationOfTime)
    }

    /** 把「当天 UTC 分钟数」换算成给定时区下的本地时间。 */
    private fun toLocalTime(date: LocalDate, utcMinutes: Double, zone: ZoneId): LocalTime {
        val noonUtc = date.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant()
        return noonUtc.plusSeconds(((utcMinutes - 720.0) * 60.0).roundToLong())
            .atZone(zone)
            .toLocalTime()
    }
}
