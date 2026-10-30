#!/usr/bin/env python3
"""用与 Kotlin 相同的几何逻辑渲染一张离线预览图，肉眼确认投影、陆地、晨昏线。

不是给 App 用的，纯粹是开发时看效果：输出 preview.png。
"""
import math
import struct
import zlib
from datetime import datetime

import sun as ref

W, H = 720, 360
OCEAN = (0x12, 0x30, 0x4A)
LAND = (0x35, 0x6F, 0x9A)
NIGHT = (0x00, 0x00, 0x00)
NIGHT_ALPHA = 0.68
GRID = (0x60, 0x70, 0x80)
TERM = (0x7F, 0xD8, 0xD0)
SUN = (0xFF, 0xC2, 0x4B)
HERE = (0x7F, 0xD8, 0xD0)
BIN = "../../app/src/main/res/raw/world_land.bin"


def load_rings():
    data = open(BIN, "rb").read()
    pos = 0
    count = struct.unpack_from("<H", data, pos)[0]; pos += 2
    rings = []
    for _ in range(count):
        n = struct.unpack_from("<H", data, pos)[0]; pos += 2
        pts = []
        for _ in range(n):
            lon_c, lat_c = struct.unpack_from("<HH", data, pos); pos += 4
            pts.append((lon_c / 100.0 - 180.0, lat_c / 100.0 - 90.0))
        rings.append(pts)
    return rings


SHADOW = (0x10, 0x16, 0x1A)


def _px(img, x, y, color):
    if 0 <= x < W and 0 <= y < H:
        img[y][x] = color


def disc(img, cx, cy, radius, color):
    r2 = radius * radius
    for py in range(int(cy - radius) - 1, int(cy + radius) + 2):
        for px in range(int(cx - radius) - 1, int(cx + radius) + 2):
            if (px - cx) ** 2 + (py - cy) ** 2 <= r2:
                _px(img, px, py, color)


def band(img, cx, cy, inner, outer, color):
    lo, hi = inner * inner, outer * outer
    for py in range(int(cy - outer) - 1, int(cy + outer) + 2):
        for px in range(int(cx - outer) - 1, int(cx + outer) + 2):
            d = (px - cx) ** 2 + (py - cy) ** 2
            if lo <= d <= hi:
                _px(img, px, py, color)


def thick_line(img, x0, y0, x1, y1, width, color):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    half = max(1, int(round(width / 2)))
    for i in range(steps + 1):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        disc(img, x, y, half, color)


def draw_markers(img, sx, sy, hx, hy):
    """与 Kotlin 的 drawSunMarker / drawPositionMarker 同一套几何（1dp = 2px）。"""
    core = 6.4 * 2
    inner, outer = core + 2.6 * 2, core + 5.4 * 2
    for i in range(8):
        a = math.radians(i * 45.0)
        x0, y0 = sx + math.cos(a) * inner, sy + math.sin(a) * inner
        x1, y1 = sx + math.cos(a) * outer, sy + math.sin(a) * outer
        thick_line(img, x0, y0, x1, y1, 4.4 * 2, SHADOW)
        thick_line(img, x0, y0, x1, y1, 3.0 * 2, (0xFF, 0xFF, 0xFF))
        thick_line(img, x0, y0, x1, y1, 1.8 * 2, SUN)
    disc(img, sx, sy, core + 2.2 * 2, SHADOW)
    disc(img, sx, sy, core + 1.4 * 2, (0xFF, 0xFF, 0xFF))
    disc(img, sx, sy, core, SUN)

    ring = 7.2 * 2
    band(img, hx, hy, ring + 3.0 * 2, ring + 3.0 * 2 + 3.0 * 2, SHADOW)
    band(img, hx, hy, ring + 3.0 * 2, ring + 3.0 * 2 + 2.0 * 2, HERE)
    disc(img, hx, hy, ring + 2.6 * 2, SHADOW)
    disc(img, hx, hy, ring + 1.8 * 2, (0xFF, 0xFF, 0xFF))
    band(img, hx, hy, ring - 2.8 * 2, ring + 2.8 * 2, HERE)
    disc(img, hx, hy, 3.4 * 2, SHADOW)
    disc(img, hx, hy, 2.6 * 2, HERE)


