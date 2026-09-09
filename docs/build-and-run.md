# Build and run

## Prerequisites

- JDK 25.
- Android SDK with platform `android-37` and build tools `36.0.0`.
- No local Android Studio configuration is required beyond `local.properties` pointing at the SDK. The
  wrapper drives every other step.

## The wrapper

Use the Gradle wrapper for every command, from the repository root.

```powershell
.\gradlew.bat <task>
```

The wrapper pins Gradle 9.7. The build reads Kotlin 2.4, Android Gradle Plugin 9.4, and every library
version from `gradle/libs.versions.toml`.

## The gate

CI (`.github/workflows/ci.yml`) runs these steps in order on every push to `main` and every pull
request. Run the same commands locally before pushing.

```powershell
.\gradlew.bat ktfmtCheck --no-daemon
.\gradlew.bat :app:lintRelease :core:lintRelease --no-daemon
.\gradlew.bat :core:testDebugUnitTest :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:validateDebugScreenshotTest --no-daemon
.\gradlew.bat :app:assembleRelease --no-daemon
```

`ktfmtCheck` verifies formatting under the Google style ktfmt is configured with. `ktfmtFormat`
rewrites files in place. Both lint tasks run with `warningsAsErrors` set, so a warning fails the build
the same as an error. There is no lint baseline, so every finding counts.
`:app:validateDebugScreenshotTest` compares the debug build's Compose Preview
screenshot tests against their stored reference images and fails on a pixel difference past the
configured threshold.

## Recording screenshot references

A Preview screenshot test with no reference image, or an intentional visual change, needs a fresh
reference before the gate can pass. Run the update task first. It renders and stores the references
without judging them.

```powershell
.\gradlew.bat :app:updateDebugScreenshotTest --no-daemon
```

Review the rendered images under the module's screenshot test output before committing them, then run
`:app:validateDebugScreenshotTest` to confirm the gate passes against the new references.

## Running on an emulator

Install the debug build and grant the two permissions the monitor needs before it can enable.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set tools.zach.app.pixelspeed SYSTEM_ALERT_WINDOW allow
adb shell pm grant tools.zach.app.pixelspeed android.permission.POST_NOTIFICATIONS
```

The overlay permission is the `SYSTEM_ALERT_WINDOW` app op. `OverlayPermissionSource` reads it through
`Settings.canDrawOverlays` and watches it for external changes, since the platform sends no broadcast
on a revoke. `POST_NOTIFICATIONS` is a runtime permission the foreground service's notification needs;
granting it over adb skips the system permission dialog during a manual test.

## The debug-only probe activity

`ProbeActivity` (`app/src/debug/java/.../debug/ProbeActivity.kt`) exists only in debug builds, is
exported, and is gated on the `android.permission.DUMP` permission, which the shell holds. Start it
over adb with an extra to drive a measurement:

```powershell
adb shell am start -n tools.zach.app.pixelspeed/.debug.ProbeActivity --ez measure true
adb shell am start -n tools.zach.app.pixelspeed/.debug.ProbeActivity --ez cadence true
```

`measure` reads the total and Wi-Fi (`wlan0`) counters every 100 ms for ten seconds and logs the
longest run of unchanged readings, which shows the platform's counter cache plateau. `cadence` reads
the total counter on exact boundaries of three tick periods (1000, 1050, and 1100 ms) for twenty ticks
each, and logs how many reads repeated the previous value, which is the share of ticks that would read
stale data at that period. Both log to logcat under the tag `PixelSpeedProbe`. The activity also logs
every tap it receives, with the platform's obscured and partially-obscured touch flags, which is how
the overlay's touch passthrough is verified.

## The side flip on the emulator

The side flip reads the status bar through an accessibility service. Turn it on from the shell
instead of the settings page, and never `am force-stop` the package afterwards: Android disables an
accessibility service whose app is force-stopped, and the row in Position reads off until it is
enabled again.

```sh
adb shell settings put secure enabled_accessibility_services   tools.zach.app.pixelspeed/tools.zach.app.pixelspeed.service.StatusBarReaderService
adb shell settings put secure accessibility_enabled 1
```

Icons in the start zone come from notifications. The shell posts them only as a non-root shell with
the notification permission: `adb unroot`, then `adb shell pm grant com.android.shell
android.permission.POST_NOTIFICATIONS`, then `adb shell cmd notification post -S bigtext -t Title
tag1 Body`. Four such notifications collapse into one status bar icon. With `setprop
log.tag.PixelSpeed DEBUG` every measurement is logged as `Status bar read`.
