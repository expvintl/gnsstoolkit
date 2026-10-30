"""JPL 近似轨道根数法（1800–2050）的原型，用来给 Kotlin 侧生成地面真值。"""
import math

# a(AU), e, I(deg), L(deg), peri(deg), node(deg), 各自每儒略世纪的变化率
ELEMENTS = {
    "Mercury": (0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
                0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081),
    "Venus":   (0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
                0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418),
    "Earth":   (1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
                0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0),
    "Mars":    (1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
                0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343),
    "Jupiter": (5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
                -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106),
    "Saturn":  (9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
                -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794),
    "Uranus":  (19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503,
                -0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589),
    "Neptune": (30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574,
                0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664),
}

def heliocentric(name, T):
    a, e, I, L, peri, node, da, de, dI, dL, dperi, dnode = ELEMENTS[name]
    a = a + da * T; e = e + de * T
    I = math.radians(I + dI * T)
    L = L + dL * T
    peri = math.radians(peri + dperi * T)
    node = math.radians(node + dnode * T)
    w = peri - node
    M = math.radians((L - math.degrees(peri)) % 360.0)
    if M > math.pi: M -= 2 * math.pi
    E = M
    for _ in range(64):
        dE = (E - e * math.sin(E) - M) / (1 - e * math.cos(E))
        E -= dE
        if abs(dE) < 1e-12: break
    xp = a * (math.cos(E) - e)
    yp = a * math.sqrt(1 - e * e) * math.sin(E)
    cw, sw = math.cos(w), math.sin(w)
    cn, sn = math.cos(node), math.sin(node)
    ci, si = math.cos(I), math.sin(I)
    x = (cw * cn - sw * sn * ci) * xp + (-sw * cn - cw * sn * ci) * yp
    y = (cw * sn + sw * cn * ci) * xp + (-sw * sn + cw * cn * ci) * yp
    z = (sw * si) * xp + (cw * si) * yp
    return x, y, z

PRECESSION = math.radians(1.3969713)

def obliquity(T):
    return math.radians(23.43929111 - 0.0130042 * T - 1.64e-7 * T * T + 5.04e-7 * T ** 3)

def gmst_deg(jd_ut):
    d = jd_ut - 2451545.0
    T = d / 36525.0
    g = 280.46061837 + 360.98564736629 * d + 0.000387933 * T * T - T ** 3 / 38710000.0
    return g % 360.0

def jd_from_epoch(seconds):
    return 2440587.5 + seconds / 86400.0

def bodies(epoch_seconds, lat_deg, lon_deg):
    jd = jd_from_epoch(epoch_seconds)
    T = (jd - 2451545.0) / 36525.0
    ex, ey, ez = heliocentric("Earth", T)
    eps = obliquity(T)
    lst = (gmst_deg(jd) + lon_deg) % 360.0
    out = {}
    for name in ("Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"):
        px, py, pz = heliocentric(name, T)
        gx, gy, gz = px - ex, py - ey, pz - ez
        # 光行时：用减去飞行时间后的地球位置重算一次
        r0 = math.sqrt(gx * gx + gy * gy + gz * gz)
        T2 = T - r0 * 0.0057755183 / 36525.0
        ex2, ey2, ez2 = heliocentric("Earth", T2)
        gx, gy, gz = px - ex2, py - ey2, pz - ez2
        r = math.sqrt(gx * gx + gy * gy + gz * gz)
        lon_e = math.atan2(gy, gx) + PRECESSION * T
        lat_e = math.asin(gz / r)
        ra = math.atan2(math.sin(lon_e) * math.cos(eps) - math.tan(lat_e) * math.sin(eps), math.cos(lon_e))
        dec = math.asin(math.sin(lat_e) * math.cos(eps) + math.cos(lat_e) * math.sin(eps) * math.sin(lon_e))
        out[name] = (ra, dec, r)
    # 太阳：地心 = -地球日心
    sx, sy, sz = -ex, -ey, -ez
    rs = math.sqrt(sx * sx + sy * sy + sz * sz)
    lon_s = math.atan2(sy, sx) + PRECESSION * T; lat_s = math.asin(sz / rs)
    ra_s = math.atan2(math.sin(lon_s) * math.cos(eps) - math.tan(lat_s) * math.sin(eps), math.cos(lon_s))
    dec_s = math.asin(math.sin(lat_s) * math.cos(eps) + math.cos(lat_s) * math.sin(eps) * math.sin(lon_s))
    out["Sun"] = (ra_s, dec_s, rs)

    phi = math.radians(lat_deg)
    res = {}
    for name, (ra, dec, r) in out.items():
        ha = math.radians((lst - math.degrees(ra)) % 360.0)
        alt = math.asin(math.sin(phi) * math.sin(dec) + math.cos(phi) * math.cos(dec) * math.cos(ha))
        az = math.atan2(-math.cos(dec) * math.sin(ha),
                        math.sin(dec) * math.cos(phi) - math.cos(dec) * math.sin(phi) * math.cos(ha))
        res[name] = (math.degrees(az) % 360.0, math.degrees(alt), r)
    return res

if __name__ == "__main__":
    import datetime, json
    tests = [
        ("2026-10-31T12:00:00+00:00", 39.9087, 116.3975),
        ("2026-10-31T12:00:00+00:00", -33.8688, 151.2093),
        ("2026-07-04T03:30:00+00:00", 51.4779, -0.0015),
        ("2027-02-19T00:00:00+00:00", 0.0, 0.0),
        ("2026-01-01T00:00:00+00:00", 64.1466, -21.9426),
    ]
    for iso, la, lo in tests:
        ts = datetime.datetime.fromisoformat(iso).timestamp()
        print(f"== {iso} lat={la} lon={lo}")
        for name, (az, alt, r) in sorted(bodies(ts, la, lo).items()):
            print(f"   {name:8s} az={az:7.2f} alt={alt:+7.2f} r={r:8.4f}")
