import com.gnsstoolkit.data.SunCalculator
import java.time.Instant

private val CASES = listOf(
    "2026-03-20T14:46:00Z", "2026-06-21T08:25:00Z", "2026-09-23T00:05:00Z",
    "2026-12-21T20:50:00Z", "2026-10-31T12:00:00Z", "2026-10-31T04:00:00Z",
)

fun main() {
    for (text in CASES) {
        val sun = SunCalculator.position(Instant.parse(text))
        println("%s %.4f %.4f".format(text, sun.declinationDegrees, sun.subsolarLongitudeDegrees))
    }

    // 高度角网格，用来跟 Python 侧逐点比对
    val sun = SunCalculator.position(Instant.parse("2026-10-31T04:00:00Z"))
    for (lat in -80..80 step 20) {
        for (lon in -180..180 step 30) {
            val e = SunCalculator.elevationDegrees(lat.toDouble(), lon.toDouble(), sun)
            println("GRID $lat $lon %.4f".format(e))
        }
    }

    // 晨昏线：把每列求出的边界纬度回代，高度角应该 ≈ 0
    var worst = 0.0
    for (decl in listOf(-23.44, -10.0, -0.5, 0.0, 0.5, 10.0, 23.44)) {
        for (delta in -180..180 step 5) {
            val band = SunCalculator.nightBand(delta.toDouble(), decl) ?: continue
            val probe = SunCalculator.position(Instant.parse("2026-10-31T04:00:00Z"))
            for (edge in listOf(band.start, band.endInclusive)) {
                if (edge <= -89.999 || edge >= 89.999) continue
                val lat = Math.toRadians(edge)
                val d = Math.toRadians(decl)
                val h = Math.toRadians(delta.toDouble() - 0.0)
                val sinE = Math.sin(lat) * Math.sin(d) + Math.cos(lat) * Math.cos(d) * Math.cos(h)
                worst = maxOf(worst, Math.abs(Math.toDegrees(Math.asin(sinE.coerceIn(-1.0, 1.0)))))
            }
            check(probe.declinationDegrees.isFinite())
        }
    }
    println("TERMINATOR worst residual degrees = %.6f".format(worst))
}
