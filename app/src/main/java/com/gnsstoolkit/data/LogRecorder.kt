package com.gnsstoolkit.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LogFile(
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
) {
    val name: String get() = file.name
    val format: LogFormat get() = LogFormat.fromFileName(name) ?: LogFormat.CSV
    val mimeType: String get() = format.mimeType
}

/**
 * 把 GNSS 状态按秒记成轨迹文件，存到应用内部存储的 files/logs/。
 *
 * 每次 start 建一个独立 Channel + 单个消费协程：先写文件头（GPX/KML 的 XML 声明与轨道起始标签、
 * CSV 的表头），停止时再入队文件尾（闭合标签），保证产出的是结构完整的可解析文件。
 * 写盘全部在 IO 线程串行完成，接口对调用方非阻塞。
 */
class LogRecorder(context: Context) {

    private val logsDir = File(context.applicationContext.filesDir, "logs")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前录制会话的行队列；null 元素 = 关闭文件。每次录制独立一个，避免 start/stop 交错串台。 */
    private var queue: Channel<String?>? = null
    private var consumer: Job? = null

    private var currentFile: File? = null
    private var currentFormat: LogFormat = LogFormat.GPX
    private var pointCount = 0

    /** 上一次处理过的定位标记，用来把"状态变了"和"定位更新了"区分开。 */
    private var lastFixStamp = Long.MIN_VALUE
    /** 上一个真正写进文件的点；采样判断与里程累计都以它为基准。 */
    private var anchor: Fix? = null
    /** 本次录制累计的轨迹长度（米），只统计已落盘的点。 */
    private var distanceMeters = 0.0

    // KML 的 <coordinates> 必须整体闭合后才能写出，因此按块缓冲：
    // 满 10000 点（约 2.8 小时）封一个 Placemark，兼顾内存占用与通用查看器的兼容性。
    private val kmlCoords = StringBuilder()
    private var kmlChunkPoints = 0
    private var kmlChunkBegin: String? = null
    private var kmlChunkEnd: String? = null
    private var kmlChunk = 0

    val logsDirectoryPath: String get() = logsDir.absolutePath
    val isRecording: Boolean get() = currentFile != null
    val currentFileName: String? get() = currentFile?.name
    val currentPoints: Int get() = pointCount
    val currentLines: Int get() = pointCount
    val currentDistanceMeters: Double get() = distanceMeters

    fun start(format: LogFormat): String? {
        if (currentFile != null) return currentFile?.name
        logsDir.mkdirs()
        val now = Instant.now()
        val file = File(logsDir, "gnss-${FILE_STAMP.format(now)}.${format.extension}")
        val channel = Channel<String?>(Channel.UNLIMITED)
        queue = channel
        currentFile = file
        currentFormat = format
        pointCount = 0
        lastFixStamp = Long.MIN_VALUE
        anchor = null
        distanceMeters = 0.0
        kmlCoords.setLength(0)
        kmlChunkPoints = 0
        kmlChunkBegin = null
        kmlChunkEnd = null
        kmlChunk = 0
        channel.trySend(header(format, now))
        consumer = scope.launch {
            try {
                BufferedWriter(FileWriter(file, false)).use { writer ->
                    var pending = 0
                    for (line in channel) {
                        if (line == null) break
                        writer.write(line)
                        writer.write("\n")
                        // 每 4 条刷一次：轨迹文件怕丢数据，但逐条 flush 又太浪费。
                        if (++pending >= 4) {
                            writer.flush()
                            pending = 0
                        }
                    }
                    writer.flush()
                }
            } catch (_: Exception) {
                // 写盘失败时静默退出，避免影响主流程
            }
        }
        return file.name
    }

    fun stop() {
        val channel = queue ?: return
        queue = null
        currentFile = null
        anchor = null
        lastFixStamp = Long.MIN_VALUE
        if (currentFormat == LogFormat.KML) drainKmlSegment()?.let { channel.trySend(it) }
        footer(currentFormat)?.let { channel.trySend(it) }
        channel.trySend(null)
    }

