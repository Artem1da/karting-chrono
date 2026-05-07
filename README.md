# Karting Chrono — Wear OS GPS lap timer

A standalone Wear OS 5 app for the **Google Pixel Watch 3** that uses the
on-watch GPS to time karting laps. No phone, no companion app, no network.

## Project layout

```
karting-chrono/
├── core/                    pure Kotlin (JVM) — line math + lap detector
│   └── src/test             JUnit tests for the detector
└── app/                     Wear OS app — Compose UI, Room, foreground service
```

The lap-detection algorithm lives in `core/` with **no Android dependencies**
so it can be exercised against synthetic GPS traces under plain `./gradlew :core:test`.

## How it works

1. You stand on the start/finish line and tap **Set start (A)**.
2. You walk ~5–10 m perpendicular to the racing line and tap **Set end (B)**.
   The two GPS fixes define a line segment in WGS84.
3. (Alternative) **Single-point line** uses the watch's heading at the moment
   you tap to construct a 10 m line perpendicular to it.
4. Tap **Start session**. A foreground service takes over: it requests 1 Hz
   GPS updates from `FusedLocationProvider`, holds a partial wake lock, and
   feeds samples into `LapDetector`.
5. For each new fix, the segment between the previous and current fix is
   tested for intersection with the start/finish line in a local
   equirectangular projection. When it crosses, the timestamp is linearly
   interpolated based on the fraction `t` along the GPS segment.
6. A **min-lap-time guard** (default 20 s) suppresses double-counts from
   GPS drift in the pits. An optional minimum speed filter is also available.

The math (`LocalProjection`, `SegmentMath`, `LapDetector`) is documented inline
and unit-tested.

## Build & install

### Prerequisites
- Android Studio Ladybug (or newer) with the Wear OS SDK installed
- JDK 17
- Gradle 8.7+ (the wrapper will download what's needed)

### Build a debug APK
```bash
./gradlew :app:assembleDebug
# APK lands at: app/build/outputs/apk/debug/app-debug.apk
```

### Run the unit tests for the detector
```bash
./gradlew :core:test
```

## Installing on a Pixel Watch 3 over Wi-Fi ADB

Pixel Watch 3 has no USB port, so you install over Wi-Fi.

### One-time setup on the watch
1. Open **Settings → System → About → Build number** and tap **7×** to
   enable developer options.
2. Open **Settings → Developer options** and turn on:
   - **ADB debugging**
   - **Debug over Wi-Fi**
3. Tap **Debug over Wi-Fi** — the watch shows an IP and port, e.g. `192.168.1.42:5555`.

### Pair from your computer
```bash
# Modern Wear OS uses pair-then-connect. From the watch's "Wireless debugging"
# screen, tap "Pair new device" — it will show a 6-digit code and a pairing
# port (e.g. 192.168.1.42:41234). Then on your machine:
adb pair 192.168.1.42:41234        # paste the code when prompted
adb connect 192.168.1.42:5555      # use the debugging port shown on the watch

adb devices                        # confirm the watch is listed
```

### Install the APK
```bash
adb -s 192.168.1.42:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

### View logs
```bash
adb -s 192.168.1.42:5555 logcat -v time \
    KartingChrono:V TimingService:V LapDetector:V '*:S'
```

Or watch everything filtered by package:
```bash
adb -s 192.168.1.42:5555 logcat --pid=$(adb -s 192.168.1.42:5555 shell pidof com.karting.chrono)
```

## Notes & known limitations

- Pixel Watch 3 GPS runs at **1 Hz** — that's a hardware ceiling. At karting
  speeds (~50 km/h ≈ 14 m/s) the kart moves ~14 m between samples, so lap
  times are accurate to roughly ±35 ms (half a sample at race pace). The
  linear interpolation between samples reduces this error but can't eliminate
  it.
- All processing is on-watch. There is no cloud sync, no account, no network
  permissions beyond GPS.
- The user defines the start/finish line **manually each session** — no track
  database in v1.
- Sessions and laps are persisted in a Room database (`karting.db`).
