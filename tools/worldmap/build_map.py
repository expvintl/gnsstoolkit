#!/usr/bin/env python3
"""把 Natural Earth 110m 陆地多边形压成 res/raw 用的紧凑二进制。

格式（小端）：
  uint16 ringCount
  每个 ring: uint16 pointCount, 然后 pointCount 个 (uint16 lon*100+18000, uint16 lat*100+9000)
调用方用偶奇填充规则画，湖泊等内环自然成为空洞。
"""
import json
import math
import pathlib
import struct

TOLERANCE = 0.25  # 度
SRC = pathlib.Path(__file__).with_name("ne110.geojson")
OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/res/raw/world_land.bin"


def perpendicular_distance(p, a, b):
    if a == b:
        return math.dist(p, a)
    (x, y), (x1, y1), (x2, y2) = p, a, b
    dx, dy = x2 - x1, y2 - y1
    t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))


def douglas_peucker(points, tolerance):
    if len(points) < 3:
        return points
    stack = [(0, len(points) - 1)]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    while stack:
        start, end = stack.pop()
        if end <= start + 1:
            continue
        worst, index = 0.0, start
        for i in range(start + 1, end):
            d = perpendicular_distance(points[i], points[start], points[end])
            if d > worst:
                worst, index = d, i
        if worst > tolerance:
            keep[index] = True
            stack.append((start, index))
            stack.append((index, end))
    return [p for p, k in zip(points, keep) if k]


def split_antimeridian(ring):
    """相邻两点经度跳变超过 180° 就切开，避免在等距圆柱投影上横穿整幅图。"""
    out, current = [], [ring[0]]
    for prev, point in zip(ring, ring[1:]):
        if abs(point[0] - prev[0]) > 180.0:
            if len(current) >= 3:
                out.append(current)
            current = [point]
        else:
            current.append(point)
    if len(current) >= 3:
        out.append(current)
    return out


def main():
    data = json.loads(SRC.read_text())
    rings = []
    for feature in data["features"]:
        geometry = feature["geometry"]
        polygons = (
            [geometry["coordinates"]]
            if geometry["type"] == "Polygon"
            else geometry["coordinates"]
        )
        for polygon in polygons:
            for raw_ring in polygon:
                pts = [(float(x), float(y)) for x, y, *_ in raw_ring]
                for piece in split_antimeridian(pts):
                    simplified = douglas_peucker(piece, TOLERANCE)
                    if len(simplified) >= 3:
                        rings.append(simplified)

    payload = bytearray(struct.pack("<H", len(rings)))
    for ring in rings:
        payload += struct.pack("<H", len(ring))
        for lon, lat in ring:
            payload += struct.pack(
                "<HH",
                int(round(lon * 100)) + 18000,
                int(round(lat * 100)) + 9000,
            )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(payload)
    points = sum(len(r) for r in rings)
    print(f"rings={len(rings)} points={points} bytes={len(payload)} -> {OUT}")


if __name__ == "__main__":
    main()