    /** 记录一个轨迹点；未录制、没有定位结果或未通过采样判断时不做任何事。 */
    fun append(state: GnssState, units: UnitSystem, sampling: TrackSampling) {
        val channel = queue ?: return
        val fix = state.fix ?: return
        // 主 StateFlow 同时被定位、卫星状态、NMEA 和罗盘传感器驱动，一次定位内会更新几十次。
        // 不按定位时间戳去重的话，写盘频率会变成传感器刷新率（十几到几十 Hz），每秒吃掉几十 KB。
        val stamp = if (fix.elapsedRealtimeNanos != 0L) fix.elapsedRealtimeNanos else fix.timeMillis
        if (stamp == lastFixStamp) return
        lastFixStamp = stamp
        if (!accept(fix, sampling)) return
        pointCount++
        if (currentFormat == LogFormat.KML) {
            val whenText = ISO_UTC.format(Instant.ofEpochMilli(fix.timeMillis))
            if (kmlChunkBegin == null) kmlChunkBegin = whenText
            kmlChunkEnd = whenText
            if (kmlChunkPoints > 0) kmlCoords.append(' ')
            kmlCoords.append(f(fix.longitude, "%.7f")).append(',')
                .append(f(fix.latitude, "%.7f")).append(',')
                .append(f(fix.mslAltitude ?: fix.altitude ?: 0.0, "%.1f"))
            kmlChunkPoints++
            if (kmlChunkPoints >= KML_CHUNK) drainKmlSegment()?.let { channel.trySend(it) }
        } else {
            channel.trySend(renderPoint(currentFormat, state, fix, units))
        }
    }

    /**
     * 采样与质量判断：过差的定位直接丢、物理上不可能的跳变丢、其余按 [sampling] 抽稀。
     * 通过时同步累计里程并把当前点设为新的基准点。
     */
    private fun accept(fix: Fix, sampling: TrackSampling): Boolean {
        val accuracy = fix.horizontalAccuracy
        if (accuracy != null && accuracy > MAX_ACCURACY_M) return false
        val prev = anchor
        if (prev == null) {
            anchor = fix
            return true
        }
        val dt = (fix.timeMillis - prev.timeMillis) / 1000.0
        if (dt <= 0.0) return false
        val moved = distanceBetween(prev, fix)
        if (moved / dt > MAX_SPEED_MPS) {
            // 超过 360 km/h 的位移只可能是定位跳变（室内重捕、多径），丢掉并让下一点重新起步。
            anchor = null
            return false
        }
        val interval = sampling.fixedIntervalSeconds
        val keep = if (interval != null) {
            // 固定节奏，同时保留一个位移兜底：长时间没落点时别把整段直线压成一个点。
            dt >= interval || moved >= FIXED_MOVE_BACKSTOP_M
        } else {
            adaptive(
                moved = moved,
                dt = dt,
                noise = maxOf(accuracy ?: 0f, prev.horizontalAccuracy ?: 0f).toDouble(),
                gpsSpeed = fix.speed,
            )
        }
        if (keep) {
            if (isMoving(fix, moved, dt)) distanceMeters += moved
            anchor = fix
        }
        return keep
    }

    /**
     * 自适应抽稀：静止时几乎不写点，速度越快采样越密。
     *
     * 静止判定优先用 GPS 多普勒速度而不是位置差分 —— 位置噪声能轻易让静止的手机
     * "漂"出十几米，但多普勒速度会稳定停在 0 附近。
     */
    private fun adaptive(moved: Double, dt: Double, noise: Double, gpsSpeed: Float?): Boolean {
        if (gpsSpeed != null && gpsSpeed < MIN_MOVE_SPEED_MPS) return dt >= IDLE_GAP_S
        val speed = moved / dt
        if (speed >= FAST_SPEED_MPS) return dt >= FAST_INTERVAL_S || moved >= FAST_MOVE_M
        val step = maxOf(MIN_MOVE_M, noise)
        if (moved >= step * 2) return true
        if (dt >= IDLE_GAP_S) return true
        return moved >= step && dt >= 3.0
    }

    /** 里程累计的判据：同样以 GPS 速度为准，避免静止时的位置噪声算进里程。 */
    private fun isMoving(fix: Fix, moved: Double, dt: Double): Boolean {
        val gpsSpeed = fix.speed
        return if (gpsSpeed != null) {
            gpsSpeed >= MIN_MOVE_SPEED_MPS
        } else {
            moved / dt >= MIN_MOVE_SPEED_MPS
        }
    }

    /** 球面距离（haversine），单位米。 */
    private fun distanceBetween(a: Fix, b: Fix): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    suspend fun list(): List<LogFile> = withContext(Dispatchers.IO) {
        logsDir.listFiles()
            ?.filter { it.isFile && LogFormat.fromFileName(it.name) != null }
            ?.map { LogFile(it, it.lastModified(), it.length()) }
            ?.sortedByDescending { it.createdAt }
            .orEmpty()
    }

