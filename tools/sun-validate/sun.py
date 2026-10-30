#!/usr/bin/env python3
"""太阳直射点的参考实现，用来给 Kotlin 版做地面真值。

低精度天文算法（USNO/Astronomical Almanac 简化式），精度约 0.01°——
对一张昼夜分界图来说绰绰有余。同时输出 NOAA 日角公式的赤纬作为交叉核对。
"""
import math
from datetime import datetime, timezone

J2000 = 2451545.0


def julian_day(dt: datetime) -> float:
    return dt.timestamp() / 86400.0 + 2440587.5


def subsolar(dt: datetime):
    n = julian_day(dt) - J2000
    mean_lon = 280.460 + 0.9856474 * n
    mean_anom = math.radians(357.528 + 0.9856003 * n)
    ecl_lon = math.radians(
        mean_lon + 1.915 * math.sin(mean_anom) + 0.020 * math.sin(2 * mean_anom)
    )
    obliquity = math.radians(23.439 - 0.0000004 * n)
    decl = math.asin(math.sin(obliquity) * math.sin(ecl_lon))
    ra = math.atan2(math.cos(obliquity) * math.sin(ecl_lon), math.cos(ecl_lon))
    gmst = (280.46061837 + 360.98564736629 * n) % 360.0
    lon = (math.degrees(ra) - gmst + 180.0) % 360.0 - 180.0
    return math.degrees(decl), lon


def noaa_declination(dt: datetime) -> float:
    day_angle = 2.0 * math.pi / 365.0 * (dt.timetuple().tm_yday - 1)
    return math.degrees(
        0.006918 - 0.399912 * math.cos(day_angle) + 0.070257 * math.sin(day_angle)
        - 0.006758 * math.cos(2 * day_angle) + 0.000907 * math.sin(2 * day_angle)
        - 0.002697 * math.cos(3 * day_angle) + 0.00148 * math.sin(3 * day_angle)
    )


CASES = [
    ("2026-03-20T14:46:00Z", "春分"),
    ("2026-06-21T08:25:00Z", "夏至"),
    ("2026-09-23T00:05:00Z", "秋分"),
    ("2026-12-21T20:50:00Z", "冬至"),
    ("2026-10-31T12:00:00Z", "今天正午"),
    ("2026-10-31T04:00:00Z", "北京时间正午"),
]

if __name__ == "__main__":
    print(f"{'时刻':22s} {'赤纬':>8s} {'NOAA赤纬':>9s} {'直射点经度':>10s}  备注")
    for text, note in CASES:
        dt = datetime.fromisoformat(text.replace("Z", "+00:00"))
        decl, lon = subsolar(dt)
        print(
            f"{text:22s} {decl:8.3f} {noaa_declination(dt):9.3f} {lon:10.3f}  {note}"
        )
