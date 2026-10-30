package com.gnsstoolkit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.GnssState
import com.gnsstoolkit.ui.components.ConstellationStatCard
import com.gnsstoolkit.ui.components.SatelliteSignalRow
import java.util.Locale

@Composable
fun SignalScreen(state: GnssState, modifier: Modifier = Modifier) {
    var usedOnly by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }

    val satellites = remember(state.satellites, usedOnly) {
        state.satellites
            .filter { !usedOnly || it.usedInFix }
            .distinctBy { it.label }
            .sortedWith(compareByDescending<com.gnsstoolkit.data.Satellite> { it.cn0 }
                .thenBy { it.constellation.ordinal })
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.constellationStats.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.constellationStats, key = { it.constellation.name }) { stat ->
                    ConstellationStatCard(stat)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.signal_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format(Locale.US, "%d / %d", state.usedCount, state.visibleCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !usedOnly,
                onClick = { usedOnly = false },
                label = { Text(stringResource(R.string.satellites_visible)) },
            )
            FilterChip(
                selected = usedOnly,
                onClick = { usedOnly = true },
                label = { Text(stringResource(R.string.satellites_used)) },
            )
        }

        if (satellites.isEmpty()) {
            Text(
                text = stringResource(R.string.signal_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(satellites, key = { it.label }) { satellite ->
                    SatelliteSignalRow(
                        satellite = satellite,
                        selected = satellite.label == selectedLabel,
                        onClick = {
                            selectedLabel = if (selectedLabel == satellite.label) null else satellite.label
                        },
                    )
                }
            }
        }
    }
}
