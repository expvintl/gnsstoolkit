package com.gnsstoolkit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.saveable.rememberSaveable
import com.gnsstoolkit.data.CelestialBody
import com.gnsstoolkit.data.Fix
import com.gnsstoolkit.data.PlanetPositions
import com.gnsstoolkit.data.PointingAxis
import com.gnsstoolkit.data.SkyBody
import com.gnsstoolkit.ui.bodyColor
import com.gnsstoolkit.ui.components.CelestialSkyView
import com.gnsstoolkit.ui.components.angularDistanceDegrees
import java.time.Instant
import com.gnsstoolkit.data.GnssState
import com.gnsstoolkit.data.Satellite
import com.gnsstoolkit.data.UnitSystem
import com.gnsstoolkit.ui.components.MetricRow
import com.gnsstoolkit.ui.components.SkyView
import com.gnsstoolkit.ui.components.StatPair
import com.gnsstoolkit.ui.color
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyScreen(
    state: GnssState,
    units: UnitSystem,
    pointingAxis: PointingAxis = PointingAxis.default,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(MODE_SATELLITES) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var selectedBody by remember { mutableStateOf<CelestialBody?>(null) }
    val selected = state.satellites.firstOrNull { it.label == selectedLabel }
    val pointingAzimuth = when (pointingAxis) {
        PointingAxis.CAMERA -> state.cameraAzimuth
        PointingAxis.TOP -> state.topAzimuth
    }
    val pointingElevation = when (pointingAxis) {
        PointingAxis.CAMERA -> state.cameraElevation
        PointingAxis.TOP -> state.topElevation
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        ) {
            SegmentedButton(
                selected = mode == MODE_SATELLITES,
                onClick = { mode = MODE_SATELLITES },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.sky_mode_satellites))
            }
            SegmentedButton(
                selected = mode == MODE_BODIES,
                onClick = { mode = MODE_BODIES },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.sky_mode_bodies))
            }
        }

        if (mode == MODE_SATELLITES) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatPair(
                    label = stringResource(R.string.satellites_visible),
                    value = state.visibleCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatPair(
                    label = stringResource(R.string.satellites_used),
                    value = state.usedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatPair(
                    label = stringResource(R.string.signal_average),
                    value = String.format(Locale.US, "%.0f", state.meanCn0),
                    unit = stringResource(R.string.unit_dbhz),
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.fix == null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(R.string.no_fix),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            SkyView(
                satellites = state.satellites,
                selectedLabel = selectedLabel,
                onSelect = { selectedLabel = it?.label },
                heading = state.trueHeading ?: state.magneticHeading,
                pointingAzimuth = pointingAzimuth,
                pointingElevation = pointingElevation,
                modifier = Modifier.fillMaxWidth(),
            )

            AimHint(
                pointingAzimuth = pointingAzimuth,
                pointingElevation = pointingElevation,
                targetAzimuth = selected?.azimuth,
                targetElevation = selected?.elevation,
            )

            AnimatedVisibility(visible = selected != null) {
                if (selected != null) SatelliteDetailCard(selected, units)
            }
            if (selected == null) {
                Text(
                    text = stringResource(R.string.sky_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        } else {
            val fix = state.fix
            if (fix == null) {
                Text(
                    text = stringResource(R.string.sky_bodies_need_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                CelestialSection(
                    fix = fix,
                    selected = selectedBody,
                    onSelect = { selectedBody = it },
                    heading = state.trueHeading ?: state.magneticHeading,
                    pointingAzimuth = pointingAzimuth,
                    pointingElevation = pointingElevation,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 星空图：太阳与月亮之外还有 7 颗行星，按当前定位与时刻实时推算。 */
@Composable
private fun CelestialSection(
    fix: Fix,
    selected: CelestialBody?,
    onSelect: (CelestialBody?) -> Unit,
    heading: Float,
    pointingAzimuth: Float?,
    pointingElevation: Float?,
) {
    // 一分钟一更新：行星在天上一分钟移动不到一个像素
    val minute = fix.timeMillis / 60_000L
    val bodies = remember(minute, fix.latitude, fix.longitude) {
        PlanetPositions.positions(
            instant = Instant.ofEpochMilli(fix.timeMillis),
            latitudeDegrees = fix.latitude,
            longitudeDegrees = fix.longitude,
        )
    }

    CelestialSkyView(
        bodies = bodies,
        selected = selected,
        pointingAzimuth = pointingAzimuth,
        pointingElevation = pointingElevation,
        onSelect = onSelect,
        heading = heading,
        modifier = Modifier.fillMaxWidth(),
    )

    val chosen = bodies.firstOrNull { it.body == selected }
    AnimatedVisibility(visible = chosen != null) {
        if (chosen != null) SkyBodyCard(chosen)
    }
    if (chosen == null) {
        Text(
            text = stringResource(R.string.sky_body_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }

    bodies
        .sortedWith(
            compareByDescending<SkyBody> { it.isAboveHorizon }
                .thenByDescending { it.elevationDegrees },
        )
        .forEach { SkyBodyRow(it) }
}

@Composable
private fun SkyBodyRow(item: SkyBody) {
    val above = item.isAboveHorizon
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = item.body.bodyColor().copy(alpha = if (above) 1f else 0.35f),
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(item.body.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (above) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (!above) {
            Text(
                text = stringResource(R.string.sky_below_horizon),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = String.format(Locale.US, "%.0f° / %.0f°", item.elevationDegrees, item.azimuthDegrees),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkyBodyCard(item: SkyBody) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(item.body.bodyColor(), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(item.body.labelRes),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            MetricRow(
                label = stringResource(R.string.satellite_elevation),
                value = String.format(Locale.US, "%.1f", item.elevationDegrees),
                unit = stringResource(R.string.unit_degree),
            )
            MetricRow(
                label = stringResource(R.string.satellite_azimuth),
                value = String.format(Locale.US, "%.1f", item.azimuthDegrees),
                unit = stringResource(R.string.unit_degree),
            )
            MetricRow(
                label = stringResource(R.string.sky_magnitude),
                value = String.format(Locale.US, "%.1f", item.magnitude),
            )
            MetricRow(
                label = stringResource(R.string.sky_body_distance),
                value = if (item.distanceAu < 0.01) {
                    String.format(Locale.US, "%.0f", item.distanceAu * 149_597_870.7)
                } else {
                    String.format(Locale.US, "%.3f", item.distanceAu)
                },
                unit = if (item.distanceAu < 0.01) "km" else "AU",
            )
        }
    }
}

@Composable
private fun SatelliteDetailCard(satellite: Satellite, units: UnitSystem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = satellite.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = satellite.constellation.color,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = satellite.constellation.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            MetricRow(
                label = stringResource(R.string.satellite_cn0),
                value = String.format(Locale.US, "%.1f", satellite.cn0),
                unit = stringResource(R.string.unit_dbhz),
            )
            satellite.basebandCn0?.let {
                MetricRow(
                    label = stringResource(R.string.satellite_baseline),
                    value = String.format(Locale.US, "%.1f", it),
                    unit = stringResource(R.string.unit_dbhz),
                )
            }
            MetricRow(
                label = stringResource(R.string.satellite_elevation),
                value = String.format(Locale.US, "%.0f", satellite.elevation),
                unit = stringResource(R.string.unit_degree),
            )
            MetricRow(
                label = stringResource(R.string.satellite_azimuth),
                value = String.format(Locale.US, "%.0f", satellite.azimuth),
                unit = stringResource(R.string.unit_degree),
            )
            satellite.band?.let { band ->
                MetricRow(label = stringResource(R.string.satellite_band), value = band)
            }
            MetricRow(
                label = stringResource(R.string.satellite_used),
                value = stringResource(if (satellite.usedInFix) R.string.yes else R.string.no),
            )
            MetricRow(
                label = stringResource(R.string.satellite_ephemeris),
                value = stringResource(if (satellite.hasEphemeris) R.string.yes else R.string.no),
            )
            MetricRow(
                label = stringResource(R.string.satellite_almanac),
                value = stringResource(if (satellite.hasAlmanac) R.string.yes else R.string.no),
            )
        }
    }
}

private const val MODE_SATELLITES = 0
private const val MODE_BODIES = 1

/** 指向提示：有磁力计且手机背面朝上时显示，对准 3° 以内给出确认。 */
@Composable
private fun AimHint(
    pointingAzimuth: Float?,
    pointingElevation: Float?,
    targetAzimuth: Float?,
    targetElevation: Float?,
) {
    if (pointingAzimuth == null || pointingElevation == null || pointingElevation < -10f) return
    val aligned = targetAzimuth != null && targetElevation != null &&
        angularDistanceDegrees(pointingAzimuth, pointingElevation, targetAzimuth, targetElevation) < 3f
    Text(
        text = stringResource(if (aligned) R.string.sky_aim_locked else R.string.sky_aim_hint),
        style = MaterialTheme.typography.labelMedium,
        color = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