    suspend fun delete(file: LogFile) = withContext(Dispatchers.IO) {
        if (file.file.absolutePath != currentFile?.absolutePath) file.file.delete()
    }

    /** 停止录制并等待文件尾落盘，否则进程退出时 GPX/KML 会缺闭合标签。 */
    fun release() {
        stop()
        val job = consumer
        if (job != null) runBlocking { withTimeoutOrNull(1500) { job.join() } }
        consumer = null
        scope.cancel()
    }

    // ---------------------------------------------------------------- 渲染

    private fun header(format: LogFormat, now: Instant): String = when (format) {
        LogFormat.GPX -> buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"GNSS Toolkit\"")
            append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
            append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
            append(" xmlns:gnt=\"https://gnsstoolkit.app/xmlschemas/gpxext/1\"")
            append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1")
            append(" http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
            append("  <metadata><name>GNSS Toolkit ${FILE_STAMP.format(now)}</name>")
            append("<time>${ISO_UTC.format(now)}</time></metadata>\n")
            append("  <trk><name>GNSS Toolkit track</name><trkseg>")
        }

        LogFormat.KML -> buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
            append("  <Document><name>GNSS Toolkit ${FILE_STAMP.format(now)}</name>\n")
            append("    <Style id=\"track\"><LineStyle><color>ffa8a500</color>")
            append("<width>3</width></LineStyle></Style>\n")
            append("    <!-- one LineString per 10000 points -->\n")
        }

        LogFormat.CSV -> CSV_HEADER
    }

    private fun footer(format: LogFormat): String? = when (format) {
        LogFormat.GPX -> "\n  </trkseg></trk>\n</gpx>"
        LogFormat.KML -> "\n  </Document>\n</kml>"
        LogFormat.CSV -> null
    }

    private fun renderPoint(
        format: LogFormat,
        state: GnssState,
        fix: Fix,
        units: UnitSystem,
    ): String = when (format) {
        LogFormat.GPX -> renderGpx(state, fix)
        LogFormat.CSV -> renderCsv(state, fix, units)
        LogFormat.KML -> error("KML 轨迹点按块封装，不逐点渲染")
    }

    /** GPX 1.1 的 trkpt 子元素有固定顺序：ele, time, ..., fix, sat, hdop, vdop, pdop, extensions。 */
    private fun renderGpx(state: GnssState, fix: Fix): String = buildString {
        append("<trkpt lat=\"").append(f(fix.latitude, "%.7f"))
            .append("\" lon=\"").append(f(fix.longitude, "%.7f")).append("\">")
        val elevation = fix.mslAltitude ?: fix.altitude
        if (elevation != null) append("<ele>").append(f(elevation, "%.1f")).append("</ele>")
        append("<time>").append(ISO_UTC.format(Instant.ofEpochMilli(fix.timeMillis))).append("</time>")
        append("<fix>").append(if (elevation != null) "3d" else "2d").append("</fix>")
        append("<sat>").append(state.usedCount).append("</sat>")
        state.dops.hdop?.let { append("<hdop>").append(f(it, "%.2f")).append("</hdop>") }
        state.dops.vdop?.let { append("<vdop>").append(f(it, "%.2f")).append("</vdop>") }
        state.dops.pdop?.let { append("<pdop>").append(f(it, "%.2f")).append("</pdop>") }
        append("<extensions>")
        fix.speed?.let { append("<gnt:speed>").append(f(it, "%.2f")).append("</gnt:speed>") }
        fix.bearing?.let { append("<gnt:course>").append(f(it, "%.1f")).append("</gnt:course>") }
        fix.horizontalAccuracy?.let {
            append("<gnt:hacc>").append(f(it, "%.1f")).append("</gnt:hacc>")
        }
        fix.verticalAccuracy?.let {
            append("<gnt:vacc>").append(f(it, "%.1f")).append("</gnt:vacc>")
        }
        append("<gnt:meanCn0>").append(f(state.meanCn0, "%.1f")).append("</gnt:meanCn0>")
        append("<gnt:satsUsed>").append(state.usedCount).append("</gnt:satsUsed>")
        append("<gnt:satsVisible>").append(state.visibleCount).append("</gnt:satsVisible>")
        append("</extensions></trkpt>")
    }

    /**
     * 把一个缓冲块封成 Placemark + LineString；空块返回 null。
     *
     * 用 LineString 而不是 Google 私有的 gx:Track：后者能逐点带时间戳，但大量
     * 查看器（含不少国产地图与 QGIS）会直接报"元素不受支持"。轨迹的可打开性优先，
     * 逐点时间序列交给 GPX。
     */
    private fun drainKmlSegment(): String? {
        if (kmlChunkPoints == 0) return null
        val coords = kmlCoords.toString()
        val begin = kmlChunkBegin
        val end = kmlChunkEnd
        kmlCoords.setLength(0)
        kmlChunkPoints = 0
        kmlChunkBegin = null
        kmlChunkEnd = null
        kmlChunk++
        return buildString {
            append("    <Placemark>\n")
            append("      <name>Track ").append(kmlChunk).append("</name>\n")
            // KML 的元素顺序由 schema 的 sequence 强制：TimeSpan 必须排在 styleUrl 之前。
            if (begin != null && end != null) {
                append("      <TimeSpan><begin>").append(begin)
                append("</begin><end>").append(end).append("</end></TimeSpan>\n")
            }
            append("      <styleUrl>#track</styleUrl>\n")
            append("      <LineString>\n")
            append("        <altitudeMode>absolute</altitudeMode>\n")
            append("        <coordinates>").append(coords).append("</coordinates>\n")
            append("      </LineString>\n")
            append("    </Placemark>")
        }
    }

    private fun renderCsv(state: GnssState, fix: Fix, units: UnitSystem): String = buildString {
        append(Instant.ofEpochMilli(fix.timeMillis).toString()).append(',')
        append(f(fix.latitude, "%.7f")).append(',')
        append(f(fix.longitude, "%.7f")).append(',')
        append(f(fix.altitude, "%.1f", "")).append(',')
        append(f(fix.horizontalAccuracy, "%.1f", "")).append(',')
        append(f(fix.verticalAccuracy, "%.1f", "")).append(',')
        append(f(fix.speed, "%.2f", "")).append(',')
        append(f(fix.bearing, "%.1f", "")).append(',')
        append(f(state.dops.hdop, "%.2f", "")).append(',')
        append(f(state.dops.pdop, "%.2f", "")).append(',')
        append(f(state.dops.vdop, "%.2f", "")).append(',')
        append(state.visibleCount).append(',')
        append(state.usedCount).append(',')
        append(f(state.meanCn0, "%.1f")).append(',')
        append(units.name)
    }

    private fun f(value: Float, pattern: String): String = String.format(Locale.US, pattern, value)

    private fun f(value: Double, pattern: String): String = String.format(Locale.US, pattern, value)

    /** 可选值；缺省时输出 fallback（CSV 里是空字段）。 */
    private fun f(value: Float?, pattern: String, fallback: String): String =
        value?.let { String.format(Locale.US, pattern, it) } ?: fallback

    private fun f(value: Double?, pattern: String, fallback: String): String =
        value?.let { String.format(Locale.US, pattern, it) } ?: fallback

    companion object {
        private val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        private val ISO_UTC: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        private const val KML_CHUNK = 10_000

        /** 水平精度差于 50 m 的定位不写进轨迹：室内重捕、多径时常给出几十上百米的漂移。 */
        private const val MAX_ACCURACY_M = 50f
        /** 相邻两点的位移速度上限，超过只可能是跳变（360 km/h，高铁和民航都在其下）。 */
        private const val MAX_SPEED_MPS = 100.0
        /** 判定"在移动"的速度门限，也用于静止时跳过里程累计。 */
        private const val MIN_MOVE_SPEED_MPS = 0.5f
        /** 自适应模式下判定"真的移动了"的最小位移。 */
        private const val MIN_MOVE_M = 5.0
        /** 静止时多久留一个时间锚点。 */
        private const val IDLE_GAP_S = 30.0
        /** 高速（>54 km/h）时的采样间隔与位移兜底。 */
        private const val FAST_SPEED_MPS = 15.0
        private const val FAST_INTERVAL_S = 2.0
        private const val FAST_MOVE_M = 50.0
        /** 固定间隔模式下的位移兜底，避免一段长直线被压成一个点。 */
        private const val FIXED_MOVE_BACKSTOP_M = 200.0
        private const val EARTH_RADIUS_M = 6_371_008.8

        private const val CSV_HEADER =
            "utc,latitude,longitude,altitude_m,h_accuracy_m,v_accuracy_m,speed_mps,bearing_deg," +
                "hdop,pdop,vdop,visible,used,mean_cn0_db_hz,units"
    }
}
