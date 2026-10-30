#!/bin/bash
# 下载官方 schema 到 schemas/，供 verify.py / verify_kml.py 使用。
set -e
here="$(cd "$(dirname "$0")" && pwd)"
cd "$here"
mkdir -p schemas
curl -sS -o gpx.xsd http://www.topografix.com/GPX/1/1/gpx.xsd
curl -sS -o ogckml22.xsd http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd
curl -sS -o atom-author-link.xsd http://schemas.opengis.net/kml/2.2.0/atom-author-link.xsd
curl -sSL -o xAL.xsd "https://docs.oasis-open.org/election/external/xAL.xsd"
curl -sSL -o kml22gx.xsd "https://developers.google.com/kml/schema/kml22gx.xsd"
cp atom-author-link.xsd xAL.xsd kml22gx.xsd schemas/
# 把跨文件引用指向本地副本，并让 KML 主 schema 认识 gx 扩展命名空间
sed 's#schemaLocation="http://docs.oasis-open.org/election/external/xAL.xsd"#schemaLocation="xAL.xsd"#' \
  ogckml22.xsd > schemas/ogckml22.xsd
sed -i 's#schemaLocation="http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd"#schemaLocation="ogckml22.xsd"#' \
  schemas/kml22gx.xsd
python3 - <<'PY'
import io
p = 'schemas/ogckml22.xsd'
s = io.open(p, encoding='utf-8').read()
anchor = '  <!-- KML field types (simple content) -->'
if 'kml22gx.xsd' not in s:
    s = s.replace(anchor, '  <import namespace="http://www.google.com/kml/ext/2.2"\n'
                          '    schemaLocation="kml22gx.xsd"/>\n\n' + anchor, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
PY
echo "schemas ready"
