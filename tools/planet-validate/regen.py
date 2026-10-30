"""把 Android 侧的 MoonCalculator.kt / PlanetPositions.kt 剥成纯 JVM 源码，拼成 Bench.kt。"""
import pathlib, re

SRC = pathlib.Path("/sd/Ubuntu/workspace/default/gnsstoolkit/app/src/main/java/com/gnsstoolkit/data")
FILES = ["MoonCalculator.kt", "PlanetPositions.kt"]

def strip(text):
    text = re.sub(r"^import androidx\.annotation\.StringRes\n", "", text, flags=re.M)
    text = re.sub(r"^import com\.gnsstoolkit\.R\n", "", text, flags=re.M)
    text = text.replace("@param:StringRes ", "")
    text = re.sub(r"R\.string\.\w+", "0", text)
    return text

bodies = []
imports = set()
for name in FILES:
    text = strip((SRC / name).read_text())
    lines = text.splitlines()
    pkg = [l for l in lines if l.startswith("package ")]
    imp = [l for l in lines if l.startswith("import ")]
    imports.update(imp)
    body = "\n".join(l for l in lines if not l.startswith("package ") and not l.startswith("import "))
    bodies.append(body)

CASES = [
    ("2026-10-31T12:00:00Z", 39.9087, 116.3975),
    ("2026-10-31T12:00:00Z", -33.8688, 151.2093),
    ("2026-07-04T03:30:00Z", 51.4779, -0.0015),
    ("2027-02-19T00:00:00Z", 0.0, 0.0),
    ("2026-01-01T00:00:00Z", 64.1466, -21.9426),
    ("2026-03-20T09:15:00Z", 47.3769, 8.5417),
]

def json(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'

main = r"""
fun main() {
    val cases = listOf(
""" + "\n".join(
    '        Triple("%s", %r, %r),' % (iso, lat, lon) for iso, lat, lon in CASES
) + r"""
    )
    for ((iso, lat, lon) in cases) {
        val instant = java.time.Instant.parse(iso)
        for (body in PlanetPositions.positions(instant, lat, lon)) {
            println("{\"date\":\"$iso\",\"lat\":$lat,\"lon\":$lon,\"body\":\"${body.body.name}\",\"az\":${body.azimuthDegrees},\"alt\":${body.elevationDegrees},\"mag\":${body.magnitude}}")
        }
    }
}
"""

out = ["package com.gnsstoolkit.data", ""]
out += sorted(i for i in imports if not i.startswith("import androidx"))
out += bodies
out.append(main)
pathlib.Path("Bench.kt").write_text("\n".join(out))
print("Bench.kt written")
