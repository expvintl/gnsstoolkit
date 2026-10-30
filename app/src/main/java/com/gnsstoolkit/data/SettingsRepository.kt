package com.gnsstoolkit.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val hiddenConstellations: Set<Constellation> = emptySet(),
    val keepScreenOn: Boolean = true,
    /** BCP-47 语言标签；空串表示跟随系统。 */
    val language: String = "",
    /** 轨迹记录的输出格式。 */
    val logFormat: LogFormat = LogFormat.GPX,
    val trackSampling: TrackSampling = TrackSampling.ADAPTIVE,
    /** 天空图准星跟随手机的哪一条轴。 */
    val pointingAxis: PointingAxis = PointingAxis.default,
    /** 位置页的坐标显示格式。 */
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DMS,
    /** 坐标换算用的大地基准；OSGB/OSNI/CH1903 三种格式会强制使用各自的基准。 */
    val geodeticDatum: GeodeticDatum = GeodeticDatum.WGS84,
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private object Keys {
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val amoled = booleanPreferencesKey("amoled")
    val units = stringPreferencesKey("units")
    val hiddenConstellations = stringSetPreferencesKey("hidden_constellations")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    val language = stringPreferencesKey("language")
    val logFormat = stringPreferencesKey("log_format")
    val trackSampling = stringPreferencesKey("track_sampling")
    val pointingAxis = stringPreferencesKey("pointing_axis")
    val coordinateFormat = stringPreferencesKey("coordinate_format")
    val geodeticDatum = stringPreferencesKey("geodetic_datum")
}

private fun Preferences.toSettings(): AppSettings = AppSettings(
    themeMode = this[Keys.themeMode]?.let { name ->
        ThemeMode.entries.firstOrNull { it.name == name }
    } ?: ThemeMode.SYSTEM,
    dynamicColor = this[Keys.dynamicColor] ?: true,
    amoled = this[Keys.amoled] ?: false,
    units = this[Keys.units]?.let { name ->
        UnitSystem.entries.firstOrNull { it.name == name }
    } ?: UnitSystem.METRIC,
    hiddenConstellations = this[Keys.hiddenConstellations]
        .orEmpty()
        .mapNotNull { name -> Constellation.entries.firstOrNull { it.name == name } }
        .toSet(),
    keepScreenOn = this[Keys.keepScreenOn] ?: true,
    language = this[Keys.language] ?: "",
    logFormat = LogFormat.fromName(this[Keys.logFormat]),
    trackSampling = TrackSampling.fromName(this[Keys.trackSampling]),
    pointingAxis = PointingAxis.fromName(this[Keys.pointingAxis]),
    coordinateFormat = CoordinateFormat.fromName(this[Keys.coordinateFormat]),
    geodeticDatum = GeodeticDatum.fromName(this[Keys.geodeticDatum]),
)

private fun MutablePreferences.writeSettings(settings: AppSettings) {
    this[Keys.themeMode] = settings.themeMode.name
    this[Keys.dynamicColor] = settings.dynamicColor
    this[Keys.amoled] = settings.amoled
    this[Keys.units] = settings.units.name
    this[Keys.hiddenConstellations] = settings.hiddenConstellations.map { it.name }.toSet()
    this[Keys.keepScreenOn] = settings.keepScreenOn
    this[Keys.language] = settings.language
    this[Keys.logFormat] = settings.logFormat.name
    this[Keys.trackSampling] = settings.trackSampling.name
    this[Keys.pointingAxis] = settings.pointingAxis.name
    this[Keys.coordinateFormat] = settings.coordinateFormat.name
    this[Keys.geodeticDatum] = settings.geodeticDatum.name
}

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    /** 启动时同步读一次语言，供 attachBaseContext 使用。 */
    suspend fun currentLanguage(): String = settings.first().language

    /** 读取-修改-写回在 DataStore 的原子事务里完成。 */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            prefs.writeSettings(transform(prefs.toSettings()))
        }
    }
}
