package com.gnsstoolkit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.SunCalculator
import com.gnsstoolkit.data.SunPosition
import com.gnsstoolkit.data.WorldMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 等距圆柱投影的昼夜分界图：整幅世界地图，太阳直射点与晨昏线。
 *
 * 夜半球不是简单的半圆——晨昏线在图上是一条正弦状曲线，两极附近还会翻到另一侧。
 * 这里逐列解出「该经线上夜间的纬度区间」，再合成一个填充路径，所以曲线是解析解，不是贴图。
 *
 * 地图本身走 [drawWithCache]，只有尺寸或太阳位置变化才重算；两个标记放在上层
 * 单独的 Canvas 里，脉冲动画每帧重绘也不会牵动整张地图的路径构建。
 */
@Composable
fun DayNightMap(
    latitude: Double?,
    longitude: Double?,
    sun: SunPosition,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rings = remember(context) { WorldMap.landRings(context) }
    val description = stringResource(R.string.daynight_map_description)

    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.surface.luminance() > 0.5f
    val oceanColor = if (isLightTheme) Color(0xFFC3D9E8) else Color(0xFF12304A)
    val landColor = if (isLightTheme) Color(0xFF87A2B6) else Color(0xFF356F9A)
    val nightColor = if (isLightTheme) {
        Color(0xFF061420).copy(alpha = 0.66f)
    } else {
        Color.Black.copy(alpha = 0.68f)
    }
    val gridColor = colorScheme.onSurface.copy(alpha = 0.10f)
    val terminatorColor = colorScheme.primary.copy(alpha = 0.9f)
    val borderColor = colorScheme.outlineVariant
    val sunColor = Color(0xFFFFB020)
    val hereColor = colorScheme.primary
    val haloColor = Color.White
    val shape = MaterialTheme.shapes.medium

    val pulse: State<Float> = rememberInfiniteTransition(label = "position-pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(shape)
            .semantics { contentDescription = description },
    ) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val land = buildLandPath(rings, size)
                val night = buildNightPath(size, sun)
                val terminator = buildTerminatorPath(size, sun)

                onDrawBehind {
                    drawRect(oceanColor)
                    drawPath(land, landColor)
                    drawPath(night, nightColor)
                    drawGraticule(gridColor)
                    drawPath(terminator, terminatorColor, style = Stroke(width = 1.2.dp.toPx()))
                    drawRect(color = borderColor, style = Stroke(width = 1.dp.toPx()))
                }
            },
        )

        Canvas(Modifier.matchParentSize()) {
            val progress = pulse.value

            drawSunMarker(
                center = Offset(
                    x = longitudeToX(sun.subsolarLongitudeDegrees, size.width),
                    y = latitudeToY(sun.subsolarLatitudeDegrees, size.height),
                ),
                color = sunColor,
                halo = haloColor,
            )

            if (latitude != null && longitude != null) {
                drawPositionMarker(
                    center = Offset(
                        x = longitudeToX(longitude, size.width),
                        y = latitudeToY(latitude, size.height),
                    ),
                    color = hereColor,
                    halo = haloColor,
                    progress = progress,
                )
            }
        }
    }
}

/**
 * 太阳：琥珀色实心圆 + 八条短线，外面套两层描边（深色 + 白色）。
 *
 * 单靠白色描边在浅色海面上会糊掉，单靠深色描边在夜半球又会糊掉；两层叠在一起
 * 才能保证在任何底色上都读得出来。
 */
