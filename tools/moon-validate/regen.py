"""从 MoonCalculator.kt 生成 Bench.kt：把算法部分原样抽出来，去掉 Android 依赖。

MoonCalculator.kt 一改就要重跑本脚本，否则 bench 编译的是旧代码。
"""
import pathlib

SRC = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/gnsstoolkit/data/MoonCalculator.kt"
MAIN = '''
/**
 * 纯 JVM 回归程序：与 moon.py 的基准值逐点比对。
 * 由 regen.py 从 MoonCalculator.kt 生成，不要手改。
 */
fun main() {
    data class Case(val iso: String, val elongation: Double, val illumination: Double, val age: Double)
    val cases = listOf(
        Case("2026-10-31T12:00:00Z", 252.281, 65.22, 20.84),
        Case("2026-01-01T00:00:00Z", 146.149, 91.52, 11.93),
        Case("2024-04-08T18:17:00Z", 359.947, 0.00, 29.39),
        Case("2025-03-14T06:58:00Z", 180.019, 100.00, 14.26),
    )
    var worst = 0.0
    for (c in cases) {
        val p = MoonCalculator.phase(java.time.Instant.parse(c.iso))
        val dElong = kotlin.math.abs(p.elongationDegrees - c.elongation)
        val dIllum = kotlin.math.abs(p.illuminatedFraction * 100.0 - c.illumination)
        worst = maxOf(worst, dElong)
        println(
            String.format(
                java.util.Locale.US,
                "%-22s elong=%8.3f (d=%.4f) illum=%6.2f%% (d=%.2f) age=%5.2f d (d=%.2f) %-14s waxing=%b",
                c.iso, p.elongationDegrees, dElong, p.illuminatedFraction * 100.0, dIllum,
                p.ageDays, kotlin.math.abs(p.ageDays - c.age), p.name, p.waxing,
            )
        )
        println("   nextNew=" + p.nextNewMoon + "  nextFull=" + p.nextFullMoon)
    }
    println(String.format(java.util.Locale.US, "worst elongation delta = %.5f deg", worst))
}
'''


def build() -> str:
    src = SRC.read_text()
    src = src.replace("package com.gnsstoolkit.data\n\n", "")
    src = src.replace("import androidx.annotation.StringRes\n", "").replace("import com.gnsstoolkit.R\n", "")
    start = src.index("enum class MoonPhaseName")
    end = src.index("/**\n * 某一时刻的月相。")
    src = (src[:start]
           + "enum class MoonPhaseName { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, "
             "FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }\n\n"
           + src[end:])
    # bench 在 object 之外调用，把字段/函数开成 internal
    src = src.replace("    private fun ", "    internal fun ")
    src = src.replace("    private val ", "    internal val ")
    src = src.replace("    private const val ", "    internal const val ")
    return src + MAIN


if __name__ == "__main__":
    out = pathlib.Path(__file__).with_name("Bench.kt")
    out.write_text(build())
    print("wrote", out)
