package com.gnsstoolkit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gnsstoolkit.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 罗盘盘面：盘面按磁北朝向反向旋转，设备朝向固定在顶部。
 * [magneticHeading] 为磁北方向角，[declination] 为磁偏角（东正西负）。
 */
@Composable
fun CompassView(
    magneticHeading: Float,
    declination: Float?,
    modifier: Modifier = Modifier,
    showTrueNorth: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val tickColor = scheme.outlineVariant
    val majorTickColor = scheme.outline
    val cardinalColor = scheme.onSurface
    val northColor = scheme.error
    val bodyColor = scheme.surfaceContainerHighest
    val accentColor = scheme.primary
    val declinationLabel = stringResource(R.string.compass_declination)

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(center.x, center.y) - 20.dp.toPx()

            drawCircle(color = bodyColor.copy(alpha = 0.5f), radius = radius, center = center)
            drawCircle(color = tickColor, radius = radius, center = center, style = Stroke(1.5.dp.toPx()))

            // 刻度随设备朝向反向旋转
            rotate(degrees = -magneticHeading, pivot = center) {
                for (degree in 0 until 360 step 5) {
                    val isMajor = degree % 30 == 0
                    val isMedium = degree % 10 == 0
                    val length = when {
                        isMajor -> 14.dp.toPx()
                        isMedium -> 9.dp.toPx()
                        else -> 5.dp.toPx()
                    }
                    val width = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                    val rad = Math.toRadians(degree.toDouble())
                    val outer = Offset(
                        center.x + (radius * sin(rad)).toFloat(),
                        center.y - (radius * cos(rad)).toFloat(),
                    )
                    val inner = Offset(
                        center.x + ((radius - length) * sin(rad)).toFloat(),
                        center.y - ((radius - length) * cos(rad)).toFloat(),
                    )
                    drawLine(
                        color = if (isMajor) majorTickColor else tickColor,
                        start = inner,
                        end = outer,
                        strokeWidth = width,
                    )
                }
            }

            fun cardinal(text: String, degree: Float, color: Color, size: Float) {
                val layout = measurer.measure(
                    text,
                    TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = color),
                )
                val rad = Math.toRadians((degree - magneticHeading).toDouble())
                val r = radius - 30.dp.toPx()
                val x = center.x + (r * sin(rad)).toFloat()
                val y = center.y - (r * cos(rad)).toFloat()
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
                )
            }
            cardinal("N", 0f, northColor, 16f)
            cardinal("E", 90f, cardinalColor, 14f)
            cardinal("S", 180f, cardinalColor, 14f)
            cardinal("W", 270f, cardinalColor, 14f)

            // 顶部固定指标
            val arrow = Path().apply {
                moveTo(center.x, center.y - radius - 6.dp.toPx())
                lineTo(center.x - 7.dp.toPx(), center.y - radius - 18.dp.toPx())
                lineTo(center.x + 7.dp.toPx(), center.y - radius - 18.dp.toPx())
                close()
            }
            drawPath(arrow, color = accentColor)

            // 真北指针：屏幕上角度 = -(磁北方位 + 磁偏角)
            if (showTrueNorth && declination != null) {
                val rad = Math.toRadians(-(magneticHeading + declination).toDouble())
                val tip = Offset(
                    center.x + (radius * 0.66f * sin(rad)).toFloat(),
                    center.y - (radius * 0.66f * cos(rad)).toFloat(),
                )
                drawLine(
                    color = accentColor,
                    start = center,
                    end = tip,
                    strokeWidth = 3.dp.toPx(),
                )
                drawCircle(color = accentColor, radius = 5.dp.toPx(), center = tip)
            }
            // 中心读数
            val headingLayout = measurer.measure(
                "${magneticHeading.toInt()}°",
                TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
            )
            drawText(
                textLayoutResult = headingLayout,
                topLeft = Offset(
                    center.x - headingLayout.size.width / 2f,
                    center.y - headingLayout.size.height / 2f,
                ),
            )
            if (declination != null) {
                val sign = if (declination >= 0) "+" else ""
                val subLayout = measurer.measure(
                    String.format(Locale.US, "%s %s%.1f°", declinationLabel, sign, declination),
                    TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant),
                )
                drawText(
                    textLayoutResult = subLayout,
                    topLeft = Offset(
                        center.x - subLayout.size.width / 2f,
                        center.y + headingLayout.size.height / 2f + 2.dp.toPx(),
                    ),
                )
            }
        }
    }
}
