import com.gnsstoolkit.data.LightPeriod
import com.gnsstoolkit.data.SolarCalculator
import com.gnsstoolkit.data.SolarEvents
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

private fun pair(bands: List<com.gnsstoolkit.data.LightBand>, p: LightPeriod): String {
    val b = bands.firstOrNull { it.period == p } ?: return "[]"
    return b.intervals.joinToString(",", "[", "]") { "[" + t(it.start) + "," + t(it.end) + "]" }
}

private fun t(value: LocalTime?): String =
    value?.let { String.format(Locale.US, "\"%02d:%02d\"", it.hour, it.minute) } ?: "null"

fun main() {
    val places = listOf(
        doubleArrayOf(39.9087, 116.3975) to "Asia/Shanghai",
        doubleArrayOf(51.5074, -0.1278) to "Europe/London",
        doubleArrayOf(-33.8688, 151.2093) to "Australia/Sydney",
        doubleArrayOf(69.6492, 18.9553) to "Europe/Oslo",
        doubleArrayOf(64.1466, -21.9426) to "Atlantic/Reykjavik",
        doubleArrayOf(1.3521, 103.8198) to "Asia/Singapore",
        doubleArrayOf(47.3769, 8.5417) to "Europe/Zurich",
        doubleArrayOf(-77.85, 166.67) to "Antarctica/McMurdo",
        doubleArrayOf(0.0, 0.0) to "UTC",
        doubleArrayOf(-23.5505, -46.6333) to "America/Sao_Paulo",
    )
    val dates = listOf("2026-03-20", "2026-06-21", "2026-09-23", "2026-12-21", "2026-10-31")
    for ((coord, zoneId) in places) {
        val zone = ZoneId.of(zoneId)
        for (dateText in dates) {
            val e = SolarCalculator.events(LocalDate.parse(dateText), coord[0], coord[1], zone)
            println(
                """{"lat":${coord[0]},"lon":${coord[1]},"zone":"$zoneId","date":"$dateText",""" +
                    """"sunrise":${t(e.sunrise)},"sunset":${t(e.sunset)},""" +
                    """"mb0":${t(e.morningBlueHour?.start)},"mb1":${t(e.morningBlueHour?.end)},""" +
                    """"eb0":${t(e.eveningBlueHour?.start)},"eb1":${t(e.eveningBlueHour?.end)},""" +
                    """"golden":${pair(e.lightBands, LightPeriod.GOLDEN)},""" +
                    """"civil":${pair(e.lightBands, LightPeriod.CIVIL)},""" +
                    """"nautical":${pair(e.lightBands, LightPeriod.NAUTICAL)},""" +
                    """"astro":${pair(e.lightBands, LightPeriod.ASTRONOMICAL)}}"""
            )
        }
    }
}
