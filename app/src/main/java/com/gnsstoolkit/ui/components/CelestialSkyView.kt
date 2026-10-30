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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.gnsstoolkit.data.CelestialBody
import com.gnsstoolkit.data.SkyBody
import com.gnsstoolkit.ui.bodyColor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private fun skyOffset(azimuth: Double, elevation: Double, radius: Float, center: Offset, heading: Float): Offset {
    val r = radius * (90.0 - elevation.coerceIn(0.0, 90.0)).toFloat() / 90f
    val relative = ((azimuth.toFloat() - heading) % 360f + 360f) % 360f
    val rad = Math.toRadians(relative.toDouble())
    return Offset(center.x + (r * sin(rad)).toFloat(), center.y - (r * cos(rad)).toFloat())
}

/**
 * 和 [SkyView] 同一套俯视投影，但画的是太阳系天体的实时位置。
 *
 * 圆点大小按视星等缩放；地平线下的天体压在圆周上、透明度减半，方便看出「刚落下/将要升起」。
 * [heading] 同样是设备朝向，0 表示北朝上。
 */
@Composable
fun CelestialSkyView(
    bodies: List<SkyBody>,
    modifier: Modifier = Modifier,
    selected: CelestialBody? = null,
    onSelect: (CelestialBody?) -> Unit = {},
    heading: Float = 0f,
    pointingAzimuth: Float? = null,
    pointingElevation: Float? = null,
) {
    val measurer = rememberTextMeasurer()
    val latestBodies by rememberUpdatedState(bodies)
    val latestHeading by rememberUpdatedState(heading)
    val latestSelect by rememberUpdatedState(onSelect)

    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary

    val labels = bodies.associate { it.body to stringResource(it.body.labelRes) }
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
                val hitRadius = 34.dp.toPx()
                detectTapGestures { tap ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val center = Offset(width / 2f, height / 2f)
                    val radius = minOf(width, height) / 2f - 14.dp.toPx()
                    val nearest = latestBodies
                        .filter { it.isAboveHorizon }
                        .map { it to skyOffset(it.azimuthDegrees, it.elevationDegrees, radius, center, latestHeading) }
                        .minByOrNull { (_, offset) -> hypot(tap.x - offset.x, tap.y - offset.y) }
                    if (nearest != null && hypot(tap.x - nearest.second.x, tap.y - nearest.second.y) <= hitRadius) {
                        latestSelect(nearest.first.body)
                    } else {
                        latestSelect(null)
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 14.dp.toPx()

        drawCircle(color = surface.copy(alpha = 0.35f), radius = radius, center = center)
        drawCircle(color = grid, radius = radius, center = center, style = Stroke(1.dp.toPx()))

        for (fraction in listOf(1f / 3f, 2f / 3f)) {
            drawCircle(
                color = grid.copy(alpha = 0.6f),
                radius = radius * fraction,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
        }
        drawLine(
            color = grid.copy(alpha = 0.45f),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = grid.copy(alpha = 0.45f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.dp.toPx(),
        )

        // 设备朝向刻痕
        drawLine(
            color = accent,
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y - radius + 7.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val north = radiansFor(0.0, heading)
        val east = radiansFor(90.0, heading)
        val south = radiansFor(180.0, heading)
        val west = radiansFor(270.0, heading)
        val compassRadius = radius + 1.dp.toPx()
        drawCompassLabel(measurer, northLabel, north, center, compassRadius, labelStyle, labelColor)
        drawCompassLabel(measurer, eastLabel, east, center, compassRadius, labelStyle, labelColor)
        drawCompassLabel(measurer, southLabel, south, center, compassRadius, labelStyle, labelColor)
        drawCompassLabel(measurer, westLabel, west, center, compassRadius, labelStyle, labelColor)

        val ringRadius = radius * 2f / 3f
        listOf(30f to elev30, 60f to elev60).forEachIndexed { index, (_, text) ->
            val fraction = if (index == 0) 2f / 3f else 1f / 3f
            drawText(
                textMeasurer = measurer,
                text = text,
                style = TextStyle(fontSize = 9.sp, color = labelColor.copy(alpha = 0.7f)),
                topLeft = Offset(center.x - ringRadius * (1f - fraction) - 10.dp.toPx(), center.y + 2.dp.toPx()),
            )
        }

        val bright = bodies.filter { it.isAboveHorizon }
        val dim = bodies.filterNot { it.isAboveHorizon }

        dim.forEach { item -> drawBody(measurer, item, labels, center, radius, heading, labelStyle, dimmed = true, selected == item.body) }
        bright.forEach { item -> drawBody(measurer, item, labels, center, radius, heading, labelStyle, dimmed = false, selected == item.body) }

        val aimed = bodies.firstOrNull { it.body == selected }
        drawAimReticle(
            pointingAzimuth = pointingAzimuth,
            pointingElevation = pointingElevation,
            color = accent,
            targetAzimuth = aimed?.azimuthDegrees?.toFloat(),
            targetElevation = aimed?.elevationDegrees?.toFloat(),
            project = { az, el -> skyOffset(az.toDouble(), el.toDouble(), radius, center, heading) },
        )
    }
}

private fun radiansFor(bearing: Double, heading: Float): Double {
    val relative = ((bearing.toFloat() - heading) % 360f + 360f) % 360f
    return Math.toRadians(relative.toDouble())
}

private fun DrawScope.drawCompassLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    radians: Double,
    center: Offset,
    radius: Float,
    style: TextStyle,
    color: Color,
) {
    val layout = measurer.measure(text, style.copy(color = color))
    val position = Offset(
        center.x + radius * sin(radians).toFloat() - layout.size.width / 2f,
        center.y - radius * cos(radians).toFloat() - layout.size.height / 2f,
    )
    drawText(layout, topLeft = position)
}

private fun DrawScope.drawBody(
    measurer: androidx.compose.ui.text.TextMeasurer,
    item: SkyBody,
    labels: Map<CelestialBody, String>,
    center: Offset,
    radius: Float,
    heading: Float,
    labelStyle: TextStyle,
    dimmed: Boolean,
    isSelected: Boolean,
) {
    val offset = skyOffset(item.azimuthDegrees, item.elevationDegrees, radius, center, heading)
    val color = item.body.bodyColor().copy(alpha = if (dimmed) 0.35f else 1f)
    val dotRadius = bodyRadius(item.magnitude)

    if (isSelected) {
        drawCircle(
            color = color.copy(alpha = if (dimmed) 0.5f else 0.9f),
            radius = dotRadius + 4.dp.toPx(),
            center = offset,
            style = Stroke(2.dp.toPx()),
        )
    }
    drawCircle(color = color, radius = dotRadius, center = offset)
    if (item.body == CelestialBody.SUN) {
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            drawLine(
                color = color.copy(alpha = 0.7f),
                start = Offset(
                    offset.x + (dotRadius + 2.dp.toPx()) * sin(angle).toFloat(),
                    offset.y - (dotRadius + 2.dp.toPx()) * cos(angle).toFloat(),
                ),
                end = Offset(
                    offset.x + (dotRadius + 5.dp.toPx()) * sin(angle).toFloat(),
                    offset.y - (dotRadius + 5.dp.toPx()) * cos(angle).toFloat(),
                ),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    val showLabel = !dimmed || isSelected
    if (showLabel) {
        val label = labels[item.body] ?: return
        val layout = measurer.measure(label, labelStyle.copy(color = color))
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                offset.x - layout.size.width / 2f,
                offset.y + dotRadius + 2.dp.toPx(),
            ),
        )
    }
}

/** 星等越小越亮，点也越大：太阳/月亮 ~11dp，肉眼勉强可见的行星 ~3.5dp。 */
private fun DrawScope.bodyRadius(magnitude: Double): Float =
    (7.8 - 0.62 * magnitude).coerceIn(3.2, 11.0).toFloat().dp.toPx()
