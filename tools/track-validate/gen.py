"""按 LogRecorder.kt 的拼接逻辑复刻输出，用于验证产出的 GPX/KML 是否结构合法。"""
import xml.etree.ElementTree as ET

FILE_STAMP = "20261031-023500"
NOW_ISO = "2026-10-31T02:35:00.000Z"

def header_gpx():
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<gpx version="1.1" creator="GNSS Toolkit"'
        ' xmlns="http://www.topografix.com/GPX/1/1"'
        ' xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
        ' xmlns:gnt="https://gnsstoolkit.app/xmlschemas/gpxext/1"'
        ' xsi:schemaLocation="http://www.topografix.com/GPX/1/1'
        ' http://www.topografix.com/GPX/1/1/gpx.xsd">\n'
        f'  <metadata><name>GNSS Toolkit {FILE_STAMP}</name>'
        f'<time>{NOW_ISO}</time></metadata>\n'
        '  <trk><name>GNSS Toolkit track</name><trkseg>'
    )

FOOTER_GPX = "\n  </trkseg></trk>\n</gpx>"

def pt(ele, t, fix_type, sat, hdop, vdop, pdop, speed, course, hacc, vacc, cn0, used, visible):
    s = f'<trkpt lat="39.9086920" lon="116.3974770">'
    if ele is not None: s += f'<ele>{ele:.1f}</ele>'
    s += f'<time>{t}</time><fix>{fix_type}</fix><sat>{sat}</sat>'
    if hdop is not None: s += f'<hdop>{hdop:.2f}</hdop>'
    if vdop is not None: s += f'<vdop>{vdop:.2f}</vdop>'
    if pdop is not None: s += f'<pdop>{pdop:.2f}</pdop>'
    s += '<extensions>'
    if speed is not None: s += f'<gnt:speed>{speed:.2f}</gnt:speed>'
    if course is not None: s += f'<gnt:course>{course:.1f}</gnt:course>'
    if hacc is not None: s += f'<gnt:hacc>{hacc:.1f}</gnt:hacc>'
    if vacc is not None: s += f'<gnt:vacc>{vacc:.1f}</gnt:vacc>'
    s += f'<gnt:meanCn0>{cn0:.1f}</gnt:meanCn0>'
    s += f'<gnt:satsUsed>{used}</gnt:satsUsed><gnt:satsVisible>{visible}</gnt:satsVisible>'
    s += '</extensions></trkpt>'
    return s

lines = [header_gpx()]
lines.append(pt(45.2, "2026-10-31T02:35:01.123Z", "3d", 12, 0.8, 1.2, 1.4, 1.2, 180.0, 3.5, 5.0, 32.1, 12, 14))
lines.append(pt(45.4, "2026-10-31T02:35:02.123Z", "3d", 11, None, 1.3, 1.5, None, None, 4.0, None, 30.5, 11, 14))
lines.append(pt(None, "2026-10-31T02:35:03.123Z", "2d", 5, 2.0, None, None, 0.0, 90.5, 25.0, 30.0, 25.0, 5, 9))
gpx = "\n".join(lines) + FOOTER_GPX
open("sample.gpx","w",encoding="utf-8").write(gpx)

def header_kml():
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<kml xmlns="http://www.opengis.net/kml/2.2">\n'
        f'  <Document><name>GNSS Toolkit {FILE_STAMP}</name>\n'
        '    <Style id="track"><LineStyle><color>ffa8a500</color>'
        '<width>3</width></LineStyle></Style>\n'
        '    <!-- one LineString per 10000 points -->\n'
    )

FOOTER_KML = "\n  </Document>\n</kml>"

def kml_segment(points, index):
    """points: [(when, 'lon,lat,alt')] -> 一个 Placemark + LineString"""
    if not points:
        return None
    coords = " ".join(c for _, c in points)
    out = [f'    <Placemark>\n      <name>Track {index}</name>\n'
           f'      <TimeSpan><begin>{points[0][0]}</begin>'
           f'<end>{points[-1][0]}</end></TimeSpan>\n'
           '      <styleUrl>#track</styleUrl>\n'
           '      <LineString>\n'
           '        <altitudeMode>absolute</altitudeMode>\n'
           f'        <coordinates>{coords}</coordinates>\n'
           '      </LineString>\n    </Placemark>']
    return ''.join(out)

pts = [('2026-10-31T02:35:01.123Z', '116.3974770,39.9086920,45.2'),
       ('2026-10-31T02:35:02.123Z', '116.3974771,39.9086921,45.4'),
       ('2026-10-31T02:35:03.123Z', '116.3974772,39.9086922,45.6')]
klines = [header_kml(), kml_segment(pts[:2], 1), kml_segment(pts[2:], 2)]
kml = '\n'.join(klines) + FOOTER_KML
open("sample.kml","w",encoding="utf-8").write(kml)

# 空轨迹（只有头尾）也要合法
open("sample_empty.kml","w",encoding="utf-8").write(header_kml() + FOOTER_KML)
print("generated")
