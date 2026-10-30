package com.gnsstoolkit.data

import androidx.annotation.StringRes
import com.gnsstoolkit.R

/**
 * 轨迹记录的输出格式。GPX / KML 是通用的轨迹交换格式，CSV 保留给后续数据分析。
 */
enum class LogFormat(
    val extension: String,
    val mimeType: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    GPX(
        extension = "gpx",
        mimeType = "application/gpx+xml",
        labelRes = R.string.log_format_gpx,
        descriptionRes = R.string.log_format_gpx_desc,
    ),
    KML(
        extension = "kml",
        mimeType = "application/vnd.google-earth.kml+xml",
        labelRes = R.string.log_format_kml,
        descriptionRes = R.string.log_format_kml_desc,
    ),
    CSV(
        extension = "csv",
        mimeType = "text/csv",
        labelRes = R.string.log_format_csv,
        descriptionRes = R.string.log_format_csv_desc,
    );

    companion object {
        fun fromFileName(name: String): LogFormat? =
            entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) }

        fun fromName(value: String?): LogFormat =
            entries.firstOrNull { it.name == value } ?: GPX
    }
}
