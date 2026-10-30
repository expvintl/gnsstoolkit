"""月相计算原型：Meeus《Astronomical Algorithms》第 47/25 章截断级数。

用于在写 Kotlin 之前验证算法精度，并生成对照数据。
"""
import math

# Meeus 47.A 的主要项（(D, M, M', F, 黄经系数 1e-6 度)），按系数绝对值取前 20 项
TERMS = [
    (0, 0, 1, 0, 6288774), (2, 0, -1, 0, 1274027), (2, 0, 0, 0, 658314),
    (0, 0, 2, 0, 213618), (0, 1, 0, 0, -185116), (0, 0, 0, 2, -114332),
    (2, 0, -2, 0, 58793), (2, -1, -1, 0, 57066), (2, 0, 1, 0, 53322),
    (2, -1, 0, 0, 45758), (0, 1, -1, 0, -40923), (1, 0, 0, 0, -34720),
    (0, 1, 1, 0, -30383), (2, 0, 0, -2, 15327), (0, 0, 1, 2, -12528),
    (0, 0, 1, -2, 10980), (4, 0, -1, 0, 10675), (0, 0, 3, 0, 10034),
    (4, 0, -2, 0, 8548), (2, 1, -1, 0, -7888),
]


def jd(y, m, d, h=0.0):
    """公历 -> 儒略日（Meeus 7.1）。h 为小时（可带小数）。"""
    if m <= 2:
        y -= 1
        m += 12
    a = y // 100
    b = 2 - a + a // 4
    return int(365.25 * (y + 4716)) + int(30.6001 * (m + 1)) + d + b - 1524.5 + h / 24.0


def century(j):
    return (j - 2451545.0) / 36525.0


def sun_longitude(t):
    l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    m = math.radians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
    c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(m)
         + (0.019993 - 0.000101 * t) * math.sin(2 * m)
         + 0.000289 * math.sin(3 * m))
    return (l0 + c) % 360.0


def moon_longitude(t):
    lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t ** 2 + t ** 3 / 538841 - t ** 4 / 65194000
    d = math.radians(297.8501921 + 445267.1114034 * t - 0.0018819 * t ** 2 + t ** 3 / 545868)
    m = math.radians(357.5291092 + 35999.0502909 * t - 0.0001536 * t ** 2)
    mp = math.radians(134.9633964 + 477198.8675055 * t + 0.0087414 * t ** 2 + t ** 3 / 69699)
    f = math.radians(93.2720950 + 483202.0175233 * t - 0.0036539 * t ** 2 - t ** 3 / 3526000)
    total = 0.0
    for cd, cm, cmp_, cf, coeff in TERMS:
        total += coeff * math.sin(cd * d + cm * m + cmp_ * mp + cf * f)
    return (lp + total / 1e6) % 360.0


def elongation(j):
    """月-日黄经差，0..360（0=新月，180=满月）。"""
    t = century(j)
    return (moon_longitude(t) - sun_longitude(t)) % 360.0


def rel(j, target):
    """elongation - target，规范化到 [-180, 180)，用于找过零点。"""
    return ((elongation(j) - target + 180.0) % 360.0) - 180.0


def solve(target, j_start, span=40.0, step=0.25):
    """在 [j_start, j_start+span] 内找 elongation == target 的时刻（二分到约 1 秒）。"""
    j = j_start
    f_prev = rel(j, target)
    while j < j_start + span:
        j_next = j + step
        f_cur = rel(j_next, target)
        if f_prev < 0.0 <= f_cur:
            lo, hi = j, j_next
            for _ in range(40):
                mid = (lo + hi) / 2
                if rel(mid, target) < 0.0:
                    lo = mid
                else:
                    hi = mid
            return (lo + hi) / 2
        f_prev, j = f_cur, j_next
    return None


def fmt(j):
    z = int(j + 0.5)
    frac = j + 0.5 - z
    if z < 2299161:
        a = z
    else:
        alpha = int((z - 1867216.25) / 36524.25)
        a = z + 1 + alpha - alpha // 4
    b = a + 1524
    c = int((b - 122.1) / 365.25)
    dd = int(365.25 * c)
    e = int((b - dd) / 30.6001)
    day = b - dd - int(30.6001 * e) + frac
    month = e - 1 if e < 14 else e - 13
    year = c - 4716 if month > 2 else c - 4715
    di = int(day)
    hours = (day - di) * 24
    return f"{year:04d}-{month:02d}-{di:02d} {int(hours):02d}:{int(hours % 1 * 60):02d} UTC"


SYN = 29.530588861


def report(y, mo, d, h):
    """打印一个时刻的月相全量数据（同时作为 Kotlin 侧回归基准）。"""
    j = jd(y, mo, d, h)
    e = elongation(j)
    k = (1 - math.cos(math.radians(e))) / 2
    prev = solve(0.0, j - 31.5)
    while True:
        nxt = solve(0.0, prev + 0.5)
        if nxt is None or nxt >= j:
            break
        prev = nxt
    # 真实月龄 = 距上一次新月的天数；按平均角速度折算会差到半天。
    age = j - prev
    nxt_new = solve(0.0, j + 0.01)
    nxt_full = solve(180.0, j + 0.01)
    print(f"{y}-{mo:02d}-{d:02d} {h:05.2f}h  elong={e:7.3f}  illum={k * 100:6.2f}%  "
          f"age={age:5.2f}d  next_new={fmt(nxt_new)}  next_full={fmt(nxt_full)}")
    return e, k, age


if __name__ == "__main__":
    # 1) Meeus 47.a 算例：1992-04-12 0h TD -> lambda = 133.162655（验证月球黄经级数）
    print(f"Example 47.a  lambda = {moon_longitude(century(jd(1992, 4, 12, 0.0))):.6f}"
          f"  (Meeus 133.162655)")

    # 2) 端到端：日食必在新月附近、月食必在满月附近，偏差通常 <30 分钟
    eclipses = [
        ("solar 2023-04-20 hybrid", 0.0, (2023, 4, 10), (2023, 4, 20, 4.283)),
        ("solar 2024-04-08 total", 0.0, (2024, 4, 1), (2024, 4, 8, 18.283)),
        ("solar 2024-10-02 annular", 0.0, (2024, 9, 25), (2024, 10, 2, 18.75)),
        ("solar 2026-08-12 total", 0.0, (2026, 8, 5), (2026, 8, 12, 17.767)),
        ("lunar 2022-11-08 total", 180.0, (2022, 11, 1), (2022, 11, 8, 10.983)),
        ("lunar 2025-03-14 total", 180.0, (2025, 3, 7), (2025, 3, 14, 6.967)),
        ("lunar 2025-09-07 total", 180.0, (2025, 9, 1), (2025, 9, 7, 18.183)),
    ]
    for name, target, start, ref in eclipses:
        j = solve(target, jd(*start))
        offset = (j - jd(ref[0], ref[1], ref[2], ref[3])) * 1440
        print(f"{name:26s} phase {fmt(j)}  vs max eclipse {(offset):+6.1f} min")

    # 3) 固定基准点：Kotlin 侧 run-bench.sh 用同样的数字回归
    print("\nreference points (Bench.kt 对照):")
    for args in [(2026, 10, 31, 12.0), (2026, 1, 1, 0.0), (2024, 4, 8, 18.283), (2025, 3, 14, 6.967)]:
        report(*args)
