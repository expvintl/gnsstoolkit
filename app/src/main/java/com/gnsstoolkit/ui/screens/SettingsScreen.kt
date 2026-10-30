package com.gnsstoolkit.ui.screens

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.AppLanguage
import com.gnsstoolkit.data.AppSettings
import com.gnsstoolkit.data.CoordinateFormat
import com.gnsstoolkit.data.LogFormat
import com.gnsstoolkit.data.PointingAxis
import com.gnsstoolkit.data.Constellation
import com.gnsstoolkit.data.GeodeticDatum
import com.gnsstoolkit.data.ThemeMode
import com.gnsstoolkit.data.TrackSampling
import com.gnsstoolkit.data.UnitSystem
import com.gnsstoolkit.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionTitle(stringResource(R.string.appearance))
        ChoiceRow(
            title = stringResource(R.string.language),
            options = listOf(
                stringResource(R.string.language_system) to AppLanguage.SYSTEM,
                "简体中文" to "zh-CN",
                "English" to "en",
            ),
            selected = settings.language,
            onSelect = { tag -> onChange { it.copy(language = tag) } },
        )
        ChoiceRow(
            title = stringResource(R.string.theme),
            options = listOf(
                stringResource(R.string.theme_system) to ThemeMode.SYSTEM,
                stringResource(R.string.theme_light) to ThemeMode.LIGHT,
                stringResource(R.string.theme_dark) to ThemeMode.DARK,
            ),
            selected = settings.themeMode,
            onSelect = { mode -> onChange { it.copy(themeMode = mode) } },
        )
        SwitchRow(
            title = stringResource(R.string.dynamic_color),
            summary = stringResource(R.string.dynamic_color_summary),
            checked = settings.dynamicColor,
            onCheckedChange = { value -> onChange { it.copy(dynamicColor = value) } },
        )
        SwitchRow(
            title = stringResource(R.string.amoled),
            summary = stringResource(R.string.amoled_summary),
            checked = settings.amoled,
            enabled = settings.themeMode == ThemeMode.DARK,
            onCheckedChange = { value -> onChange { it.copy(amoled = value) } },
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.units))
        ChoiceRow(
            title = stringResource(R.string.units),
            options = listOf(
                stringResource(R.string.units_metric) to UnitSystem.METRIC,
                stringResource(R.string.units_imperial) to UnitSystem.IMPERIAL,
                stringResource(R.string.units_nautical) to UnitSystem.NAUTICAL,
                stringResource(R.string.units_aviation) to UnitSystem.AVIATION,
            ),
            selected = settings.units,
            onSelect = { units -> onChange { it.copy(units = units) } },
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.position_title))
        ListChoiceRow(
            title = stringResource(R.string.position_format),
            options = CoordinateFormat.entries.map { it.label to it },
            selected = settings.coordinateFormat,
            onSelect = { format -> onChange { it.copy(coordinateFormat = format) } },
        )
        ListChoiceRow(
            title = stringResource(R.string.geodetic_datum),
            options = GeodeticDatum.entries.map { it.label to it },
            selected = settings.geodeticDatum,
            onSelect = { datum -> onChange { it.copy(geodeticDatum = datum) } },
            enabled = settings.coordinateFormat.usesDatum,
        )
        if (!settings.coordinateFormat.usesDatum) {
            Text(
                text = stringResource(R.string.datum_pinned_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.constellations_title))
        Text(
            text = stringResource(R.string.constellations_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Constellation.entries
                .filter { it != Constellation.UNKNOWN }
                .take(4)
                .forEach { constellation ->
                    FilterChip(
                        selected = constellation !in settings.hiddenConstellations,
                        onClick = {
                            onChange { current ->
                                val hidden = current.hiddenConstellations.toMutableSet()
                                if (constellation in hidden) hidden.remove(constellation)
                                else hidden.add(constellation)
                                current.copy(hiddenConstellations = hidden)
                            }
                        },
                        label = { Text(constellation.displayName) },
                    )
                }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Constellation.entries
                .filter { it != Constellation.UNKNOWN }
                .drop(4)
                .forEach { constellation ->
                    FilterChip(
                        selected = constellation !in settings.hiddenConstellations,
                        onClick = {
                            onChange { current ->
                                val hidden = current.hiddenConstellations.toMutableSet()
                                if (constellation in hidden) hidden.remove(constellation)
                                else hidden.add(constellation)
                                current.copy(hiddenConstellations = hidden)
                            }
                        },
                        label = { Text(constellation.displayName) },
                    )
                }
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.log_section))
        ChoiceRow(
            title = stringResource(R.string.log_format),
            options = LogFormat.entries.map { format -> stringResource(format.labelRes) to format },
            selected = settings.logFormat,
            onSelect = { value -> onChange { it.copy(logFormat = value) } },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(settings.logFormat.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ChoiceRow(
            title = stringResource(R.string.track_sampling),
            options = TrackSampling.entries.map { s -> stringResource(s.labelRes) to s },
            selected = settings.trackSampling,
            onSelect = { value -> onChange { it.copy(trackSampling = value) } },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(settings.trackSampling.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.display))
        ChoiceRow(
            title = stringResource(R.string.pointing_axis),
            options = PointingAxis.entries.map { axis -> stringResource(axis.labelRes) to axis },
            selected = settings.pointingAxis,
            onSelect = { axis -> onChange { it.copy(pointingAxis = axis) } },
        )
        Text(
            text = stringResource(settings.pointingAxis.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            title = stringResource(R.string.keep_screen_on),
            summary = stringResource(R.string.keep_screen_on_summary),
            checked = settings.keepScreenOn,
            onCheckedChange = { value -> onChange { it.copy(keepScreenOn = value) } },
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.about))
        Text(
            text = stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.version) + " " + versionName(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (label, value) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** 选项多到塞不下 SegmentedButton 时用的下拉 + 单选对话框。 */
@Composable
private fun <T> ListChoiceRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.second == selected }?.first ?: "-"
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = { if (enabled) open = true },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == selected,
                                    onClick = {
                                        onSelect(value)
                                        open = false
                                    },
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