def x_of(lon):
    return (lon + 180.0) / 360.0 * W


def y_of(lat):
    return (90.0 - lat) / 180.0 * H


def night_band(delta_lon, decl):
    psi = math.degrees(math.atan2(
        math.cos(math.radians(decl)) * math.cos(math.radians(delta_lon)),
        math.sin(math.radians(decl))))
    cuts = [-90.0]
    k = math.floor((-90.0 + psi) / 180.0) - 1
    while True:
        phi = k * 180.0 - psi
        if phi > 90.0:
            break
        if -90.0 + 1e-9 < phi < 90.0 - 1e-9:
            cuts.append(phi)
        k += 1
    cuts.append(90.0)
    for i in range(len(cuts) - 1):
        lo, hi = cuts[i], cuts[i + 1]
        if hi - lo < 1e-9:
            continue
        if math.sin(math.radians((lo + hi) / 2.0 + psi)) < 0.0:
            return lo, hi
    return None


def fill_land(img, rings):
    for ring in rings:
        ys = [y_of(p[1]) for p in ring]
        for py in range(max(0, int(min(ys))), min(H, int(max(ys)) + 1)):
            yc = py + 0.5
            xs = []
            for i in range(len(ring)):
                x1, y1 = x_of(ring[i][0]), y_of(ring[i][1])
                x2, y2 = x_of(ring[(i + 1) % len(ring)][0]), y_of(ring[(i + 1) % len(ring)][1])
                if (y1 <= yc < y2) or (y2 <= yc < y1):
                    xs.append(x1 + (yc - y1) * (x2 - x1) / (y2 - y1))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for px in range(max(0, int(xs[i])), min(W, int(xs[i + 1]) + 1)):
                    img[py][px] = LAND


def main():
    rings = load_rings()
    img = [[OCEAN for _ in range(W)] for _ in range(H)]
    fill_land(img, rings)

    dt = datetime.fromisoformat("2026-10-31T04:00:00+00:00")
    decl, sub_lon = ref.subsolar(dt)

    for px in range(W):
        lon = (px + 0.5) / W * 360.0 - 180.0
        delta = (lon - sub_lon + 180.0) % 360.0 - 180.0
        band = night_band(delta, decl)
        if band is None:
            continue
        y_top = max(0, int(y_of(band[1])))
        y_bottom = min(H - 1, int(y_of(band[0])))
        for py in range(y_top, y_bottom + 1):
            for c in range(3):
                img[py][px] = tuple(
                    int(img[py][px][c] * (1 - NIGHT_ALPHA) + NIGHT[c] * NIGHT_ALPHA) for c in range(3)
                ) if False else tuple(
                    int(img[py][px][i] * (1 - NIGHT_ALPHA)) for i in range(3)
                )

    for lon in range(-180, 181, 30):
        for py in range(H):
            img[py][max(0, min(W - 1, int(x_of(lon))))] = GRID
    for lat in range(-60, 61, 30):
        row = max(0, min(H - 1, int(y_of(lat))))
        for px in range(W):
            img[row][px] = GRID

    prev = None
    for px in range(W):
        lon = px / W * 360.0 - 180.0
        delta = (lon - sub_lon + 180.0) % 360.0 - 180.0
        band = night_band(delta, decl)
        if band is None:
            prev = None
            continue
        edge = band[0] if band[1] > 89.99 else band[1]
        y = int(y_of(edge))
        if prev is not None and abs(y - prev) < H // 2:
            for yy in range(min(y, prev), max(y, prev) + 1):
                if 0 <= yy < H:
                    img[yy][px] = TERM
        prev = y

    draw_markers(img, x_of(sub_lon), y_of(decl), x_of(116.4), y_of(39.9))

    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in img)
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    open("preview.png", "wb").write(png)
    print(f"preview.png {W}x{H}  sun=({decl:.2f}, {sub_lon:.2f})")


if __name__ == "__main__":
    main()
