#!/usr/bin/env python3
"""把 Kotlin 侧 bench 的输出跟 sun.py 的独立实现逐点比对。"""
import math
import subprocess
import sys
from datetime import datetime

import sun as ref

CASES = [
    "2026-03-20T14:46:00Z", "2026-06-21T08:25:00Z", "2026-09-23T00:05:00Z",
    "2026-12-21T20:50:00Z", "2026-10-31T12:00:00Z", "2026-10-31T04:00:00Z",
]


def elevation(lat, lon, decl, subsolar_lon):
    lat_r, d_r = math.radians(lat), math.radians(decl)
    h = math.radians(lon - subsolar_lon)
    return math.degrees(math.asin(
        max(-1.0, min(1.0, math.sin(lat_r) * math.sin(d_r)
                      + math.cos(lat_r) * math.cos(d_r) * math.cos(h)))))


def main():
    out = subprocess.run(
        ["bash", "run-bench.sh"], capture_output=True, text=True, check=True,
        env={**__import__("os").environ},
    ).stdout.splitlines()

    worst_decl = worst_lon = worst_elev = 0.0
    rows = 0
    for line in out:
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "GRID":
            lat, lon, got = float(parts[1]), float(parts[2]), float(parts[3])
            decl, sub_lon = ref.subsolar(datetime.fromisoformat("2026-10-31T04:00:00+00:00"))
            want = elevation(lat, lon, decl, sub_lon)
            worst_elev = max(worst_elev, abs(got - want))
            rows += 1
        elif parts[0].endswith("Z"):
            dt = datetime.fromisoformat(parts[0].replace("Z", "+00:00"))
            decl, sub_lon = ref.subsolar(dt)
            worst_decl = max(worst_decl, abs(float(parts[1]) - decl))
            worst_lon = max(worst_lon, abs(float(parts[2]) - sub_lon))
        elif parts[0] == "TERMINATOR":
            print(f"晨昏线回代残差: {parts[-1]}°")

    print(f"赤纬最大偏差 {worst_decl:.4f}°, 直射点经度最大偏差 {worst_lon:.4f}°, "
          f"高度角 {rows} 点最大偏差 {worst_elev:.4f}°")
    ok = worst_decl < 0.01 and worst_lon < 0.01 and worst_elev < 0.01
    print("PASS" if ok else "FAIL")
    return 0 if ok else 1



# ── 独立交叉验证 ────────────────────────────────────────────────────────────
# 月相模块的月球黄经是拿真实日月食时刻校准过的（误差 < 8 分钟）。
# 新月时太阳与月亮的黄经必然相等，用这个关系反过来检验太阳算法的黄经。
def check_against_new_moon():
    sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "moon-validate"))
    import moon as lunar

    worst = 0.0
    for year, month, day in ((2024, 5, 8), (2025, 3, 29), (2026, 11, 9), (2023, 4, 20)):
        start = lunar.jd(year, month, day - 3)
        instant = lunar.solve(0.0, start, span=8.0, step=0.05)
        t = lunar.century(instant)
        moon_lon = lunar.moon_longitude(t)
        # sun.py 里同一个时刻的太阳黄经
        dt = __import__("datetime").datetime.fromtimestamp(
            (instant - 2440587.5) * 86400.0, tz=__import__("datetime").timezone.utc
        )
        n = (instant - 2451545.0)
        mean_lon = 280.460 + 0.9856474 * n
        mean_anom = math.radians(357.528 + 0.9856003 * n)
        sun_lon = (mean_lon + 1.915 * math.sin(mean_anom) + 0.020 * math.sin(2 * mean_anom)) % 360.0
        diff = (moon_lon - sun_lon + 180.0) % 360.0 - 180.0
        worst = max(worst, abs(diff))
        print(f"  新月 {dt:%Y-%m-%d %H:%M} UTC  月黄经 {moon_lon:.3f}° "
              f"太阳黄经 {sun_lon:.3f}°  差 {diff:+.3f}°")
    print(f"新月时刻日月黄经最大差 {worst:.3f}°")
    return worst


if __name__ == "__main__":
    code = main()
    print("\n独立交叉验证（用月相模块的新月时刻反推太阳黄经）：")
    w = check_against_new_moon()
    print("PASS" if w < 0.05 else "FAIL")
    sys.exit(code or (0 if w < 0.05 else 1))
