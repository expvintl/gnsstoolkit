package com.gnsstoolkit.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 大地基准：参考椭球 + 到 WGS84 的 Helmert 七参数。
 *
 * 参数取自 EPSG 数据集（PROJ 的 `proj.db`，`helmert_transformation` 表），
 * 旋转按 EPSG 9606 *position vector* 约定，单位为角秒，尺度单位为 ppm。
 * [label] 是基准的惯例写法，与 GPS Test 等工具保持一致，不做翻译。
 */
enum class GeodeticDatum(
    val label: String,
    val semiMajor: Double,
    val inverseFlattening: Double,
    private val tx: Double,
    private val ty: Double,
    private val tz: Double,
    private val rx: Double,
    private val ry: Double,
    private val rz: Double,
    private val ppm: Double,
) {
    /** EPSG:6326，本身就是 WGS84，无需转换。 */
    WGS84("WGS84", 6_378_137.0, 298.257223563, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),

    /** EPSG:1172 "NAD27 to WGS 84 (3)"，北美大陆平均。 */
    NAD27_CONUS(
        "NAD27 - Conus", 6_378_206.4, 294.978698214,
        -10.0, 158.0, 187.0, 0.0, 0.0, 0.0, 0.0,
    ),

    /** EPSG:1188 "NAD83 to WGS 84 (1)"，三参数为零（差异小于 2 m）。 */
    NAD83_CONUS(
        "NAD83 - Conus", 6_378_137.0, 298.257222101,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
    ),

    /** EPSG:1314 "OSGB36 to WGS 84 (6)"，英国国家格网官方参数。 */
    OSGB36_MEAN(
        "OSGB36 - Mean", 6_377_563.396, 299.3249646,
        446.448, -125.157, 542.060, 0.15, 0.247, 0.842, -20.489,
    ),

    /** EPSG:1133 "ED50 to WGS 84 (1)"，欧洲平均。 */
    ED50_MEAN(
        "ED50 - Mean", 6_378_388.0, 297.0,
        -87.0, -98.0, -121.0, 0.0, 0.0, 0.0, 0.0,
    ),

    /** EPSG:1633 "ED50 to WGS 84 (28)"，西班牙与葡萄牙。 */
    ED50_SPAIN(
        "ED50 - Spain", 6_378_388.0, 297.0,
        -131.0, -100.3, -163.4, -1.244, -0.02, -1.144, 9.39,
    ),

    /** EPSG:15980 "AGD66 to WGS 84 (18)"，澳大利亚。 */
    AGD66(
        "AGD66", 6_378_160.0, 298.25,
        -117.808, -51.536, 137.784, -0.303, -0.446, -0.234, -0.29,
    ),

    /** EPSG:1109 "AGD84 to WGS 84 (1)"，澳大利亚。 */
    AGD84(
        "AGD84", 6_378_160.0, 298.25,
        -134.0, -48.0, 149.0, 0.0, 0.0, 0.0, 0.0,
    ),

    /** EPSG:1864 "SAD69 to WGS 84 (1)"，南美平均。 */
    SAD69_MEAN(
        "SAD69 - Mean", 6_378_160.0, 298.25,
        -57.0, 1.0, -41.0, 0.0, 0.0, 0.0, 0.0,
    ),

    /** EPSG:1100 "Adindan to WGS 84 (1)"，埃塞俄比亚、马里、塞内加尔、苏丹。 */
    ADINDAN_MEAN(
        "Adindan - Mean", 6_378_249.145, 293.465,
        -166.0, -15.0, 204.0, 0.0, 0.0, 0.0, 0.0,
    ),

    /** 爱尔兰 1965（EPSG:6299），OSNI 网格与爱尔兰横轴墨卡托的基准。 */
    IRELAND_1965(
        "Ireland 1965", 6_377_340.189, 299.3249646,
        482.530, -130.596, 564.557, -1.042, -0.214, -0.631, 8.150,
    ),

    /** 瑞士 CH1903（EPSG:6149），CH1903 斜轴墨卡托的基准。 */
    CH1903(
        "CH1903", 6_377_397.155, 299.1528128,
        674.374, 15.056, 405.346, 0.0, 0.0, 0.0, 0.0,
    ),
    ;

    val flattening: Double get() = 1.0 / inverseFlattening
    val eccentricitySquared: Double
        get() = flattening * (2.0 - flattening)

    val isWgs84: Boolean
        get() = tx == 0.0 && ty == 0.0 && tz == 0.0 && rx == 0.0 && ry == 0.0 &&
            rz == 0.0 && ppm == 0.0 && semiMajor == WGS84.semiMajor

    /**
     * 本基准的经纬度 → WGS84 经纬度。
     *
     * 三维 Helmert 换算经由地心直角坐标完成，位移向量的符号约定为
     * 「本基准 → WGS84」。
     */
    fun toWgs84(latitude: Double, longitude: Double): GeoPoint {
        if (this == WGS84) return GeoPoint(latitude, longitude)
        val source = toEcef(latitude, longitude)
        val rotated = helmert(source, tx, ty, tz, rx, ry, rz, ppm)
        val (lat, lon) = fromEcef(rotated[0], rotated[1], rotated[2], WGS84)
        return GeoPoint(lat, lon)
    }

    /** WGS84 经纬度 → 本基准的经纬度（对参考椭球做一次逆换算）。 */
    fun fromWgs84(latitude: Double, longitude: Double): GeoPoint {
        if (this == WGS84) return GeoPoint(latitude, longitude)
        val source = WGS84.toEcef(latitude, longitude)
        val rotated = helmert(source, -tx, -ty, -tz, -rx, -ry, -rz, -ppm)
        val (lat, lon) = fromEcef(rotated[0], rotated[1], rotated[2], this)
        return GeoPoint(lat, lon)
    }

    private fun toEcef(latitude: Double, longitude: Double): DoubleArray {
        val phi = Math.toRadians(latitude)
        val lam = Math.toRadians(longitude)
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val n = semiMajor / sqrt(1.0 - eccentricitySquared * sinPhi * sinPhi)
        return doubleArrayOf(
            n * cosPhi * cos(lam),
            n * cosPhi * sin(lam),
            n * (1.0 - eccentricitySquared) * sinPhi,
        )
    }

    private fun helmert(
        point: DoubleArray,
        tx: Double,
        ty: Double,
        tz: Double,
        rx: Double,
        ry: Double,
        rz: Double,
        ppm: Double,
    ): DoubleArray {
        val arcSeconds = Math.PI / (180.0 * 3600.0)
        val ex = rx * arcSeconds
        val ey = ry * arcSeconds
        val ez = rz * arcSeconds
        val scale = 1.0 + ppm * 1e-6
        val x = point[0]
        val y = point[1]
        val z = point[2]
        return doubleArrayOf(
            tx + scale * (x - ez * y + ey * z),
            ty + scale * (ez * x + y - ex * z),
            tz + scale * (-ey * x + ex * y + z),
        )
    }

    private fun fromEcef(x: Double, y: Double, z: Double, datum: GeodeticDatum): Pair<Double, Double> {
        val lon = atan2(y, x)
        val p = sqrt(x * x + y * y)
        // 纬度用 Bowring 式的迭代，收敛快且不需要初始海平面假定。
        var lat = atan2(z, p * (1.0 - datum.eccentricitySquared))
        repeat(6) {
            val sinLat = sin(lat)
            val n = datum.semiMajor / sqrt(1.0 - datum.eccentricitySquared * sinLat * sinLat)
            val height = p / cos(lat) - n
            lat = atan2(z, p * (1.0 - datum.eccentricitySquared * n / (n + height)))
        }
        require(abs(lat) <= Math.PI) { "latitude out of range" }
        return Math.toDegrees(lat) to Math.toDegrees(lon)
    }

    companion object {
        /** 设置页与持久化用的默认值。 */
        val default: GeodeticDatum = WGS84

        fun fromName(value: String?): GeodeticDatum =
            entries.firstOrNull { it.name == value } ?: default
    }
}

/** 一个经纬度对，单位为度。 */
data class GeoPoint(val latitude: Double, val longitude: Double)
