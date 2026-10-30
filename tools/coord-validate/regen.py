#!/usr/bin/env python3
"""把 app 里的坐标代码原样抽成可以在纯 JVM 上跑的 Bench.kt。

只做两件事：
1. 去掉 CoordinateFormat 里对 android R 与 @StringRes 的依赖（枚举项名与逻辑不动）；
2. 追加一个 main，把测试点的格式化结果打成 JSON 行，交给 compare.py 比对。

GeodeticDatum.kt 与 CoordinateFormatter.kt 都是纯 Kotlin，原样复制。
"""
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
SRC = HERE.parents[1] / "app/src/main/java/com/gnsstoolkit/data"
OUT = HERE / "Bench.kt"

POINTS = [
    ("London", 51.5074, -0.1278),
    ("Edinburgh", 55.9533, -3.1883),
    ("Swansea", 51.6214, -3.9436),
    ("Belfast", 54.5973, -5.9301),
    ("Dublin", 53.3498, -6.2603),
    ("Beijing", 39.9087, 116.3975),
    ("Sydney", -33.8688, 151.2093),
    ("NewYork", 40.7128, -74.0060),
    ("Zurich", 47.3769, 8.5417),
    ("NullIsland", 0.0, 0.0),
    ("CapeTown", -33.9249, 18.4241),
    ("Reykjavik", 64.1466, -21.9426),
]

FORMATS = [
    "DMS", "DDM", "DECIMAL", "UTM", "MGRS", "USNG",
    "OSGB_EN", "OSGB_GRID", "MAIDENHEAD", "CH1903", "OSNI_EN", "OSNI_GRID",
]

DATUMS = ["WGS84", "OSGB36_MEAN", "NAD27_CONUS", "ED50_MEAN", "CH1903"]


def strip_string_res(text: str) -> str:
    text = re.sub(r"^import androidx\.annotation\.StringRes\n", "", text, flags=re.M)
    # 枚举构造参数里的注解与取值
    text = re.sub(r"^\s*@param:StringRes val labelRes: Int,\n", "", text, flags=re.M)
    text = re.sub(r"^\s*@param:StringRes val descriptionRes: Int,\n", "", text, flags=re.M)
    # 枚举项：DMS(R.string.format_dms, R.string.format_dms_desc), -> DMS,
    text = re.sub(
        r"^(\s*)([A-Z0-9_]+)\(R\.string\.[a-z0-9_]+, R\.string\.[a-z0-9_]+\)(,?)$",
        r"\1\2\3",
        text,
        flags=re.M,
    )
    # 去掉 labelRes/descriptionRes 的别处引用（当前没有，留作保险）
    if "R.string" in text:
        print("WARN: R.string still referenced in CoordinateFormat", file=sys.stderr)
    return text


def strip_package(text: str, first: bool) -> str:
    """三个源文件各自带 package 声明，拼成一个文件时只留第一处。"""
    if not first:
        text = re.sub(r"^package com\.gnsstoolkit\.data\n+", "", text, count=1, flags=re.M)
    return text


def hoist_imports(chunks: list[str]) -> tuple[list[str], list[str]]:
    """Kotlin 要求 import 集中在文件头，把三份文件的 import 合并去重后提到顶部。"""
    imports: list[str] = []
    seen: set[str] = set()
    bodies: list[str] = []
    for chunk in chunks:
        body = []
        for line in chunk.splitlines():
            if line.startswith("import "):
                if line not in seen:
                    seen.add(line)
                    imports.append(line)
            else:
                body.append(line)
        bodies.append("\n".join(body))
    return imports, bodies


def build() -> str:
    datum = (SRC / "GeodeticDatum.kt").read_text()
    fmt = strip_string_res((SRC / "CoordinateFormat.kt").read_text())
    formatter = (SRC / "CoordinateFormatter.kt").read_text()

    imports, bodies = hoist_imports([
        strip_package(datum, first=False),
        strip_package(fmt, first=False),
        strip_package(formatter, first=False),
    ])

    lines = []
    lines.append("// 由 regen.py 生成，勿手工编辑")
    lines.append("package com.gnsstoolkit.data")
    lines.append("")
    lines.extend(imports)
    lines.append("")
    lines.append(bodies[0])
    lines.append(bodies[1])
    lines.append(bodies[2])
    lines.append("")
    lines.append("fun main() {")
    lines.append("    val points = listOf(")
    for name, lat, lon in POINTS:
        lines.append(f'        Triple("{name}", {lat}, {lon}),')
    lines.append("    )")
    lines.append("    val formats = listOf(")
    for f in FORMATS:
        lines.append(f'        CoordinateFormat.{f},')
    lines.append("    )")
    lines.append("    val datums = listOf(")
    for d in DATUMS:
        lines.append(f'        GeodeticDatum.{d},')
    lines.append("    )")
    lines.append("    for ((name, lat, lon) in points) {")
    lines.append("        for (datum in datums) {")
    lines.append("            for (format in formats) {")
    lines.append("                val reading = runCatching {")
    lines.append("                    CoordinateFormatter.format(lat, lon, datum, format)")
    lines.append("                }.getOrElse { CoordinateReading(listOf(\"ERROR:${it.message}\")) }")
    lines.append("                val body = reading.lines.joinToString(\" | \")")
    lines.append("                val safe = body.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\")")
    lines.append(
        "                println(\"{\\\"point\\\":\\\"$name\\\",\\\"lat\\\":$lat,\\\"lon\\\":$lon,"
        "\\\"datum\\\":\\\"${datum.name}\\\",\\\"format\\\":\\\"${format.name}\\\","
        "\\\"lines\\\":\\\"$safe\\\"}\")"
    )
    lines.append("            }")
    lines.append("        }")
    lines.append("    }")
    lines.append("}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    OUT.write_text(build())
    print(f"wrote {OUT}")
