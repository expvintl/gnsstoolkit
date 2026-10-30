package com.gnsstoolkit.data

import android.location.GnssStatus

/** GNSS 星座（对应 [android.location.GnssStatus.CONSTELLATION_*]）。 */
enum class Constellation(val prefix: String) {
    GPS("G"),
    GLONASS("R"),
    GALILEO("E"),
    BEIDOU("C"),
    QZSS("J"),
    IRNSS("I"),
    SBAS("S"),
    UNKNOWN("U"),
    ;

    /** 系统品牌的通用写法，各语言下都保持不变。 */
    val displayName: String get() = when (this) {
        GPS -> "GPS"
        GLONASS -> "GLONASS"
        GALILEO -> "Galileo"
        BEIDOU -> "BeiDou"
        QZSS -> "QZSS"
        IRNSS -> "NavIC"
        SBAS -> "SBAS"
        UNKNOWN -> "Other"
    }

    companion object {
        fun fromGnssType(type: Int): Constellation = when (type) {
            GnssStatus.CONSTELLATION_GPS -> GPS
            GnssStatus.CONSTELLATION_SBAS -> SBAS
            GnssStatus.CONSTELLATION_GLONASS -> GLONASS
            GnssStatus.CONSTELLATION_QZSS -> QZSS
            GnssStatus.CONSTELLATION_BEIDOU -> BEIDOU
            GnssStatus.CONSTELLATION_GALILEO -> GALILEO
            GnssStatus.CONSTELLATION_IRNSS -> IRNSS
            else -> UNKNOWN
        }
    }
}

/** 由载波频率推断的信号频段名（用于双频/多频展示）。 */
data class Satellite(
    val svid: Int,
    val constellation: Constellation,
    val cn0: Float,
    val azimuth: Float,
    val elevation: Float,
    val usedInFix: Boolean,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val carrierHz: Float? = null,
    val basebandCn0: Float? = null,
) {
    val label: String get() = constellation.prefix + svid.toString().padStart(2, '0')

    val band: String? get() = carrierHz?.let { bandName(constellation, it) }
}

/** NMEA GSA 句给出的精度衰减因子。 */
data class Dops(
    val pdop: Float? = null,
    val hdop: Float? = null,
    val vdop: Float? = null,
) {
    val isAvailable: Boolean get() = pdop != null || hdop != null || vdop != null
}

/** 当前定位解。 */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val mslAltitude: Double?,
    val horizontalAccuracy: Float?,
    val verticalAccuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val timeMillis: Long,
    val provider: String,
    val satelliteCount: Int,
    val elapsedRealtimeNanos: Long,
)

/** 设备 GNSS 能力（Android 12+ 可查询，低版本为 null）。 */
data class GnssCapabilities(
    val measurements: Boolean? = null,
    val navigationMessages: Boolean? = null,
    val lowPowerMode: Boolean? = null,
    val satelliteBlocklist: Boolean? = null,
    val satellitePvt: Boolean? = null,
    val measurementCorrections: Boolean? = null,
    val antennaInfo: Boolean? = null,
    val accumulatedDeltaRange: Boolean? = null,
    val msb: Boolean? = null,
    val scheduling: Boolean? = null,
    val geofencing: Boolean? = null,
    val singleShotFix: Boolean? = null,
    val signalTypes: List<SignalTypeInfo> = emptyList(),
)

data class ConstellationStat(
    val constellation: Constellation,
    val visible: Int,
    val used: Int,
    val bestCn0: Float,
    val meanCn0: Float,
)

/** 引擎对外暴露的完整状态快照。 */
data class GnssState(
    val satellites: List<Satellite> = emptyList(),
    val fix: Fix? = null,
    val dops: Dops = Dops(),
    val magneticHeading: Float = 0f,
    val trueHeading: Float? = null,
    val magneticDeclination: Float? = null,
    val compassAccuracy: Int = -1,
    /** 设备背面（摄像头一侧，-Z 轴）指向的方位角（真北，度）。无磁力计时为 null。 */
    val cameraAzimuth: Float? = null,
    /** 设备背面指向的高度角（度，地平线以上为正，天顶 90）。 */
    val cameraElevation: Float? = null,
    /** 设备顶部边缘（+Y 轴）指向的方位角（真北，度）。 */
    val topAzimuth: Float? = null,
    /** 设备顶部边缘指向的高度角（度）。 */
    val topElevation: Float? = null,
    val isRunning: Boolean = false,
    val capabilities: GnssCapabilities? = null,
    val ttffMillis: Long? = null,
    val sessionStartMillis: Long = 0L,
    val nmeaLineCount: Long = 0L,
) {
    val visibleCount: Int get() = satellites.size
    val usedCount: Int get() = satellites.count { it.usedInFix }

    val meanCn0: Float
        get() = satellites.filter { it.cn0 > 0f }.let { list ->
            if (list.isEmpty()) 0f else list.sumOf { it.cn0.toDouble() }.toFloat() / list.size
        }

    val bestCn0: Float get() = satellites.maxOfOrNull { it.cn0 } ?: 0f

    /** 按星座汇总，GPS/GLONASS/Galileo/BeiDou 等固定顺序。 */
    val constellationStats: List<ConstellationStat>
        get() = satellites
            .groupBy { it.constellation }
            .map { (constellation, list) ->
                val valid = list.filter { it.cn0 > 0f }
                ConstellationStat(
                    constellation = constellation,
                    visible = list.size,
                    used = list.count { it.usedInFix },
                    bestCn0 = list.maxOfOrNull { it.cn0 } ?: 0f,
                    meanCn0 = if (valid.isEmpty()) 0f
                    else valid.sumOf { it.cn0.toDouble() }.toFloat() / valid.size,
                )
            }
            .sortedBy { it.constellation.ordinal }
}

/** 由载波频率推断频段名称，未知返回 null。 */
fun bandName(constellation: Constellation, carrierHz: Float): String? {
    val mhz = carrierHz / 1_000_000f
    fun near(target: Float) = kotlin.math.abs(mhz - target) < 8f
    return when (constellation) {
        Constellation.GPS -> when {
            near(1575.42f) -> "L1"
            near(1227.60f) -> "L2"
            near(1176.45f) -> "L5"
            else -> null
        }
        Constellation.GALILEO -> when {
            near(1575.42f) -> "E1"
            near(1176.45f) -> "E5a"
            near(1207.14f) -> "E5b"
            near(1278.75f) -> "E6"
            else -> null
        }
        Constellation.BEIDOU -> when {
            near(1561.098f) -> "B1I"
            near(1575.42f) -> "B1C"
            near(1176.45f) -> "B2a"
            near(1207.14f) -> "B2b"
            near(1268.52f) -> "B3I"
            else -> null
        }
        Constellation.GLONASS -> when {
            mhz in 1598f..1606f -> "G1"
            mhz in 1242f..1251f -> "G2"
            else -> null
        }
        Constellation.QZSS -> when {
            near(1575.42f) -> "L1"
            near(1227.60f) -> "L2"
            near(1176.45f) -> "L5"
            near(1278.75f) -> "L6"
            else -> null
        }
        Constellation.IRNSS -> when {
            near(1176.45f) -> "L5"
            near(2492.028f) -> "S"
            else -> null
        }
        Constellation.SBAS, Constellation.UNKNOWN -> null
    }
}

data class SignalTypeInfo(
    val constellation: Constellation,
    val band: String?,
    val frequencyMhz: Float,
)
