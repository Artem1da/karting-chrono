# Karting Chrono — Wear OS GPS lap timer + phone companion

A standalone Wear OS 5 app for the **Google Pixel Watch 3** that uses the
on-watch GPS to time karting laps, plus an Android phone companion app
that mirrors the session history and shows live status.

## Project layout

```
karting-chrono/
├── core/                    pure Kotlin (JVM) — line math + lap detector
│   └── src/test             JUnit tests for the detector
├── sync/                    Android library — Wearable Data Layer
│                            paths and (de)serialization shared between
│                            watch and phone
├── app/                     Wear OS app — Compose UI, Room, foreground
│                            service, GPS, publishes to the phone
└── phone/                   Phone app — Compose Material 3 UI, Room
                             mirror, WearableListenerService
```

The lap-detection algorithm lives in `core/` with **no Android dependencies**
so it can be exercised against synthetic GPS traces under plain `./gradlew :core:test`.

## How phone ↔ watch sync works

Both modules ship the **same `applicationId` (`com.karting.chrono`)**, which
is the prerequisite for Google's Wearable Data Layer to deliver events
between paired devices automatically.

- During a session, the watch posts a small DataItem at `/karting/active`
  (lap count, best lap, last lap, etc) on every lap completion. The phone's
  `WearableListenerService` reflects it into a `MutableStateFlow` so the
  phone UI shows a "Session active on watch" banner with live numbers.
- When the session ends, the watch posts a full `SessionPayload`
  (DataMap-encoded laps + track) at `/karting/session/{id}` and deletes the
  active-session DataItem. The phone listener writes the payload into its
  own Room database (`karting-phone.db`) keyed by the watch's `sessionId`.
- Two fire-and-forget messages (`/karting/msg/started`, `/karting/msg/ended`)
  let the phone clear the live banner promptly without waiting for the next
  data sync round trip.

There is no cloud anywhere — sync goes Bluetooth/Wi-Fi peer-to-peer through
Google's Wearable platform.

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

### Build debug APKs
```bash
./gradlew :app:assembleDebug      # watch APK
./gradlew :phone:assembleDebug    # phone APK
# APKs land in:
#   app/build/outputs/apk/debug/app-debug.apk
#   phone/build/outputs/apk/debug/phone-debug.apk
```

Install the phone APK with `adb install` over USB. Install the watch APK
over Wi-Fi ADB (see below).

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

## Publishing to Google Play

Both APKs share `applicationId = com.karting.chrono`, which is required for
Wearable Data Layer to work between them. On Play Console this is the
**multi-form-factor** model — one app listing, two AABs uploaded as
form-factor variants.

Steps:

1. **Switch every module to AAB:**
   ```bash
   ./gradlew :app:bundleRelease
   ./gradlew :phone:bundleRelease
   ```
   Outputs:
   - `app/build/outputs/bundle/release/app-release.aab`
   - `phone/build/outputs/bundle/release/phone-release.aab`

2. **Sign both bundles with the same upload key.** Configure a `signingConfig`
   in each module's `build.gradle.kts` pointing at the same keystore, or
   enroll in **Play App Signing** (recommended) so Google manages the
   signing key and only the upload key lives on your machine.

3. **Bump the `versionCode` in both modules in lockstep** — Play won't accept
   a release where the watch and phone APKs have the same `versionCode`
   relative to the previous upload. Easiest pattern: set the watch APK
   `versionCode` to `phoneVersionCode + 1` so the watch is always one ahead.

4. **In Play Console:**
   - Create one app, upload `phone-release.aab` to a regular release track.
   - Open the app's Wear OS section and upload `app-release.aab` there.
   - Fill the watch listing screenshots (round preview required).
   - Submit both for review together.

5. **Capability declaration.** The watch app advertises capability
   `karting_chrono_watch` via `app/src/main/res/values/wear.xml`. The phone
   app uses this in the future if you want to programmatically check that
   the watch app is installed before showing certain UI.

A few sharp edges to watch for at submission:

- The watch APK must declare `<uses-feature android:name="android.hardware.type.watch" />`
  (already done) so Play Console accepts it as a Wear app.
- `<meta-data android:name="com.google.android.wearable.standalone" android:value="true" />`
  is required to mark the watch APK as installable independently — Play
  rejects standalone listings without it.
- Both APKs are signed with the **same key** if you want Wearable Data Layer
  to deliver between them. If you only ever use Play App Signing, this is
  automatic — Google ensures the production signature matches.
