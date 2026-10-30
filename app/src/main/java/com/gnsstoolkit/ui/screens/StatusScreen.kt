package com.gnsstoolkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.GnssCapabilities
import com.gnsstoolkit.data.GnssState
import com.gnsstoolkit.ui.components.MetricRow
import com.gnsstoolkit.ui.components.SectionTitle
import java.util.Locale

@Composable
fun StatusScreen(
    state: GnssState,
    onRequestAgps: () -> Unit,
    onClearAgps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionTitle(stringResource(R.string.engine_status))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (state.isRunning) R.string.engine_running else R.string.engine_stopped
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    state.fix?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text(it.provider.uppercase(Locale.US)) },
                        )
                    }
                }
                MetricRow(
                    label = stringResource(R.string.engine_ttff),
                    value = state.ttffMillis?.let { String.format(Locale.US, "%.1f", it / 1000f) },
                    unit = "s",
                )
                MetricRow(
                    label = stringResource(R.string.engine_nmea),
                    value = state.nmeaLineCount.toString(),
                )
                MetricRow(
                    label = stringResource(R.string.satellites_visible),
                    value = "${state.visibleCount} / ${state.usedCount}",
                )
            }
        }

        SectionTitle(stringResource(R.string.capabilities))
        val caps = state.capabilities
        if (caps == null) {
            Text(
                text = stringResource(R.string.capabilities_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            CapabilityGrid(caps)
            if (caps.signalTypes.isNotEmpty()) {
                SectionTitle(stringResource(R.string.capability_signal_types))
                caps.signalTypes.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { type ->
                            Text(
                                text = "${type.constellation.prefix} ${type.band ?: "?"}  " +
                                    String.format(Locale.US, "%.2f MHz", type.frequencyMhz),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.agps))
        OutlinedCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agps_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRequestAgps) {
                        Text(stringResource(R.string.agps_request))
                    }
                    TextButton(onClick = onClearAgps) {
                        Text(stringResource(R.string.agps_clear))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CapabilityGrid(caps: GnssCapabilities) {
    val items = listOf(
        stringResource(R.string.capability_measurements) to caps.measurements,
        stringResource(R.string.capability_nav_messages) to caps.navigationMessages,
        stringResource(R.string.capability_dual_frequency) to
            (caps.signalTypes.map { it.frequencyMhz }.distinct().size > 1),
        stringResource(R.string.capability_accumulated_delta) to caps.accumulatedDeltaRange,
        stringResource(R.string.capability_antenna) to caps.antennaInfo,
        stringResource(R.string.capability_msb) to caps.msb,
        stringResource(R.string.capability_low_power) to caps.lowPowerMode,
        stringResource(R.string.capability_satellite_pvt) to caps.satellitePvt,
        stringResource(R.string.capability_scheduling) to caps.scheduling,
        stringResource(R.string.capability_geofencing) to caps.geofencing,
        stringResource(R.string.capability_single_shot) to caps.singleShotFix,
        stringResource(R.string.capability_blocklist) to caps.satelliteBlocklist,
        stringResource(R.string.capability_corrections) to caps.measurementCorrections,
    )
    Column {
        items.forEachIndexed { index, (label, enabled) ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (enabled == true) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
