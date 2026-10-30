package com.gnsstoolkit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnsstoolkit.MainViewModel
import com.gnsstoolkit.R
import com.gnsstoolkit.data.AppLanguage
import com.gnsstoolkit.ui.screens.CompassScreen
import com.gnsstoolkit.ui.screens.LocationScreen
import com.gnsstoolkit.ui.screens.LogScreen
import com.gnsstoolkit.ui.screens.SettingsScreen
import com.gnsstoolkit.ui.screens.SignalScreen
import com.gnsstoolkit.ui.screens.SkyScreen
import com.gnsstoolkit.ui.screens.StatusScreen
import com.gnsstoolkit.ui.theme.GnssToolkitTheme

private enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    SKY(R.string.nav_sky, Icons.Outlined.Public),
    SIGNAL(R.string.nav_signal, Icons.Outlined.BarChart),
    LOCATION(R.string.nav_location, Icons.Outlined.MyLocation),
    COMPASS(R.string.nav_compass, Icons.Outlined.Explore),
    STATUS(R.string.nav_status, Icons.Outlined.Info),
}

private sealed interface Screen {
    data class Tab(val tab: MainTab) : Screen
    data object Settings : Screen
    data object Logs : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GnssToolkitApp(viewModel: MainViewModel, permissionPrompt: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val darkTheme = when (settings.themeMode) {
        com.gnsstoolkit.data.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        com.gnsstoolkit.data.ThemeMode.LIGHT -> false
        com.gnsstoolkit.data.ThemeMode.DARK -> true
    }

    GnssToolkitTheme(
        darkTheme = darkTheme,
        dynamicColor = settings.dynamicColor,
        amoled = settings.amoled,
    ) {
        val view = LocalView.current
        val activity = remember(view) { view.context.findActivity() }
        LaunchedEffect(settings.language) {
            if (settings.language != AppLanguage.current) {
                AppLanguage.current = settings.language
                activity?.recreate()
            }
        }
        DisposableEffect(settings.keepScreenOn) {
            view.keepScreenOn = settings.keepScreenOn
            onDispose { view.keepScreenOn = false }
        }

        val gnss by viewModel.gnss.collectAsStateWithLifecycle()
        val granted by viewModel.permissionGranted.collectAsStateWithLifecycle()
        val recording by viewModel.recording.collectAsStateWithLifecycle()
        val recordingName by viewModel.recordingName.collectAsStateWithLifecycle()
        val logFiles by viewModel.logFiles.collectAsStateWithLifecycle()
        val recordingPoints by viewModel.recordingPoints.collectAsStateWithLifecycle()
        val recordingDistance by viewModel.recordingDistance.collectAsStateWithLifecycle()

        var tabIndex by rememberSaveable { mutableStateOf(0) }
        var overlay by rememberSaveable { mutableStateOf(0) }
        val currentTab = MainTab.entries[tabIndex]
        val screen: Screen = when (overlay) {
            1 -> Screen.Settings
            2 -> Screen.Logs
            else -> Screen.Tab(currentTab)
        }

        if (!granted) {
            PermissionGate(onRequest = permissionPrompt)
            return@GnssToolkitTheme
        }

        BackHandler(enabled = overlay != 0) { overlay = 0 }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (val s = screen) {
                                is Screen.Tab -> stringResource(s.tab.labelRes)
                                Screen.Settings -> stringResource(R.string.nav_settings)
                                Screen.Logs -> stringResource(R.string.log_title)
                            }
                        )
                    },
                    navigationIcon = {
                        if (overlay != 0) {
                            IconButton(onClick = { overlay = 0 }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.close),
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleRecording) {
                            Icon(
                                imageVector = if (recording) Icons.Filled.Stop
                                else Icons.Outlined.FiberManualRecord,
                                contentDescription = stringResource(
                                    if (recording) R.string.log_stop else R.string.log_start
                                ),
                                tint = if (recording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { overlay = 2 }) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = stringResource(R.string.nav_log),
                            )
                        }
                        IconButton(onClick = { overlay = 1 }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                if (overlay == 0) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = overlay == 0 && currentTab == tab,
                                onClick = { tabIndex = tab.ordinal; overlay = 0 },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }

                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        // Material 的 fade-through：旧页先淡出，新页延后一点淡入，中间留一小段空白，
                        // 这样两页不会同时半透明地叠在一起（叠影会让密集列表看着发糊）。
                        // 横向位移只有 1/14 屏宽，负责给方向感，淡入淡出负责顺滑。
                        val forward = screenOrder(targetState) >= screenOrder(initialState)
                        val direction = if (forward) 1 else -1
                        val exit = fadeOut(tween(150, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(tween(220, easing = FastOutLinearInEasing)) { width ->
                                -width / 14 * direction
                            }
                        val enter = fadeIn(tween(230, delayMillis = 90, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(310, delayMillis = 90, easing = FastOutSlowInEasing)) { width ->
                                width / 14 * direction
                            }
                        enter togetherWith exit
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        is Screen.Tab -> when (target.tab) {
                            MainTab.SKY -> SkyScreen(gnss, settings.units, settings.pointingAxis)
                            MainTab.SIGNAL -> SignalScreen(gnss)
                            MainTab.LOCATION -> LocationScreen(
                                state = gnss,
                                units = settings.units,
                                format = settings.coordinateFormat,
                                datum = settings.geodeticDatum,
                            )
                            MainTab.COMPASS -> CompassScreen(gnss, viewModel.hasCompass)
                            MainTab.STATUS -> StatusScreen(
                                state = gnss,
                                onRequestAgps = viewModel::requestAssistanceData,
                                onClearAgps = viewModel::clearAssistanceData,
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(settings = settings, onChange = viewModel::updateSettings)
                        }
                        Screen.Logs -> {
                            LogScreen(
                                recording = recording,
                                recordingName = recordingName,
                                points = recordingPoints,
                                distanceMeters = recordingDistance,
                                units = settings.units,
                                format = settings.logFormat,
                                files = logFiles,
                                storagePath = viewModel.logDirectoryPath(),
                                onToggleRecording = viewModel::toggleRecording,
                                onDelete = viewModel::deleteLog,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.MyLocation,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_button))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 用于判断换页方向：tab 用枚举序号，二级页排在所有 tab 之后。 */
private fun screenOrder(screen: Screen): Int = when (screen) {
    is Screen.Tab -> screen.tab.ordinal
    Screen.Settings -> 10
    Screen.Logs -> 11
}
