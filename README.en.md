# GNSS Toolkit

[简体中文](README.md) | English

A Material 3 Android GNSS toolkit for people who care about **raw satellite data**, not just "a coordinate".
All positioning data comes straight from `LocationManager` / `GnssStatus` / NMEA — no Google Play Services — so it works fully on devices without GMS.

## Features

| Screen | Contents |
|---|---|
| Sky | Polar sky plot that rotates with the device, per-satellite details, a **celestial** mode (alt/az, distance and magnitude of the Sun, Moon and eight planets), and an **AR reticle** (camera axis or phone top edge, with a circle that follows where you point) |
| Signal | C/N0 bars per satellite, per-constellation summary (visible / used / best / mean), and visible / in-use filters |
| Location | Coordinates in **12 formats** × **12 geodetic datums**, altitude (incl. MSL), speed, accuracy, bearing, DOP, TTFF, sunrise/sunset with **five twilight bands**, moon phase, and a day/night terminator map |
| Compass | Magnetic vs true north, declination, GPS course, sensor accuracy and a calibration hint |
| Status | Engine state, the `GnssCapabilities` matrix, supported signal types and frequencies, AGPS injection and reset |
| Log | GPX / KML / CSV track recording with adaptive sampling, distance totals, share and delete |
| Settings | In-app language (system / 中文 / English), dynamic color, pure black, units (metric / imperial / aviation / nautical), constellation visibility, coordinate format and datum, log format and sampling rate |

### Coordinate formats

`DD°MM'SS.SSS"`, `DD°MM.MMMM'`, `DD.DDDDDD°`, UTM, MGRS, USNG, OSGB (E,N), OSGB (Grid Ref), Maidenhead, CH1903, OSNI (E,N), OSNI (Grid Ref)

### Geodetic datums

WGS84, NAD27-Conus, NAD83-Conus, OSGB36-Mean, ED50-Mean, ED50-Spain, AGD66, AGD84, SAD69-Mean, Adindan-Mean, Ireland 1965, CH1903.
Parameters are the EPSG Helmert 7-parameter set (position-vector convention). The OSGB, OSNI and CH1903 formats automatically pin to their matching datum.

## Building

```bash
# Requires JDK 17+ and Android SDK Platform 35 / Build-Tools 35.0.0
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk

./gradlew :app:assembleDebug     # debug build (package com.gnsstoolkit.debug, installs alongside release)
./gradlew :app:assembleRelease   # release build (R8 + resource shrinking, ~1.3 MB)
```

`release` is currently signed with the debug certificate so it can be installed for testing; swap in your own keystore before distributing it for real.

## Releases

- `.github/workflows/release.yml` — on every push to `main` (or a manual run from the Actions tab) it builds the release APK and updates the single rolling `latest` release, replacing the old assets.
- `.github/workflows/ci.yml` — builds the debug APK on pull requests and uploads it as an artifact.

The version name is generated automatically as `1.0.<run number>`; there are no tags to manage. The download URL never changes:

```
https://github.com/expvintl/gnsstoolkit/releases/latest/download/gnsstoolkit-latest.apk
```

## Tech stack

Kotlin 2.1 · Jetpack Compose (Material 3) · single Activity with hand-rolled navigation · DataStore Preferences · no third-party location libraries.
`minSdk 26`, `targetSdk 35`.

## Algorithms and verification

The astronomy, coordinate and track-format code is all checked offline; the scripts live in `tools/`:

| Directory | Contents | Reference | Result |
|---|---|---|---|
| `tools/solar-validate` | Sunrise/sunset, five twilight bands, golden/blue hour | astral | 802 values, all within 3 minutes except grazing cases at 77.85°S |
| `tools/moon-validate` | Moon phase, illuminated fraction, age, new/full moon times | Meeus 47.a example + eclipse timings | new/full moon ±8 min; Kotlin vs Python < 0.001° |
| `tools/planet-validate` | Alt/az of nine bodies | skyfield + DE421 ephemeris | 54 cases, worst 0.081° (Saturn) |
| `tools/coord-validate` | 12 coordinate formats × 12 datums | pyproj / PROJ 9.8 + pygeodesy | 720 rows; only CH1903 extrapolated outside Switzerland deviates |
| `tools/track-validate` | GPX 1.1 / KML 2.2 output | official XSDs (OGC, Topografix) | schema validation passes |
| `tools/worldmap` | Low-resolution land outlines for the terminator map | Natural Earth 110m | — |

Solar position uses chapter 25 of Meeus, *Astronomical Algorithms* (including the equation of time, formula 28.3); the Moon uses the truncated series of chapter 47; planets use JPL approximate orbital elements plus precession, annual parallax and light-time correction.

## Known limitations

- Datum conversion is a 7-parameter Helmert approximation with metre-level accuracy. Use grid shifts (NTv2 etc.) if you need centimetres.
- CH1903 uses the swisstopo approximation formula, accurate inside Switzerland only; outside it is an extrapolation.
- Moon phase and planet positions are good to minutes / 0.1°, enough for display and pointing, not for precision tracking.
- The AR reticle relies on the rotation-vector sensor; devices without a magnetometer fall back to north-up.

## License

[MIT](LICENSE) © 2026 expvintl
