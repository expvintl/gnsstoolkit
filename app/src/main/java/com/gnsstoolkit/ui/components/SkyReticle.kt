package com.gnsstoolkit.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** 两个地平坐标之间的球面角距（度）。 */
internal fun angularDistanceDegrees(az1: Float, el1: Float, az2: Float, el2: Float): Float {
    val d = PI / 180.0
    val cosine = cos(el1 * d) * cos(el2 * d) * cos((az1 - az2) * d) + sin(el1 * d) * sin(el2 * d)
    return (acos(cosine.coerceIn(-1.0, 1.0)) * 180.0 / PI).toFloat()
}

/**
 * 在天空图上画「设备当前指向」的准星，并从准星拉一条线到选中目标。
 *
 * 这相当于一个二维水平仪：转动机身时准星跟着走，把准星移到目标点上就表示手机已经对准了。
 * [pointingElevation] 低于 -10° 时说明手机背面朝地，此时方位角没有意义，直接不画。
 */
internal fun DrawScope.drawAimReticle(
    pointingAzimuth: Float?,
    pointingElevation: Float?,
    color: Color,
    targetAzimuth: Float?,
    targetElevation: Float?,
    project: (Float, Float) -> Offset,
) {
    if (pointingAzimuth == null || pointingElevation == null) return
    if (pointingElevation < -10f) return

    val point = project(pointingAzimuth, pointingElevation.coerceIn(0f, 90f))
    val aligned = targetAzimuth != null && targetElevation != null &&
        angularDistanceDegrees(pointingAzimuth, pointingElevation, targetAzimuth, targetElevation) < 3f

    if (targetAzimuth != null && targetElevation != null) {
        drawLine(
            color = color.copy(alpha = if (aligned) 0.9f else 0.4f),
            start = point,
            end = project(targetAzimuth, targetElevation),
            strokeWidth = if (aligned) 2.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    val radius = if (aligned) 13.dp.toPx() else 14.dp.toPx()
    // 只有一圈：外侧一层淡淡的辉光负责“看得见”，内侧的描边负责“指得准”。
    drawCircle(color = color.copy(alpha = 0.16f), radius = radius, center = point, style = Stroke(8.dp.toPx()))
    drawCircle(
        color = color,
        radius = radius,
        center = point,
        style = Stroke(if (aligned) 2.5.dp.toPx() else 1.5.dp.toPx()),
    )
}
