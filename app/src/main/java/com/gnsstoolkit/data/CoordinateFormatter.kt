package com.gnsstoolkit.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * 把 WGS84 经纬度渲染成用户选定的坐标格式与大地基准。
 *
 * 所有投影公式都作用在**目标基准**的经纬度上，因此先做一次基准换算；
 * OSGB / OSNI / CH1903 三种网格自带基准（[CoordinateFormat.pinnedDatum]），
 * 会覆盖设置里的选择——否则网格参考号没有意义。
 */
object CoordinateFormatter {

    private const val HUNDRED_KM = 100_000.0

    fun format(
        latitude: Double,
        longitude: Double,
        datum: GeodeticDatum,
        format: CoordinateFormat,
    ): CoordinateReading {
        // Maidenhead 是 WGS84 上的全球网格，与基准无关。
        if (format == CoordinateFormat.MAIDENHEAD) {
            return CoordinateReading(listOf(maidenhead(latitude, longitude)))
        }
        val target = format.pinnedDatum ?: datum
        val local = target.fromWgs84(latitude, longitude)
        return when (format) {
            CoordinateFormat.DMS -> degreePair(local, ::dms)
            CoordinateFormat.DDM -> degreePair(local, ::ddm)
            CoordinateFormat.DECIMAL -> degreePair(local, ::decimalDegrees)
            CoordinateFormat.UTM -> utmReading(latitude, longitude, target, local)
            CoordinateFormat.MGRS -> mgrsReading(latitude, longitude, target, local, spaced = false)
            CoordinateFormat.USNG -> mgrsReading(latitude, longitude, target, local, spaced = true)
            CoordinateFormat.OSGB_EN -> {
                val (e, n) = osgb(local)
                CoordinateReading(listOf("${whole(e)} ${whole(n)}"))
            }
            CoordinateFormat.OSGB_GRID -> CoordinateReading(listOf(osgbGridReference(local)))
            CoordinateFormat.OSNI_EN -> {
                val (e, n) = osni(local)
                CoordinateReading(listOf("${whole(e)} ${whole(n)}"))
            }
            CoordinateFormat.OSNI_GRID -> CoordinateReading(listOf(osniGridReference(local)))
            CoordinateFormat.CH1903 -> {
                val (y, x) = ch1903(local)
                CoordinateReading(listOf("${whole(y)} / ${whole(x)}"))
            }
            CoordinateFormat.MAIDENHEAD -> error("handled above")
        }
    }

    // ---------------------------------------------------------------- 角度

    private fun degreePair(
        point: GeoPoint,
        render: (Double, Boolean) -> String,
    ): CoordinateReading = CoordinateReading(
        listOf(
            render(point.latitude, true),
            render(point.longitude, false),
        ),
    )

    private fun hemisphere(value: Double, isLatitude: Boolean): String = when {
        isLatitude && value >= 0 -> "N"
        isLatitude -> "S"
        value >= 0 -> "E"
        else -> "W"
    }

