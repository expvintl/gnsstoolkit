package com.gnsstoolkit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gnsstoolkit.data.AppSettings
import com.gnsstoolkit.data.GnssEngine
import com.gnsstoolkit.data.GnssState
import com.gnsstoolkit.data.LogFile
import com.gnsstoolkit.data.LogRecorder
import com.gnsstoolkit.data.SettingsRepository
import com.gnsstoolkit.data.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GnssEngine(application)
    private val settingsRepository = SettingsRepository(application)
    private val recorder = LogRecorder(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val gnss: StateFlow<GnssState> =
        combine(engine.state, settings) { state, appSettings ->
            val hidden = appSettings.hiddenConstellations
            if (hidden.isEmpty()) {
                state
            } else {
                state.copy(satellites = state.satellites.filter { it.constellation !in hidden })
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, engine.state.value)

    private val _permissionGranted = MutableStateFlow(engine.hasLocationPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _logFiles = MutableStateFlow<List<LogFile>>(emptyList())
    val logFiles: StateFlow<List<LogFile>> = _logFiles.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _recordingName = MutableStateFlow<String?>(null)
    val recordingName: StateFlow<String?> = _recordingName.asStateFlow()

    private val _recordingPoints = MutableStateFlow(0)
    val recordingPoints: StateFlow<Int> = _recordingPoints.asStateFlow()

    private val _recordingDistance = MutableStateFlow(0.0)
    val recordingDistance: StateFlow<Double> = _recordingDistance.asStateFlow()

    val hasCompass: Boolean = engine.hasCompass

    init {
        viewModelScope.launch {
            gnss.collect { state ->
                if (recorder.isRecording) {
                    recorder.append(state, settings.value.units, settings.value.trackSampling)
                    _recordingName.value = recorder.currentFileName
                    _recordingPoints.value = recorder.currentPoints
                    _recordingDistance.value = recorder.currentDistanceMeters
                }
            }
        }
        refreshLogs()
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) engine.start()
    }

    fun refreshPermission() {
        val granted = engine.hasLocationPermission()
        _permissionGranted.value = granted
        if (granted && !engine.state.value.isRunning) engine.start()
    }

    fun requestAssistanceData() {
        viewModelScope.launch(Dispatchers.IO) { engine.requestAssistanceData() }
    }

    fun clearAssistanceData() {
        viewModelScope.launch(Dispatchers.IO) { engine.clearAssistanceData() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setUnits(units: UnitSystem) = updateSettings { it.copy(units = units) }

    fun toggleRecording() {
        if (recorder.isRecording) {
            recorder.stop()
            _recording.value = false
            _recordingName.value = null
        } else {
            val name = recorder.start(settings.value.logFormat)
            _recording.value = name != null
            _recordingName.value = name
        }
        _recordingPoints.value = 0
        _recordingDistance.value = 0.0
        refreshLogs()
    }

    fun refreshLogs() {
        viewModelScope.launch {
            val files = recorder.list()
            _logFiles.value = files
            if (!recorder.isRecording) {
                _recording.value = false
                _recordingName.value = null
            }
        }
    }

    fun deleteLog(file: LogFile) {
        viewModelScope.launch {
            recorder.delete(file)
            _logFiles.value = recorder.list()
        }
    }

    fun logDirectoryPath(): String = recorder.logsDirectoryPath

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        recorder.release()
    }
}
