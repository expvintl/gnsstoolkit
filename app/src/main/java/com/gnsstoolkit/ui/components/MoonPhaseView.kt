package com.gnsstoolkit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.abs

/**
 * 月相盘：一个圆，亮部由「月-日黄经差」决定。
 *
 * 明暗界线（terminator）是一条半椭圆，横向半径 = R·|cos(黄经差)|。
 * 这里用逐行扫描线填充亮区：对每一行 y，亮部从 `cosφ·√(R²-y²)` 到 `√(R²-y²)`
 * （盈月时亮在右侧），亏月左右镜像。φ=0 宽度为 0（新月），φ=180 铺满（满月）。
 *
 * 朝向按北半球习惯：盈月亮右缘，亏月亮左缘。
 */
@Composable
fun MoonPhaseView(
    elongationDegrees: Double,
    contentDescriptionText: String? = null,
    modifier: Modifier = Modifier,
    discColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    litColor: Color = MaterialTheme.colorScheme.primary,
    outlineColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val semanticsModifier = if (contentDescriptionText != null) {
        Modifier.semantics { contentDescription = contentDescriptionText }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.size(96.dp).then(semanticsModifier)) {
        val radius = size.minDimension / 2f - 1.dp.toPx()
        if (radius <= 0f) return@Canvas
        val centerPoint = center
        val disc = Path().apply {
            addOval(
                Rect(
                    Offset(centerPoint.x - radius, centerPoint.y - radius),
                    Size(radius * 2, radius * 2),
                )
            )
        }

        drawPath(disc, discColor)

        // 亮区用两个基本图形的布尔运算拼出来：一个半圆盘 + 一条半椭圆（明暗界线）。
        // 早期版本用逐行扫描线填充，96dp 的圆上能看到明显的横向条纹——抗锯齿在
        // 像素边界之间的缝隙会露出来。几何布尔不会留下这种接缝。
        val cosine = cos(Math.toRadians(elongationDegrees)).toFloat()
        val waxing = elongationDegrees < 180.0
        val oval = Rect(
            Offset(centerPoint.x - radius, centerPoint.y - radius),
            Size(radius * 2, radius * 2),
        )
        val rightHalf = Path().apply {
            moveTo(centerPoint.x, centerPoint.y - radius)
            arcTo(oval, -90f, 180f, false)
            close()
        }
        val leftHalf = Path().apply {
            moveTo(centerPoint.x, centerPoint.y + radius)
            arcTo(oval, 90f, 180f, false)
            close()
        }
        val terminatorWidth = (radius * abs(cosine)).coerceAtLeast(0.01f)
        val terminator = Path().apply {
            addOval(
                Rect(
                    Offset(centerPoint.x - terminatorWidth, centerPoint.y - radius),
                    Size(terminatorWidth * 2, radius * 2),
                )
            )
        }

        val litSide = if (waxing) rightHalf else leftHalf
        val darkSide = if (waxing) leftHalf else rightHalf
        val lit = Path()
        if (cosine < 0f) {
            // 凸月：亮区越过中线，多出的是界线椭圆落在暗侧的那一半。
            val bulge = Path().apply { op(terminator, darkSide, PathOperation.Intersect) }
            lit.op(litSide, bulge, PathOperation.Union)
        } else {
            // 娥眉月/上下弦：亮区是半圆盘减去界线椭圆。
            lit.op(litSide, terminator, PathOperation.Difference)
        }
        drawPath(lit, litColor)

        drawCircle(
            color = outlineColor,
            radius = radius,
            center = centerPoint,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
