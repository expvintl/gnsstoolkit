package com.gnsstoolkit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.CoordinateFormat
import com.gnsstoolkit.data.CoordinateFormatter
import com.gnsstoolkit.data.GeodeticDatum
import com.gnsstoolkit.data.LightBand
import com.gnsstoolkit.data.GnssState
import com.gnsstoolkit.data.MoonCalculator
import com.gnsstoolkit.data.SolarCalculator
import com.gnsstoolkit.data.SunCalculator
import com.gnsstoolkit.data.TimeRange
import com.gnsstoolkit.data.UnitSystem
import com.gnsstoolkit.data.formatAngle
import com.gnsstoolkit.data.formatAltitude
import com.gnsstoolkit.data.formatClock
import com.gnsstoolkit.data.formatDistance
import com.gnsstoolkit.data.formatSpeed
import com.gnsstoolkit.ui.components.DayNightMap
import com.gnsstoolkit.ui.components.MetricRow
import com.gnsstoolkit.ui.components.MoonPhaseView
import com.gnsstoolkit.ui.components.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MM_DD_HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun LocationScreen(
    state: GnssState,
    units: UnitSystem,
    format: CoordinateFormat,
    datum: GeodeticDatum,
    modifier: Modifier = Modifier,
) {
    val fix = state.fix
    val clipboard = LocalClipboardManager.current
    val zone = ZoneId.systemDefault()
    var compactSpeed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (fix == null) {
            Text(
                text = stringResource(R.string.location_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp),
            )
            return@Column
        }

        CoordinateCard(
            latitude = fix.latitude,
            longitude = fix.longitude,
            format = format,
            datum = datum,
            onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(
                title = stringResource(R.string.altitude),
                value = formatAltitude(fix.altitude, units)?.let { "${it.value} ${it.unit}" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                title = stringResource(R.string.speed),
                value = formatSpeed(fix.speed, units, compactSpeed)
                    ?.let { "${it.value} ${it.unit}" } ?: "—",
                modifier = Modifier.weight(1f),
                onClick = { compactSpeed = !compactSpeed },
                onClickLabel = stringResource(R.string.speed_toggle),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(
                title = stringResource(R.string.accuracy_horizontal),
                value = formatDistance(fix.horizontalAccuracy, units)
                    ?.let { "± ${it.value} ${it.unit}" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                title = stringResource(R.string.heading),
                value = formatAngle(fix.bearing)?.let { "${it.value}${it.unit}" } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle(stringResource(R.string.dop_title))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DopCell(stringResource(R.string.dop_hdop), state.dops.hdop, Modifier.weight(1f))
                    DopCell(stringResource(R.string.dop_vdop), state.dops.vdop, Modifier.weight(1f))
                    DopCell(stringResource(R.string.dop_pdop), state.dops.pdop, Modifier.weight(1f))
                }
                Text(
                    text = stringResource(R.string.dop_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        SectionTitle(stringResource(R.string.fix_time))
        MetricRow(
            label = stringResource(R.string.time_local),
            value = formatClock(fix.timeMillis, zone),
        )
        MetricRow(
            label = stringResource(R.string.time_utc),
            value = formatClock(fix.timeMillis, ZoneId.of("UTC")),
        )
        MetricRow(
            label = stringResource(R.string.ttff),
            value = state.ttffMillis?.let { String.format(Locale.US, "%.1f", it / 1000f) },
            unit = "s",
        )
        MetricRow(label = stringResource(R.string.provider), value = fix.provider)
        fix.verticalAccuracy?.let {
            MetricRow(
                label = stringResource(R.string.accuracy_vertical),
                value = formatDistance(it, units)?.let { q -> "± ${q.value} ${q.unit}" },
            )
        }
        fix.mslAltitude?.let {
            MetricRow(
                label = stringResource(R.string.altitude_msl),
                value = formatAltitude(it, units)?.let { q -> "${q.value} ${q.unit}" },
            )
        }

        SolarSection(latitude = fix.latitude, longitude = fix.longitude, zone = zone)
        Spacer(Modifier.height(16.dp))
        MoonSection(nowMillis = fix.timeMillis, zone = zone)
        Spacer(Modifier.height(16.dp))
        DayNightSection(
            latitude = fix.latitude,
            longitude = fix.longitude,
            nowMillis = fix.timeMillis,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CoordinateCard(
    latitude: Double,
    longitude: Double,
    format: CoordinateFormat,
    datum: GeodeticDatum,
    onCopy: (String) -> Unit,
) {
    val reading = remember(latitude, longitude, format, datum) {
        CoordinateFormatter.format(latitude, longitude, datum, format)
    }
    val effectiveDatum = format.pinnedDatum ?: datum
    // 长串（MGRS / 网格参考号）用更小的字号，避免折行。
    val valueStyle = if (reading.lines.any { it.length > 15 }) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.headlineSmall
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = format.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = effectiveDatum.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            reading.lines.forEach { line ->
                Text(text = line, style = valueStyle)
                Spacer(Modifier.height(2.dp))
            }
            if (format != CoordinateFormat.DECIMAL) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = CoordinateFormatter.format(
                        latitude, longitude, datum, CoordinateFormat.DECIMAL,
                    ).lines.joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.coord_copy_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopy(reading.copyText) }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Card(
        modifier = if (onClick == null) modifier else modifier.clickable(onClickLabel = onClickLabel, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onClick != null) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun DopCell(label: String, value: Float?, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** 「06:05 – 06:25」；缺任一时刻就当整段不可用。 */
private fun TimeRange?.rangeText(): String? =
    this?.let { "${it.start.format(HHMM)}\u2013${it.end.format(HHMM)}" }

@Composable
private fun SolarSection(latitude: Double, longitude: Double, zone: ZoneId) {
    val events = remember(latitude, longitude, zone) {
        runCatching {
            val date = LocalDate.now(zone)
            SolarCalculator.events(date, latitude, longitude, zone)
        }.getOrNull()
    } ?: return

    SectionTitle(stringResource(R.string.sun_section))
    val unavailable = stringResource(R.string.not_available)
    MetricRow(
        label = stringResource(R.string.sunrise),
        value = events.sunrise?.format(HHMM) ?: unavailable,
    )
    MetricRow(
        label = stringResource(R.string.sunset),
        value = events.sunset?.format(HHMM) ?: unavailable,
    )
    Spacer(Modifier.height(4.dp))
    TwilightTable(bands = events.lightBands, unavailable = unavailable)
    Spacer(Modifier.height(4.dp))
    val hours = events.dayLengthMinutes?.div(60) ?: 0
    val minutes = events.dayLengthMinutes?.rem(60) ?: 0
    MetricRow(label = stringResource(R.string.timezone), value = zone.id)
    MetricRow(
        label = stringResource(R.string.day_length),
        value = String.format(Locale.US, "%d h %02d m", hours, minutes),
    )
}

/**
 * 五段光照时间。每段最多两截（上午一截、傍晚一截），用间隔号分开；高纬退化成一截。
 */
@Composable
private fun TwilightTable(bands: List<LightBand>, unavailable: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    bands.forEach { band ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(band.period.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.width(96.dp),
            )
            Text(
                text = band.intervals
                    .mapNotNull { it.rangeText() }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" \u00b7 ")
                    ?: unavailable,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 月相不依赖观测者位置，只要时刻；一小时内的变化不可见，所以按小时缓存一次计算。
 */
@Composable
private fun MoonSection(nowMillis: Long, zone: ZoneId) {
    val hourBucket = nowMillis / 3_600_000L
    val phase = remember(hourBucket) { MoonCalculator.phase(Instant.ofEpochMilli(nowMillis)) }
    val now = remember(hourBucket) { Instant.ofEpochMilli(nowMillis) }
    val daysToNew = ChronoUnit.DAYS.between(now, phase.nextNewMoon)
    val daysToFull = ChronoUnit.DAYS.between(now, phase.nextFullMoon)

    SectionTitle(stringResource(R.string.moon_section))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(phase.name.labelRes),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(
                    R.string.moon_summary,
                    String.format(Locale.US, "%.0f%%", phase.illuminatedFraction * 100.0),
                    String.format(Locale.US, "%.1f", phase.ageDays),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoonPhaseView(
            elongationDegrees = phase.elongationDegrees,
            contentDescriptionText = stringResource(phase.name.labelRes),
        )
    }
    MetricRow(
        label = stringResource(R.string.moon_next_new),
        value = phase.nextNewMoon.atZone(zone).format(MM_DD_HHMM),
        unit = stringResource(R.string.moon_in_days, daysToNew),
    )
    MetricRow(
        label = stringResource(R.string.moon_next_full),
        value = phase.nextFullMoon.atZone(zone).format(MM_DD_HHMM),
        unit = stringResource(R.string.moon_in_days, daysToFull),
    )
}

@Composable
private fun DayNightSection(latitude: Double, longitude: Double, nowMillis: Long) {
    // 晨昏线一分钟走 0.25°，每帧重算没有意义；按分钟缓存。
    val minuteBucket = nowMillis / 60_000L
    val sun = remember(minuteBucket) { SunCalculator.position(Instant.ofEpochMilli(nowMillis)) }
    val elevation = remember(minuteBucket, latitude, longitude) {
        SunCalculator.elevationDegrees(latitude, longitude, sun)
    }

    SectionTitle(stringResource(R.string.daynight_section))
    DayNightMap(latitude = latitude, longitude = longitude, sun = sun)
    Spacer(Modifier.height(10.dp))
    MapLegendRow(
        label = stringResource(R.string.daynight_subsolar),
        value = hemisphere(sun.subsolarLatitudeDegrees, isLatitude = true) +
            "  " + hemisphere(sun.subsolarLongitudeDegrees, isLatitude = false),
        marker = MapMarker.SUN,
    )
    MapLegendRow(
        label = stringResource(R.string.daynight_here),
        value = hemisphere(latitude, isLatitude = true) +
            "  " + hemisphere(longitude, isLatitude = false),
        marker = MapMarker.POSITION,
    )
    MapLegendRow(
        label = stringResource(R.string.daynight_elevation),
        value = String.format(Locale.US, "%+.1f", elevation),
        unit = "\u00b0",
        marker = MapMarker.NONE,
    )
}

private enum class MapMarker { SUN, POSITION, NONE }

/** 跟地图上的两个标记一一对应：太阳是实心圆，当前位置是空心圆环。 */
@Composable
private fun MapLegendRow(
    label: String,
    value: String,
    unit: String? = null,
    marker: MapMarker,
) {
    val sunColor = Color(0xFFFFB020)
    val hereColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            when (marker) {
                MapMarker.SUN -> {
                    drawCircle(Color.White, radius = size.minDimension / 2f)
                    drawCircle(sunColor, radius = size.minDimension / 2f - 1.2.dp.toPx())
                }
                MapMarker.POSITION -> {
                    drawCircle(Color.White, radius = size.minDimension / 2f)
                    drawCircle(
                        color = hereColor,
                        radius = size.minDimension / 2f - 1.6.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                MapMarker.NONE -> Unit
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hemisphere(value: Double, isLatitude: Boolean): String {
    val suffix = if (isLatitude) {
        if (value >= 0.0) "N" else "S"
    } else {
        if (value >= 0.0) "E" else "W"
    }
    return String.format(Locale.US, "%.2f\u00b0%s", abs(value), suffix)
}