    private fun dms(value: Double, isLatitude: Boolean): String {
        val abs = abs(value)
        var degrees = abs.toInt()
        val minutesFull = (abs - degrees) * 60.0
        var minutes = minutesFull.toInt()
        var seconds = (minutesFull - minutes) * 60.0
        // 进位：59.95" 显示成 60.0" 会很难看
        if (String.format(Locale.US, "%.1f", seconds).toDouble() >= 60.0) {
            seconds = 0.0
            minutes += 1
            if (minutes == 60) {
                minutes = 0
                degrees += 1
            }
        }
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, hemisphere(value, isLatitude))
    }

    private fun ddm(value: Double, isLatitude: Boolean): String {
        val abs = abs(value)
        var degrees = abs.toInt()
        var minutes = (abs - degrees) * 60.0
        if (String.format(Locale.US, "%.4f", minutes).toDouble() >= 60.0) {
            minutes = 0.0
            degrees += 1
        }
        return String.format(Locale.US, "%d°%07.4f'%s", degrees, minutes, hemisphere(value, isLatitude))
    }

    private fun decimalDegrees(value: Double, isLatitude: Boolean): String =
        String.format(Locale.US, "%.7f°%s", abs(value), hemisphere(value, isLatitude))

    // ----------------------------------------------------------------- UTM

    private fun utmZone(longitude: Double): Int {
        val zone = floor((longitude + 180.0) / 6.0).toInt() + 1
        return zone.coerceIn(1, 60)
    }

    private fun latitudeBand(latitude: Double): Char {
        val index = ((latitude + 80.0) / 8.0).toInt().coerceIn(0, 19)
        return "CDEFGHJKLMNPQRSTUVWX"[index]
    }

    /** UTM 正算，带号仍按 WGS84 经度确定。 */
    private fun utmCoordinates(
        latitude: Double,
        longitude: Double,
        target: GeodeticDatum,
        local: GeoPoint,
    ): Triple<Int, Double, Double> {
        val zone = utmZone(longitude)
        val centralMeridian = -183.0 + zone * 6.0
        val (easting, northing) = transverseMercator(
            datum = target,
            latitude = local.latitude,
            longitude = local.longitude,
            originLatitude = 0.0,
            originLongitude = centralMeridian,
            scaleFactor = 0.9996,
            falseEasting = 500_000.0,
            falseNorthing = if (latitude < 0) 10_000_000.0 else 0.0,
        )
        return Triple(zone, easting, northing)
    }

    private fun utmReading(
        latitude: Double,
        longitude: Double,
        target: GeodeticDatum,
        local: GeoPoint,
    ): CoordinateReading {
        val (zone, easting, northing) = utmCoordinates(latitude, longitude, target, local)
        return CoordinateReading(listOf("$zone${latitudeBand(latitude)} ${whole(easting)} ${whole(northing)}"))
    }

    private val eastingLetters = listOf("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")
    private val northingLetters = listOf("ABCDEFGHJKLMNPQRSTUV", "FGHJKLMNPQRSTUVABCDE")

    private fun mgrsReading(
        latitude: Double,
        longitude: Double,
        target: GeodeticDatum,
        local: GeoPoint,
        spaced: Boolean,
    ): CoordinateReading {
        val (zone, easting, northing) = utmCoordinates(latitude, longitude, target, local)
        val set = (zone - 1) % 6
        // 100 km 列号从 1 起（UTM 带内 easting 166–834 km），字母表下标要减一
        val column = (floor(easting / HUNDRED_KM).toInt() - 1).coerceIn(0, 7)
        val row = (floor(northing / HUNDRED_KM).toInt() % 20 + 20) % 20
        val letters = eastingLetters[set % 3].substring(column, column + 1) +
            northingLetters[set % 2].substring(row, row + 1)
        val east = truncated(easting)
        val north = truncated(northing)
        val body = if (spaced) "$letters $east $north" else "$letters$east$north"
        return CoordinateReading(listOf("$zone${latitudeBand(latitude)} $body"))
    }

    // ---------------------------------------------------------------- OSGB

    private val osgbLetters = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

    /** [osgbLetters] 已经去掉了 I，索引可以直接用。 */
    private fun osgbLetter(index: Int): Char =
        osgbLetters[index.coerceIn(0, osgbLetters.lastIndex)]

    private fun osgb(point: GeoPoint): Pair<Double, Double> = transverseMercator(
        datum = GeodeticDatum.OSGB36_MEAN,
        latitude = point.latitude,
        longitude = point.longitude,
        originLatitude = 49.0,
        originLongitude = -2.0,
        scaleFactor = 0.9996012717,
        falseEasting = 400_000.0,
        falseNorthing = -100_000.0,
    )

    private fun osgbGridReference(point: GeoPoint): String {
        val (easting, northing) = osgb(point)
        val e100 = floor(easting / HUNDRED_KM).toInt()
        val n100 = floor(northing / HUNDRED_KM).toInt()
        val n = 19 - n100
        val first = osgbLetter(n - (n % 5) + (e100 + 10) / 5)
        val second = osgbLetter((n * 5) % 25 + (e100 % 5))
        val east = truncated(easting)
        val north = truncated(northing)
        return "$first$second $east $north"
    }

    // ---------------------------------------------------------------- OSNI

    private fun osni(point: GeoPoint): Pair<Double, Double> = transverseMercator(
        datum = GeodeticDatum.IRELAND_1965,
        latitude = point.latitude,
        longitude = point.longitude,
        originLatitude = 53.5,
        originLongitude = -8.0,
        scaleFactor = 1.000035,
        falseEasting = 200_000.0,
        falseNorthing = 250_000.0,
    )

    /** 爱尔兰网格用单个字母标记 100 km 方格，自西南角按 5 列排布。 */
    private fun osniGridReference(point: GeoPoint): String {
        val (easting, northing) = osni(point)
        val e100 = floor(easting / HUNDRED_KM).toInt()
        val n100 = floor(northing / HUNDRED_KM).toInt()
        val index = ((4 - n100) * 5 + e100).coerceIn(0, osgbLetters.lastIndex)
        val letter = osgbLetters[index]
        val east = truncated(easting)
        val north = truncated(northing)
        return "$letter $east $north"
    }

    // --------------------------------------------------------------- CH1903

    /** swisstopo 公布的近似公式，瑞士境内误差约 1 m。 */
    private fun ch1903(point: GeoPoint): Pair<Double, Double> {
        val phi = (point.latitude * 3600.0 - 169_028.66) / 10_000.0
        val lam = (point.longitude * 3600.0 - 26_782.5) / 10_000.0
        val y = 600_072.37 + 211_455.93 * lam - 10_938.51 * lam * phi -
            0.36 * lam * phi.pow(2) - 44.54 * lam.pow(3)
        val x = 200_147.07 + 308_807.95 * phi + 3_745.25 * lam.pow(2) +
            76.63 * phi.pow(2) - 194.56 * lam.pow(2) * phi + 119.79 * phi.pow(3)
        return y to x
    }

    // ----------------------------------------------------------- Maidenhead

    private fun maidenhead(latitude: Double, longitude: Double): String {
        val lon = longitude + 180.0
        val lat = latitude + 90.0
        val builder = StringBuilder(6)
        builder.append('A' + (lon / 20.0).toInt())
        builder.append('A' + (lat / 10.0).toInt())
        builder.append((lon % 20.0 / 2.0).toInt())
        builder.append((lat % 10.0).toInt())
        builder.append('a' + ((lon % 2.0) * 12.0).toInt())
        builder.append('a' + ((lat % 1.0) * 24.0).toInt())
        return builder.toString()
    }

    // ------------------------------------------------------------ 通用工具

    /** 横轴墨卡托正算（Snyder 级数，带内精度优于 1 mm）。 */
    private fun transverseMercator(
        datum: GeodeticDatum,
        latitude: Double,
        longitude: Double,
        originLatitude: Double,
        originLongitude: Double,
        scaleFactor: Double,
        falseEasting: Double,
        falseNorthing: Double,
    ): Pair<Double, Double> {
        val a = datum.semiMajor
        val e2 = datum.eccentricitySquared
        val ep2 = e2 / (1.0 - e2)
        val phi = Math.toRadians(latitude)
        val lam = Math.toRadians(longitude)
        val phi0 = Math.toRadians(originLatitude)
        val lam0 = Math.toRadians(originLongitude)

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)
        val n = a / kotlin.math.sqrt(1.0 - e2 * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = ep2 * cosPhi * cosPhi
        val delta = (lam - lam0) * cosPhi

        val arc = meridianArc(datum, phi) - meridianArc(datum, phi0)

        val easting = scaleFactor * n * (
            delta +
                (1.0 - t + c) * delta.pow(3) / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) * delta.pow(5) / 120.0
            ) + falseEasting

        val northing = scaleFactor * (
            arc + n * tanPhi * (
                delta.pow(2) / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * delta.pow(4) / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ep2) * delta.pow(6) / 720.0
                )
            ) + falseNorthing

        return easting to northing
    }

    /** 子午线弧长，取到 e⁶ 项。 */
    private fun meridianArc(datum: GeodeticDatum, phi: Double): Double {
        val a = datum.semiMajor
        val e2 = datum.eccentricitySquared
        val e4 = e2 * e2
        val e6 = e4 * e2
        return a * (
            (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi -
                (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * phi) +
                (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * phi) -
                (35.0 * e6 / 3072.0) * sin(6.0 * phi)
            )
    }

    private fun whole(value: Double): String = String.format(Locale.US, "%.0f", value)

    /** 网格参考号取 100 km 方格内的余数，按惯例截断到米，不做四舍五入。 */
    private fun truncated(value: Double): String =
        ((value % HUNDRED_KM).toInt()).toString().padStart(5, '0')
}
