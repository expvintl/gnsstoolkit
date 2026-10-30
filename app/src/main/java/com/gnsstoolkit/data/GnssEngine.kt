package com.gnsstoolkit.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssCapabilities as PlatformGnssCapabilities
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executor

/**
 * 封装平台 GNSS 接口：卫星状态、位置更新、NMEA(DOP) 与方向传感器。
 * 回调全部投递到主线程，因此 [state] 的更新天然串行。
 */
class GnssEngine(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GnssState())
    val state: StateFlow<GnssState> = _state.asStateFlow()

    private var running = false
    private var sessionStartElapsed = 0L
    private var statusRegistered = false
    private var nmeaRegistered = false

    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val hasCompass: Boolean = rotationSensor != null

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothedHeading: Float? = null
    private var smoothedCameraAzimuth: Float? = null
    private var smoothedCameraElevation: Float? = null
    private var smoothedTopAzimuth: Float? = null
    private var smoothedTopElevation: Float? = null
    private var magneticDeclination: Float? = null

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running || !hasLocationPermission()) return
        running = true
        sessionStartElapsed = SystemClock.elapsedRealtime()

        _state.update {
            it.copy(
                isRunning = true,
                sessionStartMillis = System.currentTimeMillis(),
                ttffMillis = null,
                capabilities = readCapabilities(),
            )
        }

        requestLocation()
        registerGnssStatus()
        registerNmea()
        registerCompass()
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { locationManager?.removeUpdates(locationListener) }
        unregisterGnssStatus()
        unregisterNmea()
        unregisterCompass()
        _state.update { it.copy(isRunning = false) }
    }

    /** 请求平台重新下载 AGPS 辅助数据，返回是否被系统接受。 */
    fun requestAssistanceData(): Boolean {
        val lm = locationManager ?: return false
        val results = listOf("force_xtra_injection", "force_time_injection").map { command ->
            runCatching { lm.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null) }
                .getOrDefault(false)
        }
        return results.any { it }
    }

    /** 清除平台缓存的 AGPS 辅助数据，下次定位会重新冷启动。 */
    fun clearAssistanceData(): Boolean {
        val lm = locationManager ?: return false
        return runCatching {
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", null)
        }.getOrDefault(false)
    }

    // region 位置

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = publishFix(location)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @Suppress("DEPRECATION")
    private fun requestLocation() {
        val lm = locationManager ?: return
        runCatching {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
        }
    }

    private fun publishFix(location: Location) {
        val fix = Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            mslAltitude = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
                location.mslAltitudeMeters
            } else {
                null
            },
            horizontalAccuracy = if (location.hasAccuracy()) location.accuracy else null,
            verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters
            } else {
                null
            },
            speed = if (location.hasSpeed()) location.speed else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            timeMillis = location.time,
            provider = location.provider ?: LocationManager.GPS_PROVIDER,
            satelliteCount = location.extras?.getInt(SATELLITE_COUNT_KEY) ?: 0,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
        )

        val ttff = _state.value.ttffMillis ?: (SystemClock.elapsedRealtime() - sessionStartElapsed)
        magneticDeclination = runCatching {
            GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                (fix.altitude ?: 0.0).toFloat(),
                location.time,
            ).declination
        }.getOrNull()

        _state.update {
            it.copy(
                fix = fix,
                ttffMillis = ttff,
                magneticDeclination = magneticDeclination,
                trueHeading = normalizeHeading(
                    (smoothedHeading ?: 0f) + (magneticDeclination ?: 0f),
                ),
            )
        }
    }

    // endregion

    // region 卫星

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) = publishStatus(status)
        override fun onStarted() = Unit
        override fun onStopped() = Unit
    }

    private fun publishStatus(status: GnssStatus) {
        val satellites = ArrayList<Satellite>(status.satelliteCount)
        for (index in 0 until status.satelliteCount) {
            val hasCarrier = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                status.hasCarrierFrequencyHz(index)
            satellites += Satellite(
                svid = status.getSvid(index),
                constellation = Constellation.fromGnssType(status.getConstellationType(index)),
                cn0 = status.getCn0DbHz(index),
                azimuth = status.getAzimuthDegrees(index),
                elevation = status.getElevationDegrees(index),
                usedInFix = status.usedInFix(index),
                hasAlmanac = status.hasAlmanacData(index),
                hasEphemeris = status.hasEphemerisData(index),
                carrierHz = if (hasCarrier) status.getCarrierFrequencyHz(index) else null,
                basebandCn0 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && status.hasBasebandCn0DbHz(index)) {
                    status.getBasebandCn0DbHz(index)
                } else {
                    null
                },
            )
        }
        // 厂商 ROM 可能为同一颗卫星按频点重复上报，按 (星座, SVID) 合并成一条，
        // 否则 UI 以 SVID 作列表 key 会直接抛 IllegalArgumentException。
        val fused = LinkedHashMap<Pair<Constellation, Int>, Satellite>(satellites.size)
        for (satellite in satellites) {
            val key = satellite.constellation to satellite.svid
            val prev = fused[key]
            fused[key] = if (prev == null) {
                satellite
            } else {
                prev.copy(
                    cn0 = maxOf(prev.cn0, satellite.cn0),
                    usedInFix = prev.usedInFix || satellite.usedInFix,
                    hasAlmanac = prev.hasAlmanac || satellite.hasAlmanac,
                    hasEphemeris = prev.hasEphemeris || satellite.hasEphemeris,
                    carrierHz = prev.carrierHz ?: satellite.carrierHz,
                    basebandCn0 = listOfNotNull(prev.basebandCn0, satellite.basebandCn0).maxOrNull(),
                )
            }
        }
        _state.update { it.copy(satellites = fused.values.toList()) }
    }

    private fun registerGnssStatus() {
        val lm = locationManager ?: return
        if (statusRegistered) return
        statusRegistered = true
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssStatusCallback(mainExecutor, gnssStatusCallback)
            } else {
                @Suppress("DEPRECATION")
                lm.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
            }
        }
    }

    private fun unregisterGnssStatus() {
        if (!statusRegistered) return
        statusRegistered = false
        runCatching { locationManager?.unregisterGnssStatusCallback(gnssStatusCallback) }
    }

    // endregion

    // region NMEA / DOP

    private val nmeaListener = OnNmeaMessageListener { message, _ -> handleNmea(message) }

    private fun handleNmea(message: String) {
        _state.update { it.copy(nmeaLineCount = it.nmeaLineCount + 1) }
        // GSA 提供 PDOP/HDOP/VDOP，Android 未暴露 DOP 的其它途径
        val sentence = message.trim()
        if (!sentence.contains("GSA")) return
        val body = sentence.substringBefore('*')
        val parts = body.split(',')
        if (parts.size < 18) return
        val fixType = parts[2].toIntOrNull() ?: return
        if (fixType == 1) {
            _state.update { it.copy(dops = Dops()) }
            return
        }
        val pdop = parts[15].toFloatOrNull()
        val hdop = parts[16].toFloatOrNull()
        val vdop = parts[17].toFloatOrNull()
        if (pdop == null && hdop == null && vdop == null) return
        _state.update { it.copy(dops = Dops(pdop = pdop, hdop = hdop, vdop = vdop)) }
    }

    private fun registerNmea() {
        val lm = locationManager ?: return
        if (nmeaRegistered) return
        nmeaRegistered = true
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.addNmeaListener(mainExecutor, nmeaListener)
            } else {
                @Suppress("DEPRECATION")
                lm.addNmeaListener(nmeaListener, mainHandler)
            }
        }
    }

    private fun unregisterNmea() {
        if (!nmeaRegistered) return
        nmeaRegistered = false
        runCatching { locationManager?.removeNmeaListener(nmeaListener) }
    }

    // endregion

    // region 方向传感器

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val heading = normalizeHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
            smoothedHeading = smoothHeading(smoothedHeading, heading)
            val magnetic = smoothedHeading ?: heading
            // R 把设备坐标旋到「东-北-天」世界坐标，故世界坐标下的相机轴向 = R * (0,0,-1)
            // = -R 的第三列 = (-R[2], -R[5], -R[8])。
            val east = -rotationMatrix[2]
            val north = -rotationMatrix[5]
            val up = -rotationMatrix[8]
            // 顶部边缘是设备 +Y 轴，世界方向 = R 的第二列 = (R[1], R[4], R[7])。
            val topEast = rotationMatrix[1]
            val topNorth = rotationMatrix[4]
            val topUp = rotationMatrix[7]
            val cameraElevation = Math.toDegrees(Math.asin(up.coerceIn(-1f, 1f).toDouble())).toFloat()
            val cameraAzimuth = normalizeHeading(Math.toDegrees(Math.atan2(east.toDouble(), north.toDouble())).toFloat())
            val topElevation = Math.toDegrees(Math.asin(topUp.coerceIn(-1f, 1f).toDouble())).toFloat()
            val topAzimuth = normalizeHeading(Math.toDegrees(Math.atan2(topEast.toDouble(), topNorth.toDouble())).toFloat())
            // 与朝向同样做一阶低通，否则准星会随手抖跳动。
            smoothedCameraAzimuth = smoothIfClose(smoothedCameraAzimuth, cameraAzimuth)
            smoothedCameraElevation = smoothPointingElevation(smoothedCameraElevation, cameraElevation)
            smoothedTopAzimuth = smoothIfClose(smoothedTopAzimuth, topAzimuth)
            smoothedTopElevation = smoothPointingElevation(smoothedTopElevation, topElevation)
            val declination = magneticDeclination ?: 0f
            _state.update {
                it.copy(
                    magneticHeading = magnetic,
                    trueHeading = normalizeHeading(magnetic + declination),
                    cameraAzimuth = normalizeHeading((smoothedCameraAzimuth ?: cameraAzimuth) + declination),
                    cameraElevation = smoothedCameraElevation ?: cameraElevation,
                    topAzimuth = normalizeHeading((smoothedTopAzimuth ?: topAzimuth) + declination),
                    topElevation = smoothedTopElevation ?: topElevation,
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
            _state.update { it.copy(compassAccuracy = accuracy) }
        }
    }

    private fun registerCompass() {
        val sensor = rotationSensor ?: return
        runCatching {
            sensorManager?.registerListener(
                sensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_UI,
                mainHandler,
            )
        }
    }

    private fun unregisterCompass() {
        runCatching { sensorManager?.unregisterListener(sensorListener) }
    }

    // endregion

    private fun readCapabilities(): GnssCapabilities? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val lm = locationManager ?: return null
        val caps: PlatformGnssCapabilities = runCatching { lm.gnssCapabilities }.getOrNull() ?: return null
        val signalTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                caps.gnssSignalTypes.orEmpty().map { signal ->
                    val type = Constellation.fromGnssType(signal.constellationType)
                    val hz = signal.carrierFrequencyHz.toFloat()
                    SignalTypeInfo(type, bandName(type, hz), hz / 1_000_000f)
                }.distinctBy { it.constellation to it.frequencyMhz }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return GnssCapabilities(
            measurements = caps.hasMeasurements(),
            navigationMessages = caps.hasNavigationMessages(),
            lowPowerMode = caps.hasLowPowerMode(),
            satelliteBlocklist = caps.hasSatelliteBlocklist(),
            satellitePvt = caps.hasSatellitePvt(),
            measurementCorrections = caps.hasMeasurementCorrections(),
            antennaInfo = caps.hasGnssAntennaInfo(),
            accumulatedDeltaRange = caps.hasAccumulatedDeltaRange() != 0,
            msb = caps.hasMsb(),
            scheduling = caps.hasScheduling(),
            geofencing = caps.hasGeofencing(),
            singleShotFix = caps.hasSingleShotFix(),
            signalTypes = signalTypes,
        )
    }

    private fun normalizeHeading(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /**
     * 指向方位角的平滑。手机从「竖着」转到「平躺」时方位角会在 0/360 之间跳变，
     * 此时点积为负，不做插值直接跳到新值，避免准星绕着圆心甩一圈。
     */
    private fun smoothIfClose(previous: Float?, next: Float, factor: Float = 0.25f): Float {
        if (previous == null) return next
        var delta = next - previous
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        if (Math.abs(delta) > 60f) return next
        return normalizeHeading(previous + delta * factor)
    }

    private fun smoothPointingElevation(previous: Float?, next: Float, factor: Float = 0.25f): Float {
        if (previous == null) return next
        if (Math.abs(next - previous) > 40f) return next
        return previous + (next - previous) * factor
    }

    private fun smoothHeading(previous: Float?, next: Float, factor: Float = 0.2f): Float {
        if (previous == null) return next
        var delta = next - previous
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return normalizeHeading(previous + delta * factor)
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1000L
        const val SATELLITE_COUNT_KEY = "satellites"
    }
}
