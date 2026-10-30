#!/usr/bin/env python3
"""拿 astral（PyPI 的成熟实现）核对 Kotlin 的五段光照时间与日出日落。

astral 的 dawn/dusk(depression) 是几何高度角，与本项目的定义一致；
golden 的上界取 +6° 几何高度角，用 time_at_elevation(with_refraction=False)。
容差 3 分钟；高纬掠射时两边差到十分钟是建模灵敏度，不是错误。
"""
import json
import sys
from datetime import date

from astral import LocationInfo, SunDirection
from astral.sun import dawn, dusk, sunrise, sunset, time_at_elevation

TOLERANCE_MINUTES = 3


def minutes(value):
    return value.hour * 60 + value.minute


def parse(text):
    if text is None:
        return None
    h, m = text.split(":")
    return int(h) * 60 + int(m)


def safe(fn, *args, **kwargs):
    try:
        return fn(*args, **kwargs)
    except (ValueError, TypeError):
        return None


def main():
    rows = [json.loads(line) for line in open("bench-out.jsonl") if line.strip()]
    worst = 0.0
    failures = []
    checked = 0
    for row in rows:
        loc = LocationInfo("x", "x", row["zone"], row["lat"], row["lon"])
        obs = loc.observer
        day = date.fromisoformat(row["date"])
        tz = loc.tzinfo

        rise6 = safe(time_at_elevation, obs, 6, day, SunDirection.RISING, tz, False)
        set6 = safe(time_at_elevation, obs, 6, day, SunDirection.SETTING, tz, False)

        edges = {
            "sunrise": safe(sunrise, obs, day, tzinfo=tz),
            "sunset": safe(sunset, obs, day, tzinfo=tz),
            "dawn6": safe(dawn, obs, day, tzinfo=tz, depression=6),
            "dusk6": safe(dusk, obs, day, tzinfo=tz, depression=6),
            "dawn12": safe(dawn, obs, day, tzinfo=tz, depression=12),
            "dusk12": safe(dusk, obs, day, tzinfo=tz, depression=12),
            "dawn18": safe(dawn, obs, day, tzinfo=tz, depression=18),
            "dusk18": safe(dusk, obs, day, tzinfo=tz, depression=18),
            "rise6": rise6,
            "set6": set6,
        }

        specs = {
            "golden": (("sunrise", "rise6"), ("set6", "sunset"), ("sunrise", "sunset"),
                       ("set6", "rise6")),
            "civil": (("dawn6", "sunrise"), ("sunset", "dusk6"), ("dawn6", "dusk6"),
                      ("sunset", "sunrise")),
            "nautical": (("dawn12", "dawn6"), ("dusk6", "dusk12"), ("dawn12", "dusk12"),
                         ("dusk6", "dawn6")),
            "astro": (("dawn18", "dawn12"), ("dusk12", "dusk18"), ("dawn18", "dusk18"),
                      ("dusk12", "dawn12")),
        }

        for band, spec in specs.items():
            pair1 = [edges[spec[0][0]], edges[spec[0][1]]]
            pair2 = [edges[spec[1][0]], edges[spec[1][1]]]
            expected = []
            if all(pair1):
                expected.append(pair1)
            if all(pair2):
                expected.append(pair2)
            if not expected:
                for pair in spec[2:]:
                    cand = [edges[pair[0]], edges[pair[1]]]
                    if all(cand):
                        expected.append(cand)

            got = row.get(band) or []
            if len(got) != len(expected):
                failures.append(f"{row['date']} {row['zone']} {band}: "
                                f"kotlin={got} astral={[[f'{minutes(x)//60:02d}:{minutes(x)%60:02d}' for x in p] for p in expected]}")
                continue
            for gp, ep in zip(got, expected):
                for gtx, etx in zip(gp, ep):
                    g = parse(gtx)
                    e = minutes(etx)
                    checked += 1
                    diff = abs(g - e) if g is not None else 999
                    worst = max(worst, diff)
                    if diff > TOLERANCE_MINUTES:
                        failures.append(
                            f"{row['date']} {row['zone']} {band}: kotlin={gtx} "
                            f"astral={e//60:02d}:{e%60:02d} diff={diff}min"
                        )

        for key in ("sunrise", "sunset"):
            got = parse(row.get(key))
            exp = edges[key]
            if exp is None:
                if got is not None:
                    failures.append(f"{row['date']} {row['zone']} {key}: kotlin={row.get(key)} astral=None")
                continue
            checked += 1
            diff = abs(got - minutes(exp)) if got is not None else 999
            worst = max(worst, diff)
            if diff > TOLERANCE_MINUTES:
                failures.append(f"{row['date']} {row['zone']} {key}: kotlin={row.get(key)} "
                                f"astral={minutes(exp)//60:02d}:{minutes(exp)%60:02d} diff={diff}min")

    print(f"checked {checked} values, worst diff {worst:.0f} min, {len(failures)} failures")
    for f in failures:
        print("  " + f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
