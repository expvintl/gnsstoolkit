package com.gnsstoolkit.data

import androidx.annotation.StringRes
import com.gnsstoolkit.R

/**
 * 轨迹记录的采样策略。
 *
 * 输入本身是约 1 Hz 的定位回调，但静止时相邻点几乎重合、纯粹浪费空间，
 * 所以默认用 [ADAPTIVE] 按位移与时间双门限抽稀；需要固定节奏时选其它几项。
 */
enum class TrackSampling(
    /** 固定最小间隔（秒）；null 表示自适应。 */
    val fixedIntervalSeconds: Double?,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    ADAPTIVE(
        null,
        R.string.track_sampling_adaptive,
        R.string.track_sampling_adaptive_desc,
    ),
    EVERY_1_S(
        1.0,
        R.string.track_sampling_1s,
        R.string.track_sampling_1s_desc,
    ),
    EVERY_5_S(
        5.0,
        R.string.track_sampling_5s,
        R.string.track_sampling_5s_desc,
    ),
    EVERY_10_S(
        10.0,
        R.string.track_sampling_10s,
        R.string.track_sampling_10s_desc,
    ),
    EVERY_30_S(
        30.0,
        R.string.track_sampling_30s,
        R.string.track_sampling_30s_desc,
    ),
    ;

    companion object {
        fun fromName(value: String?): TrackSampling =
            entries.firstOrNull { it.name == value } ?: ADAPTIVE
    }
}
