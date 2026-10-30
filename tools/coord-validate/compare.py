#!/usr/bin/env python3
"""拿 pyproj / pygeodesy 当真值，逐条核对 Bench.kt 输出的坐标格式。

地面真值的来源都是独立实现：pyproj(PROJ 9.8.1) 负责投影与基准换算，
pygeodesy 负责 OSGB 网格参考号与 MGRS。只要有一处偏差超出容差就打印出来。
"""
import json
import pathlib
import re
import sys
import warnings

warnings.filterwarnings("ignore")
sys.path.insert(0, "/sd/Ubuntu/workspace/default/.toolchain/pylibs")

from pyproj import CRS, Transformer  # noqa: E402
from pygeodesy.osgr import toOsgr  # noqa: E402
from pygeodesy.utm import toUtm8  # noqa: E402
from pygeodesy.mgrs import toMgrs  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
BENCH = HERE / "bench-out.jsonl"

# 与 GeodeticDatum.kt 保持一致的参数（来自 EPSG）
DATUMS = {
    "WGS84": None,
    "OSGB36_MEAN": (6377563.396, 299.3249646, 446.448, -125.157, 542.060, 0.15, 0.247, 0.842, -20.489),
    "NAD27_CONUS": (6378206.4, 294.978698214, -10.0, 158.0, 187.0, 0, 0, 0, 0),
    "ED50_MEAN": (6378388.0, 297.0, -87.0, -98.0, -121.0, 0, 0, 0, 0),
    "CH1903": (6377397.155, 299.1528128, 674.374, 15.056, 405.346, 0, 0, 0, 0),
}


def datum_crs(name: str) -> CRS:
    params = DATUMS[name]
    if params is None:
        return CRS.from_epsg(4326)
    a, rf, tx, ty, tz, rx, ry, rz, s = params
    return CRS.from_proj4(
        f"+proj=longlat +a={a} +rf={rf} "
        f"+towgs84={tx},{ty},{tz},{rx},{ry},{rz},{s} +no_defs +type=crs"
    )


def to_local(lat: float, lon: float, name: str) -> tuple[float, float]:
    if name == "WGS84":
        return lat, lon
    t = Transformer.from_crs(CRS.from_epsg(4326), datum_crs(name), always_xy=True)
    x, y = t.transform(lon, lat)
    return y, x


def utm_expected(lat: float, lon: float, name: str) -> tuple[int, int, int]:
    """返回 zone、easting、northing。"""
    zone = max(1, min(60, int((lon + 180) / 6) + 1))
    lon0 = -183 + zone * 6
    local_lat, local_lon = to_local(lat, lon, name)
    params = DATUMS[name]
    if params is None:
        a, rf = 6378137.0, 298.257223563
        towgs = ""
    else:
        a, rf, tx, ty, tz, rx, ry, rz, s = params
        towgs = f"+towgs84={tx},{ty},{tz},{rx},{ry},{rz},{s} "
    y0 = 0 if lat >= 0 else 10000000
    proj = (
        f"+proj=tmerc +lat_0=0 +lon_0={lon0} +k=0.9996 +x_0=500000 +y_0={y0} "
        f"+a={a} +rf={rf} {towgs}+units=m +no_defs +type=crs"
    )
    # 局部经纬度已经是目标基准，这里不再做基准换算
    t = Transformer.from_crs(datum_crs(name), CRS.from_proj4(proj), always_xy=True)
    e, n = t.transform(local_lon, local_lat)
    return zone, e, n


def maidenhead(lat: float, lon: float) -> str:
    lon_adj, lat_adj = lon + 180.0, lat + 90.0
    return (
        chr(ord("A") + int(lon_adj / 20))
        + chr(ord("A") + int(lat_adj / 10))
        + str(int(lon_adj % 20 / 2))
        + str(int(lat_adj % 10))
        + chr(ord("a") + int(lon_adj % 2 * 12))
        + chr(ord("a") + int(lat_adj % 1 * 24))
    )


def parse_int(text: str) -> int:
    return int(float(text))


def main() -> int:
    failures = []
    checked = 0
    for line in BENCH.read_text().splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        point, datum, fmt = row["point"], row["datum"], row["format"]
        lat, lon = row["lat"], row["lon"]
        got = row["lines"]
        checked += 1

        if fmt in ("UTM", "MGRS", "USNG"):
            zone, e, n = utm_expected(lat, lon, datum)
            if fmt == "UTM":
                m = re.match(r"(\d+)([A-Z]) (\d+) (\d+)$", got)
                if not m:
                    failures.append((point, datum, fmt, f"unparsable: {got}"))
                    continue
                if int(m.group(1)) != zone or abs(parse_int(m.group(3)) - e) > 1 or abs(parse_int(m.group(4)) - n) > 1:
                    failures.append((point, datum, fmt, f"expect zone {zone} E{e:.0f} N{n:.0f}, got {got}"))
            else:
                if datum != "WGS84":
                    continue  # pygeodesy 只算 WGS84 的 MGRS
                u = toUtm8(lat, lon)
                expected = toMgrs(u)
                compact = got.replace(" ", "")
                tail = re.search(r"([A-Z]{2})(\d+)$", compact)
                if not tail or len(tail.group(2)) != 10:
                    failures.append((point, datum, fmt, f"unparsable: {got}"))
                    continue
                body = tail.group(2)
                if abs(int(body[:5]) - expected.easting) > 1 or abs(int(body[5:]) - expected.northing) > 1:
                    failures.append((point, datum, fmt, f"expect {expected.toStr()}, got {got}"))

        elif fmt in ("OSGB_EN", "OSGB_GRID"):
            try:
                expected = toOsgr(lat, lon)
            except Exception:
                continue  # 网格覆盖范围之外（OSGB 只覆盖英国本土）
            exp_e, exp_n = expected.easting, expected.northing
            numbers = re.findall(r"\d+", got)
            if fmt == "OSGB_EN":
                if len(numbers) != 2 or abs(int(numbers[0]) - exp_e) > 1 or abs(int(numbers[1]) - exp_n) > 1:
                    failures.append((point, datum, fmt, f"expect {exp_e:.0f} {exp_n:.0f}, got {got}"))
            else:
                letters = re.match(r"^([A-Z]{2})", got)
                exp_letters = re.match(r"^([A-Z]{2})", expected.toStr())
                if not letters or letters.group(1) != exp_letters.group(1):
                    failures.append((point, datum, fmt, f"expect {expected.toStr()}, got {got}"))

        elif fmt == "MAIDENHEAD":
            expected = maidenhead(lat, lon)
            if got != expected:
                failures.append((point, datum, fmt, f"expect {expected}, got {got}"))

        elif fmt == "CH1903":
            t = Transformer.from_crs(datum_crs("CH1903"), CRS.from_proj4(
                "+proj=somerc +lat_0=46.9524055555556 +lon_0=7.43958333333333 "
                "+k_0=1 +x_0=600000 +y_0=200000 +ellps=bessel +units=m +no_defs +type=crs"
            ), always_xy=True)
            y, x = t.transform(to_local(lat, lon, "CH1903")[1], to_local(lat, lon, "CH1903")[0])
            numbers = re.findall(r"\d+", got)
            if len(numbers) != 2 or abs(int(numbers[0]) - y) > 3 or abs(int(numbers[1]) - x) > 3:
                failures.append((point, datum, fmt, f"expect {y:.0f}/{x:.0f}, got {got}"))

    print(f"checked {checked} rows, {len(failures)} mismatches")
    for point, datum, fmt, detail in failures[:40]:
        print(f"  {point:11s} {datum:12s} {fmt:11s} {detail}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
