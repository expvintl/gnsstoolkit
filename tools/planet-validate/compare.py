"""把 Bench.kt 的输出与 skyfield + DE421 星历对表。"""
import json, math, sys
sys.path.insert(0, "/sd/Ubuntu/workspace/default/.toolchain/pylibs")
from skyfield.api import load, wgs84
import datetime

TS = load.timescale()
EPH = load("/sd/Ubuntu/workspace/default/.toolchain/de421.bsp")
KEY = {
    "SUN": "sun", "MOON": "moon",
    "MERCURY": "mercury barycenter", "VENUS": "venus barycenter",
    "MARS": "mars barycenter", "JUPITER": "jupiter barycenter",
    "SATURN": "saturn barycenter", "URANUS": "uranus barycenter",
    "NEPTUNE": "neptune barycenter",
}

rows = [json.loads(l) for l in open("bench-out.jsonl")]
worst = (0.0, "")
by_body = {}
for row in rows:
    dt = datetime.datetime.fromisoformat(row["date"].replace("Z", "+00:00"))
    t = TS.from_datetime(dt)
    obs = wgs84.latlon(row["lat"], row["lon"])
    body = EPH[KEY[row["body"]]]
    alt, az, _ = (EPH["earth"] + obs).at(t).observe(body).apparent().altaz()
    daz = abs((row["az"] - az.degrees + 180) % 360 - 180) * math.cos(math.radians(alt.degrees))
    dalt = abs(row["alt"] - alt.degrees)
    err = max(daz, dalt)
    by_body.setdefault(row["body"], []).append(err)
    if err > worst[0]:
        worst = (err, f'{row["date"]} {row["body"]} @({row["lat"]},{row["lon"]}) kotlin az={row["az"]:.3f} alt={row["alt"]:.3f} / sky az={az.degrees:.3f} alt={alt.degrees:.3f}')

print(f"checked {len(rows)} rows")
for body, errs in sorted(by_body.items()):
    print(f"  {body:8s} worst {max(errs):.3f} deg")
flag = "OK" if worst[0] < 0.15 else "TOO LARGE"
print(f"{flag}: worst = {worst[0]:.3f} deg -> {worst[1]}")
