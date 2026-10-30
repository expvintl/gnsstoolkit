from lxml import etree
import sys

schema = etree.XMLSchema(etree.parse("gpx.xsd"))
doc = etree.parse("sample.gpx")
ok = schema.validate(doc)
print("GPX schema valid:", ok)
if not ok:
    for e in schema.error_log:
        print("  line", e.line, ":", e.message)
    sys.exit(1)

ns = {"g":"http://www.topografix.com/GPX/1/1","gnt":"https://gnsstoolkit.app/xmlschemas/gpxext/1"}
root = doc.getroot()
pts = root.findall(".//g:trkpt", ns)
print("trkpt count:", len(pts))
print("first trkpt children:", [etree.QName(c).localname for c in pts[0]])
print("ele:", pts[0].findtext("g:ele", namespaces=ns))
print("time:", pts[0].findtext("g:time", namespaces=ns))
print("sat:", pts[0].findtext("g:sat", namespaces=ns))
print("hdop/vdop/pdop:", [pts[0].findtext(f"g:{t}", namespaces=ns) for t in ("hdop","vdop","pdop")])
print("ext speed:", pts[0].findtext("g:extensions/gnt:speed", namespaces=ns))
print("ext course:", pts[0].findtext("g:extensions/gnt:course", namespaces=ns))
# 2D 点（无海拔）必须没有 ele，且 fix=2d
p3 = pts[2]
print("2d point has ele:", p3.find("g:ele", ns) is not None, "fix:", p3.findtext("g:fix", namespaces=ns))
