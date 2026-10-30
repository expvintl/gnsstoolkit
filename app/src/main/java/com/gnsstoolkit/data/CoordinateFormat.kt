package com.gnsstoolkit.data

/**
 * 坐标显示格式。枚举顺序即设置页的排列顺序，与 GPS Test 一致。
 */
enum class CoordinateFormat(val label: String) {
    DMS("DD\u00b0MM'SS.SSS\""),
    DDM("DD\u00b0MM.MMMM'"),
    DECIMAL("DD.DDDDDD\u00b0"),
    UTM("UTM"),
    MGRS("MGRS"),
    USNG("USNG"),
    OSGB_EN("OSGB (E,N)"),
    OSGB_GRID("OSGB (Grid Ref)"),
    MAIDENHEAD("Maidenhead"),
    CH1903("CH1903"),
    OSNI_EN("OSNI (E,N)"),
    OSNI_GRID("OSNI (Grid Ref)"),
    ;

    /** 该格式是否自带基准；为真时忽略用户在设置里选的基准。 */
    internal val pinnedDatum: GeodeticDatum?
        get() = when (this) {
            OSGB_EN, OSGB_GRID -> GeodeticDatum.OSGB36_MEAN
            OSNI_EN, OSNI_GRID -> GeodeticDatum.IRELAND_1965
            CH1903 -> GeodeticDatum.CH1903
            else -> null
        }

    val usesDatum: Boolean get() = pinnedDatum == null

    companion object {
        val default: CoordinateFormat = DMS

        fun fromName(value: String?): CoordinateFormat =
            entries.firstOrNull { it.name == value } ?: default
    }
}

/** 一次坐标格式化的结果。[lines] 每项是显示层的一行。 */
data class CoordinateReading(
    val lines: List<String>,
    val copyText: String = lines.joinToString(" "),
)