private fun DrawScope.drawSunMarker(center: Offset, color: Color, halo: Color) {
    val shadow = MarkerShadow
    val core = 6.4.dp.toPx()
    val raysInner = core + 2.6.dp.toPx()
    val raysOuter = core + 5.4.dp.toPx()

    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val from = Offset(center.x + dx * raysInner, center.y + dy * raysInner)
        val to = Offset(center.x + dx * raysOuter, center.y + dy * raysOuter)
        drawLine(shadow, from, to, strokeWidth = 4.4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(halo, from, to, strokeWidth = 3.0.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, from, to, strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    }

    drawCircle(shadow, radius = core + 2.2.dp.toPx(), center = center)
    drawCircle(halo, radius = core + 1.4.dp.toPx(), center = center)
    drawCircle(color, radius = core, center = center)
}

/** 当前位置：白底圆盘 + 主题色粗环 + 圆心，外加一圈随时间扩散的脉冲。 */
private fun DrawScope.drawPositionMarker(
    center: Offset,
    color: Color,
    halo: Color,
    progress: Float,
) {
    val shadow = MarkerShadow
    val ring = 7.2.dp.toPx()

    val pulseRadius = ring + 3.0.dp.toPx() + progress * 15.dp.toPx()
    val fade = (1f - progress) * 0.8f
    drawCircle(
        color = shadow.copy(alpha = fade * 0.55f),
        radius = pulseRadius,
        center = center,
        style = Stroke(width = 3.0.dp.toPx()),
    )
    drawCircle(
        color = color.copy(alpha = fade),
        radius = pulseRadius,
        center = center,
        style = Stroke(width = 2.0.dp.toPx()),
    )

    drawCircle(shadow.copy(alpha = 0.45f), radius = ring + 2.6.dp.toPx(), center = center)
    drawCircle(halo, radius = ring + 1.8.dp.toPx(), center = center)
    drawCircle(color, radius = ring, center = center, style = Stroke(width = 2.8.dp.toPx()))
    drawCircle(shadow.copy(alpha = 0.35f), radius = 3.4.dp.toPx(), center = center)
    drawCircle(color, radius = 2.6.dp.toPx(), center = center)
}

/** 标记统一用的深色描边，让亮色标记在昼半球浅蓝海面上也有边界。 */
private val MarkerShadow = Color(0xFF10161A)

private fun longitudeToX(longitude: Double, width: Float): Float =
    ((longitude + 180.0) / 360.0 * width).toFloat()

private fun latitudeToY(latitude: Double, height: Float): Float =
    ((90.0 - latitude) / 180.0 * height).toFloat()

private fun buildLandPath(rings: List<FloatArray>, size: Size): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    for (ring in rings) {
        path.moveTo(
            longitudeToX(ring[0].toDouble(), size.width),
            latitudeToY(ring[1].toDouble(), size.height),
        )
        var i = 2
        while (i + 1 < ring.size) {
            path.lineTo(
                longitudeToX(ring[i].toDouble(), size.width),
                latitudeToY(ring[i + 1].toDouble(), size.height),
            )
            i += 2
        }
        path.close()
    }
    return path
}

/** 逐列求夜间纬度区间，堆进一个路径一次填充：不会出现相邻矩形接缝，绘制调用也只有一次。 */
private fun buildNightPath(size: Size, sun: SunPosition): Path {
    val path = Path()
    val step = 2f
    var x = 0f
    while (x < size.width) {
        val longitude = (x + step / 2f) / size.width * 360.0 - 180.0
        val band = SunCalculator.nightBand(
            deltaLongitudeDegrees = normalizeSigned(longitude - sun.subsolarLongitudeDegrees),
            declinationDegrees = sun.declinationDegrees,
        )
        if (band != null) {
            val top = latitudeToY(band.endInclusive, size.height)
            val bottom = latitudeToY(band.start, size.height)
            path.addRect(Rect(x, top, x + step + 1f, bottom))
        }
        x += step
    }
    return path
}

/** 晨昏线：取夜间带不贴极点的那条边；纵向跳变说明极冠换侧，断开重开一段。 */
private fun buildTerminatorPath(size: Size, sun: SunPosition): Path {
    val path = Path()
    val step = 2f
    var previousY = Float.NaN
    var started = false
    var x = 0f
    while (x <= size.width) {
        val longitude = x / size.width * 360.0 - 180.0
        val band = SunCalculator.nightBand(
            deltaLongitudeDegrees = normalizeSigned(longitude - sun.subsolarLongitudeDegrees),
            declinationDegrees = sun.declinationDegrees,
        )
        if (band == null) {
            started = false
            previousY = Float.NaN
        } else {
            val edge = when {
                band.endInclusive > 89.99 -> band.start
                band.start < -89.99 -> band.endInclusive
                else -> band.start
            }
            val y = latitudeToY(edge, size.height)
            if (!started || abs(y - previousY) > size.height * 0.45f) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
            previousY = y
        }
        x += step
    }
    return path
}

private fun DrawScope.drawGraticule(color: Color) {
    val thin = 0.7.dp.toPx()
    var longitude = -180.0
    while (longitude <= 180.0) {
        val x = longitudeToX(longitude, size.width)
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = thin)
        longitude += 30.0
    }
    var latitude = -60.0
    while (latitude <= 60.0) {
        val y = latitudeToY(latitude, size.height)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = thin)
        latitude += 30.0
    }
    val equatorY = latitudeToY(0.0, size.height)
    drawLine(color, Offset(0f, equatorY), Offset(size.width, equatorY), strokeWidth = 1.dp.toPx())
}

private fun normalizeSigned(degrees: Double): Double {
    val wrapped = degrees % 360.0
    val positive = if (wrapped < 0.0) wrapped + 360.0 else wrapped
    return if (positive > 180.0) positive - 360.0 else positive
}
