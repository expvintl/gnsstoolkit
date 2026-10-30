# 轨迹格式校验

`LogRecorder` 输出的 GPX / KML 是否符合规范，用一个独立的 Python 复刻实现来验证，
不依赖 APK 运行：

```bash
./download-schemas.sh          # 需要能访问外网（走代理时先 export https_proxy）
pip install lxml               # 或用本地解包的 wheel + PYTHONPATH
python3 gen.py                 # 生成 sample.gpx / sample.kml / sample_empty.kml
python3 verify.py              # GPX 1.1 官方 schema 校验 + 元素顺序断言
python3 verify_kml.py          # KML 2.2 + gx 扩展 schema 校验
```

`gen.py` 逐字复刻 `app/src/main/java/com/gnsstoolkit/data/LogRecorder.kt` 里的拼接逻辑。
**改了 Kotlin 侧的标签或顺序时，记得同步改 `gen.py`**，否则验证的就是旧格式。

三个曾经踩到、已被 schema 校验抓出来的坑：

1. GPX 的 `<trkpt>` 子元素有固定顺序（`ele, time, fix, sat, hdop, vdop, pdop, extensions`），
   顺序错了普通解析器不报错，但严格校验器会拒。
2. `<extensions>` 里的自定义元素必须属于非 GPX 命名空间（schema 里是 `namespace="##other"`），
   所以扩展字段带 `gnt:` 前缀，前缀在根元素上声明一次。
3. KML 的 `gx:Track` 要求**所有 `<when>` 排在所有 `<gx:coord>` 之前**，
   交替写入不合法；因此按 1000 点一块封一个 `Placemark`。
