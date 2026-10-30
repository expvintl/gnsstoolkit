package com.gnsstoolkit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gnsstoolkit.R
import com.gnsstoolkit.data.GnssState
import com.gnsstoolkit.data.formatAngle
import com.gnsstoolkit.ui.components.CompassView
import com.gnsstoolkit.ui.components.MetricRow
import java.util.Locale

@Composable
fun CompassScreen(
    state: GnssState,
    hasCompass: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (!hasCompass) {
            Text(
                text = stringResource(R.string.compass_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return@Column
        }

        CompassView(
            magneticHeading = state.magneticHeading,
            declination = state.magneticDeclination,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Column(Modifier.padding(top = 8.dp)) {
            MetricRow(
                label = stringResource(R.string.compass_magnetic),
                value = formatAngle(state.magneticHeading)?.value,
                unit = stringResource(R.string.unit_degree),
                emphasise = true,
            )
            MetricRow(
                label = stringResource(R.string.compass_true),
                value = state.trueHeading?.let { formatAngle(it)?.value },
                unit = stringResource(R.string.unit_degree),
                emphasise = true,
            )
            MetricRow(
                label = stringResource(R.string.compass_declination),
                value = state.magneticDeclination?.let {
                    String.format(Locale.US, "%s%.1f", if (it >= 0) "+" else "", it)
                },
                unit = stringResource(R.string.unit_degree),
            )
            MetricRow(
                label = stringResource(R.string.compass_gps_course),
                value = state.fix?.bearing?.let { formatAngle(it)?.value },
                unit = stringResource(R.string.unit_degree),
            )
            MetricRow(
                label = stringResource(R.string.compass_sensor_accuracy),
                value = compassAccuracyLabel(state.compassAccuracy),
            )
        }

        if (state.compassAccuracy < 2) {
            Text(
                text = stringResource(R.string.compass_calibration_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            )
        } else {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun compassAccuracyLabel(accuracy: Int): String = when (accuracy) {
    3 -> stringResource(R.string.compass_accuracy_high)
    2 -> stringResource(R.string.compass_accuracy_medium)
    1 -> stringResource(R.string.compass_accuracy_low)
    0 -> stringResource(R.string.compass_accuracy_unreliable)
    else -> stringResource(R.string.not_available)
}
