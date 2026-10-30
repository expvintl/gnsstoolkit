# GNSS 工具箱

简体中文 | [English](README.en.md)

一个 Material 3 风格的安卓 GNSS 工具集，面向需要看**原始卫星数据**而不只是「一个坐标」的人。
定位数据全部来自系统的 `LocationManager` / `GnssStatus` / NMEA，不依赖 Google Play 服务，因此在没有 GMS 的设备上也能完整工作。

## 功能

| 页面 | 内容 |
|---|---|
| 天空 | 极坐标天空图（跟随设备朝向旋转）、卫星详情、**天体**模式（太阳/月亮/八大行星的天顶位置、高度角、方位角、星距、星等）、**AR 准星**（摄像头轴或手机顶边，圆圈随设备指向移动） |
| 信号 | 每颗卫星的 C/N0 条形图、按星座汇总（可见数/使用数/最强/均值）、可见 / 使用中筛选 |
| 位置 | 坐标（**12 种格式** × **12 个大地基准**）、海拔（含 MSL）、速度、精度、方位、DOP、TTFF、日出日落与**五段晨昏**、月相、昼夜分界图 |
| 罗盘 | 磁北 / 真北 / 磁偏角 / GPS 航向、传感器精度与校准提示 |
| 状态 | 引擎状态、`GnssCapabilities` 能力矩阵、支持的信号类型与频点、AGPS 注入与清除 |
| 记录 | GPX / KML / CSV 轨迹记录，自适应采样、里程统计、文件分享与删除 |
| 设置 | 应用内语言（跟随系统 / 中文 / English）、动态取色、纯黑、单位（公制 / 英制 / 航空 / 航海）、星座显隐、坐标格式与基准、记录格式与采样率 |

### 坐标格式

`DD°MM'SS.SSS"`、`DD°MM.MMMM'`、`DD.DDDDDD°`、UTM、MGRS、USNG、OSGB (E,N)、OSGB (Grid Ref)、Maidenhead、CH1903、OSNI (E,N)、OSNI (Grid Ref)

### 大地基准

WGS84、NAD27-Conus、NAD83-Conus、OSGB36-Mean、ED50-Mean、ED50-Spain、AGD66、AGD84、SAD69-Mean、Adindan-Mean、Ireland 1965、CH1903。
参数取自 EPSG 的 Helmert 七参数（position vector 约定），OSGB / OSNI / CH1903 的格式会自动锁定到对应的基准。

## 构建

```bash
# 需要 JDK 17+ 与 Android SDK Platform 35 / Build-Tools 35.0.0
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk

./gradlew :app:assembleDebug     # 调试版（包名 com.gnsstoolkit.debug，可与发布版共存）
./gradlew :app:assembleRelease   # 发布版（R8 + 资源裁剪，约 1.3 MB）
```

`release` 目前用 debug 证书签名，便于自签名安装验证；正式分发时请换成自己的 keystore。

## 发布

- `.github/workflows/release.yml`：push 到 `main`（或在 Actions 页面手动触发）即编译 release 版，并更新仓库里固定的 `latest` 发布，覆盖旧资产。
- `.github/workflows/ci.yml`：PR 时编译调试版并上传 APK 工件。

版本号自动取 `1.0.<运行序号>`，不需要手动打标签。下载地址固定不变：

```
https://github.com/expvintl/gnsstoolkit/releases/latest/download/gnsstoolkit-latest.apk
```

## 技术栈

Kotlin 2.1 · Jetpack Compose（Material 3）· 单 Activity + 自管理导航 · DataStore Preferences · 无第三方定位库。
`minSdk 26`，`targetSdk 35`。

## 算法与校验

天文、坐标、轨迹三块都做了离线对照，脚本在 `tools/`：

| 目录 | 内容 | 对照依据 | 结果 |
|---|---|---|---|
| `tools/solar-validate` | 日月出没、五段晨昏、黄金/蓝调时刻 | astral | 802 个值，除 77.85°S 的掠射情形外全部 ≤3 分钟 |
| `tools/moon-validate` | 月相、照明比例、月龄、朔望时刻 | Meeus 47.a 算例 + 日食月食时刻 | 朔望 ±8 分钟，Kotlin 与 Python 差 <0.001° |
| `tools/planet-validate` | 九大天体方位角/高度角 | skyfield + DE421 星历 | 54 组，最差 0.081°（土星） |
| `tools/coord-validate` | 12 种坐标格式 × 12 个基准 | pyproj / PROJ 9.8 + pygeodesy | 720 行，仅 CH1903 在瑞士境外外推有偏差 |
| `tools/track-validate` | GPX 1.1 / KML 2.2 输出 | 官方 XSD（OGC、Topografix） | schema 校验通过 |
| `tools/worldmap` | 昼夜分界图用的低分辨率陆地轮廓 | Natural Earth 110m | — |

太阳位置用 Meeus《Astronomical Algorithms》第 25 章（含均时差 28.3 式），月球用第 47 章截断级数，行星用 JPL 近似轨道根数 + 岁差 + 周年视差 + 光行时。

## 已知限制

- 大地基准换算是 Helmert 七参数近似，米级精度；需要厘米级请用格网校正（NTv2 等）。
- CH1903 用 swisstopo 近似公式，只在瑞士境内准确，境外是外推值。
- 月相与行星位置为分钟级 / 0.1° 级，足够显示与指向，不用于精密跟踪。
- 天空图的 AR 准星依赖旋转矢量传感器；无磁力计的设备会退化为北朝上。

## 许可

[MIT](LICENSE) © 2026 expvintl
