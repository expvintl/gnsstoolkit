"""校验 KML 轨迹：XML schema + 结构语义。

schema 只能保证"元素合法"，管不了经纬度写反、坐标数量对不上这类错误，
所以额外断言：
  * 每个 Placemark 里是 LineString（不是 Google 私有的 gx:Track，很多查看器不认）
  * coordinates 里每个三元组都是 lon,lat,alt，且经纬度在合法范围内
  * Placemark 子元素顺序符合 schema 的 sequence（name -> TimeSpan -> styleUrl -> LineString）
  * 分块后总坐标数等于输入点数
"""
from lxml import etree
import sys

SCHEMA_PATH = "schemas/ogckml22.xsd"
NS = {"k": "http://www.opengis.net/kml/2.2"}
SAMPLE = "sample.kml"
EMPTY = "sample_empty.kml"
EXPECTED_POINTS = 3
EXPECTED_ORDER = ["name", "TimeSpan", "styleUrl", "LineString"]

schema = etree.XMLSchema(etree.parse(SCHEMA_PATH))
failures = []


def check(cond, msg):
    if not cond:
        failures.append(msg)


for path in (SAMPLE, EMPTY):
    doc = etree.parse(path)
    ok = schema.validate(doc)
    print(f"{path} schema valid: {ok}")
    if not ok:
        for e in schema.error_log:
            print("  line", e.line, ":", e.message)
        sys.exit(1)

    root = doc.getroot()
    check(root.tag == "{http://www.opengis.net/kml/2.2}kml", f"{path}: 根元素必须是 kml")
    check(
        not root.findall(".//{http://www.google.com/kml/ext/2.2}Track"),
        f"{path}: 不应再出现 gx:Track（通用查看器不支持）",
    )

    total = 0
    marks = root.findall(".//k:Placemark", NS)
    for i, mark in enumerate(marks):
        order = [etree.QName(c).localname for c in mark]
        line = mark.find("k:LineString", NS)
        print(f"  placemark{i}: order={order}")
        check(line is not None, f"{path}: Placemark 缺少 LineString")
        check(order == EXPECTED_ORDER, f"{path}: Placemark 子元素顺序应为 {EXPECTED_ORDER}")
        if line is None:
            continue

        coords_el = line.find("k:coordinates", NS)
        check(coords_el is not None, f"{path}: LineString 缺少 coordinates")
        if coords_el is None:
            continue
        tuples = coords_el.text.strip().split()
        for tup in tuples:
            parts = tup.split(",")
            check(len(parts) == 3, f"{path}: 坐标应为 lon,lat,alt 三元组，实际 {tup!r}")
            if len(parts) != 3:
                continue
            lon, lat, alt = (float(v) for v in parts)
            check(-180.0 <= lon <= 180.0, f"{path}: 经度越界 {lon}")
            check(-90.0 <= lat <= 90.0, f"{path}: 纬度越界 {lat}")
            check(abs(lon) > 1.0 or abs(lat) > 1.0, f"{path}: 经纬度疑似写反 {tup!r}")
        total += len(tuples)
        print(f"    points={len(tuples)} first={tuples[0]}")

    if path == SAMPLE:
        check(total == EXPECTED_POINTS, f"{path}: 坐标总数应为 {EXPECTED_POINTS}，实际 {total}")
    else:
        check(total == 0, f"{path}: 空轨迹不应有坐标，实际 {total}")

if failures:
    for f in failures:
        print("FAIL:", f)
    sys.exit(1)
print("KML checks passed")
