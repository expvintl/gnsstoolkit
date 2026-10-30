package com.gnsstoolkit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.Satellite
import com.gnsstoolkit.ui.color
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private fun project(
    azimuth: Float,
    elevation: Float,
    radius: Float,
    center: Offset,
    heading: Float,
): Offset {
    val r = radius * (90f - elevation.coerceIn(0f, 90f)) / 90f
    val relative = ((azimuth - heading) % 360f + 360f) % 360f
    val rad = Math.toRadians(relative.toDouble())
    return Offset(center.x + (r * sin(rad)).toFloat(), center.y - (r * cos(rad)).toFloat())
}

/**
 * 从正上方俯视的天空图：圆心是天顶，圆周是地平线。
 *
 * [heading] 是设备当前朝向（磁方位角，0 = 北）。图会以“设备前方”为上对齐，
 * 所以转动机身时整个星图会跟着转；[heading] 为 0 时就是常见的北朝上。
 * 实心点是参与定位的卫星，空心点是已跟踪但未用于解算的卫星。
 */
@Composable
fun SkyView(
    satellites: List<Satellite>,
    modifier: Modifier = Modifier,
    selectedLabel: String? = null,
    onSelect: (Satellite?) -> Unit = {},
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    heading: Float = 0f,
    pointingAzimuth: Float? = null,
    pointingElevation: Float? = null,
) {
    val measurer = rememberTextMeasurer()
    // 朝向每 60ms 变一次，若拿它当 pointerInput 的 key，手势协程会被反复重启，
    // 慢速点击会被打断；改用 rememberUpdatedState 在 lambda 里读最新值。
    val latestSatellites by rememberUpdatedState(satellites)
    val latestHeading by rememberUpdatedState(heading)
    val cardBg = background
    val reticleColor = MaterialTheme.colorScheme.primary
    val grid = gridColor
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)

    val northLabel = stringResource(R.string.sky_north)
    val eastLabel = stringResource(R.string.sky_east)
    val southLabel = stringResource(R.string.sky_south)
    val westLabel = stringResource(R.string.sky_west)
    val elev30 = stringResource(R.string.sky_elevation_30)
    val elev60 = stringResource(R.string.sky_elevation_60)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                val hitRadius = 30.dp.toPx()
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(center.x, center.y) - 18.dp.toPx()
                    var best: Satellite? = null
                    var bestDistance = hitRadius
                    val headingNow = latestHeading
                    latestSatellites.forEach { satellite ->
                        val point = project(satellite.azimuth, satellite.elevation, radius, center, headingNow)
                        val distance = hypot(tap.x - point.x, tap.y - point.y)
                        if (distance < bestDistance) {
                            bestDistance = distance
                            best = satellite
                        }
                    }
                    onSelect(best)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(center.x, center.y) - 18.dp.toPx()

        drawCircle(color = cardBg, radius = radius, center = center)
        // 30° / 60° 仰角圈
        listOf(30f, 60f).forEach { elevation ->
            drawCircle(
                color = grid,
                radius = radius * (90f - elevation) / 90f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawCircle(color = grid, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
        drawLine(grid, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(grid, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())

        val outer = radius + 13.dp.toPx()
        fun compassLabel(text: String, bearing: Float) {
            val relative = ((bearing - heading) % 360f + 360f) % 360f
            val rad = Math.toRadians(relative.toDouble())
            val layout = measurer.measure(text, labelStyle.copy(color = labelColor))
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    center.x + outer * sin(rad).toFloat() - layout.size.width / 2f,
                    center.y - outer * cos(rad).toFloat() - layout.size.height / 2f,
                ),
            )
        }
        compassLabel(northLabel, 0f)
        compassLabel(eastLabel, 90f)
        compassLabel(southLabel, 180f)
        compassLabel(westLabel, 270f)

        // 屏幕正上方永远是设备朝向，给个小刻痕免得用户以为图转错了
        drawLine(
            color = labelColor.copy(alpha = 0.8f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y - radius - 7.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 仰角刻度贴在北向半径上
        listOf(30f, 60f).forEach { elevation ->
            val layout = measurer.measure(elev30.let { if (elevation == 30f) elev30 else elev60 },
                labelStyle.copy(color = labelColor.copy(alpha = 0.55f), fontSize = 9.sp))
            val r = radius * (90f - elevation) / 90f
            drawText(textLayoutResult = layout, topLeft = Offset(center.x + 3.dp.toPx(), center.y - r - layout.size.height / 2f))
        }

        satellites.forEach { satellite ->
            val point = project(satellite.azimuth, satellite.elevation, radius, center, heading)
            val color = satellite.constellation.color
            val isSelected = satellite.label == selectedLabel
            val dot = if (isSelected) 8.dp.toPx() else 6.dp.toPx()
            if (satellite.usedInFix) {
                drawCircle(color = color, radius = dot, center = point)
            } else {
                drawCircle(color = color.copy(alpha = 0.55f), radius = dot, center = point)
                drawCircle(color = color, radius = dot, center = point, style = Stroke(1.5.dp.toPx()))
            }
            if (isSelected) {
                drawCircle(color = color, radius = dot + 4.dp.toPx(), center = point, style = Stroke(2.dp.toPx()))
            }
            val layout = measurer.measure(
                satellite.label,
                labelStyle.copy(
                    color = if (satellite.usedInFix) labelColor else labelColor.copy(alpha = 0.6f),
                    fontSize = 9.5.sp,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(point.x - layout.size.width / 2f, point.y + dot + 1.dp.toPx()),
            )
        }

        val aimed = latestSatellites.firstOrNull { it.label == selectedLabel }
        drawAimReticle(
            pointingAzimuth = pointingAzimuth,
            pointingElevation = pointingElevation,
            color = reticleColor,
            targetAzimuth = aimed?.azimuth,
            targetElevation = aimed?.elevation,
            project = { az, el -> project(az, el, radius, center, heading) },
        )
    }
}
