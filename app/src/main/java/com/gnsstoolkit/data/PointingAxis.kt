package com.gnsstoolkit.data

import androidx.annotation.StringRes
import com.gnsstoolkit.R

/**
 * 天空图准星跟着手机哪一条轴走。
 *
 * - [CAMERA]：手机背面（摄像头那一侧），躺着或仰头看天时最顺手；
 * - [TOP]：手机顶部边缘，站着低头看屏幕时更自然。
 */
enum class PointingAxis(@param:StringRes val labelRes: Int, @param:StringRes val descriptionRes: Int) {
    CAMERA(R.string.pointing_axis_camera, R.string.pointing_axis_camera_desc),
    TOP(R.string.pointing_axis_top, R.string.pointing_axis_top_desc),
    ;

    companion object {
        val default = CAMERA
        fun fromName(value: String?): PointingAxis = entries.firstOrNull { it.name == value } ?: default
    }
}
