# Architecture

## Modules

The project is a Kotlin and Jetpack Compose Android app split into two Gradle modules.

`:core` (`tools.zach.app.pixelspeed.core`) holds pure Kotlin plus Android framework calls: counter
reading and sampling (`sampling`), speed formatting (`format`), settings storage, schema, and bounds
(`settings`), platform signal sources (`signals`), state reduction (`state`), and overlay placement
math (`overlay`). It has no Compose dependency and its tests run on the JVM.

`:app` (`tools.zach.app.pixelspeed`) holds Hilt wiring, the Compose UI, the foreground service, the
overlay window, the Quick Settings tile, and onboarding, help, and about screens. It depends on
`:core`.

## Data flow: counters to pixels

`TrafficStatsCounterSource` reads cumulative byte counters from `android.net.TrafficStats` for one
`CounterSourceKind`: all interfaces, mobile only, non-mobile only, the default network's interface, or
the sum of every physical interface. `MonitorService` rebuilds the source whenever the physical
interface set or the default interface changes, so the sampler always reads the counters that match
the active profile's `counterSource`.

`SpeedSampler` runs a fixed-cadence loop over a `CounterSource`. Each tick it takes a fresh baseline,
computes bytes per second from the delta since the last baseline, applies an exponential moving
average keyed by the profile's `Smoothing`, and emits a `SpeedSample`. It resyncs to the tick boundary after a scheduling gap instead of replaying missed ticks, holds the
previous sample for one extra interval when the counters did not move so a single stale read does
not flash to zero, and re-baselines on a counter rollback. On Android 15 and later the platform
caches the counters for a second, so `SettingsBounds.samplerTickMs` places a one-second tick 100 ms
past that cache. A
`CounterReading` with `supported = false` emits a zero sample marked unsupported, which the formatter
renders as dashes.

`MonitorService` (`app/.../service/MonitorService.kt`) owns the sampler and the overlay window for as
long as the monitor is enabled. It combines `SettingsStore.settings`, `ScreenStateSource.state`,
`NetworkStateSource.defaultNetwork`, `PowerSaveSource.powerSaveOn`, an idle flag, a fullscreen flag,
and `OverlayPermissionSource.granted` into `SuppressionRules.evaluate(...)`, which returns the set of
active `SuppressionReason` values (screen off, keyguard, offline, idle, fullscreen, landscape, battery
saver). It builds `MonitorInputs` from the settings, the suppression set, and the pause state, and
passes them to `MonitorReducer.reduce(...)`, a pure function that returns one of `MonitorState.Off`,
`MonitorState.NeedsPermission`, `MonitorState.Paused`, or `MonitorState.Running`. The service writes
the result to `MonitorStateHolder`, attaches or detaches the `OverlayWindow`, and shows or hides it
based on `MonitorState.Running.visible`.

Every state and settings change, plus each new sample for a sink that wants them, is fanned out
through `ReadoutPublisher` to the set of `ReadoutSink` instances: the built-in `NotificationSink` and
any sink bound in Hilt's multibinding set. `ReadoutPublisher` runs each sink in its own coroutine on a
background dispatcher, applies a timeout per sink, and reports a sink that throws or times out to a
callback without stopping the other sinks or the service.

## Settings

`Settings` (`core/.../settings/Settings.kt`) is the root object persisted by a typed Jetpack DataStore
through `DataStoreSettingsStore`. It carries the app-wide switches (`enabled`, `startOnBoot`, `paused`,
`pausedUntilEpochMillis`, `automationIntents`, `onboardingCompleted`, `advancedMode`) and a list of
`Profile` values. A `Profile` bundles `ContentSettings`, `PositionSettings`, `AppearanceSettings`,
`BehaviorSettings`, `PauseSettings`, `NotificationSettings`, and a `counterSource`. `Settings.
activeProfile` resolves `activeProfileId` against the list, falling back to the first profile or to
`SettingsDefaults.defaultProfile` when the list is empty.

`SettingsSerializer` reads and writes the on-device store as pretty-printed JSON. On read it checks the
`schemaVersion` stamp, refuses a file newer than the build's `SettingsMigrations.CURRENT_VERSION` as
corruption, runs `SettingsMigrations.migrate` on the parsed object, and refuses an empty `profiles`
array. `SettingsImport` applies the same migration and a stricter set of checks (see
`settings-migration.md`) to a user-facing export file.

`MonitorController` is the one place that changes what the monitor should be doing. Every surface
(Home, the Quick Settings tile, notification actions, shortcuts, receivers) calls it. It persists
through `SettingsStore.update` first and the service reacts to the new settings.

## Signal sources

`core/.../signals` holds the platform signal sources `MonitorService` combines:

- `NetworkStateSource`: the default network's transport and interface name, and the set of interface
  names for every up physical (non-VPN) network, both through `ConnectivityManager` callbacks.
- `ScreenStateSource`: whether the display is interactive and whether the keyguard is showing, from
  screen-on/off, user-present, and configuration-changed broadcasts.
- `PowerSaveSource`: whether battery saver is on.
- `OverlayPermissionSource`: whether the app may draw over other apps, read from the
  `SYSTEM_ALERT_WINDOW` app op and re-read on every op change, since the platform sends no broadcast on
  a revoke.

## Diagnostics

`AppLog` (`core/.../log/AppLog.kt`) is the app's only logger. Every line goes to logcat under the
`PixelSpeed` tag and into a ring of the newest 300 lines, so a report carries what the process did
without a log file or a service watching logcat. `MonitorService` logs its state changes, the
display geometry at start, and each overlay show and hide; `OverlayContent` logs a placement when it
changes. No log line carries user content, and a kept line has any content or file URI
replaced by a placeholder, since a document provider names the user's file in its exception
message. The crash record is excluded from backup and device transfer.

`CrashRecorder` (`app/.../diagnostics`) is the process's uncaught-exception handler, installed by
`PixelSpeedApp`. It writes `files/diagnostics/last-crash.txt` (time, version, thread, stack trace,
the ring) and then defers to the handler that was installed before it, so the platform's crash
dialog and Play vitals see the crash unchanged. One file, overwritten by the next crash.

`DiagnosticsCollector` gathers the report: package facts, `Build` fields, the display geometry the
placement uses, the permission and service state, `MonitorStateHolder`, the settings export, the
ring, and the crash record. `DiagnosticsReport` in `core` renders it as plain text, so the rendering
is testable on the JVM. The Help page `HelpReportProblemScreen` writes the text to
`cache/diagnostics/` and offers it through a `FileProvider` (authority
`<applicationId>.diagnostics`, paths in `res/xml/diagnostics_paths.xml`) with the share sheet.
Nothing is sent by the app.

## Placement

`OverlayPlacement.place(...)` (`core/.../overlay/OverlayPlacement.kt`) decides where the readout sits
and which look it draws, from a `PlacementRequest` (the user's anchor, style, offsets, and clamps) and
a `DisplayGeometry` (the display's size, insets, cutout rectangles, orientation, and font scale).

A status bar anchor beside a centered cutout tries a `Placement.Flank`, one half on each side of the
hole. If either half would collide with the clock or the status icons it falls back to a
`Placement.Single` on whichever side has more room. `StatusBarFit.AUTO` tries each look in
`OverlayPlacement.AUTO_ORDER` (widest to narrowest) and keeps the first that fits the free span and the
bar's height. `StatusBarFit.MANUAL` keeps the requested look regardless of fit. `StatusBarFit.SLIM`
renders the ribbon look instead of a pill, centered on the cutout. An anchor outside the status bar
(the four corners or a custom drop point) places its own look at a fixed or dragged position. Every
placement applies the request's offsets last and clamps to the display bounds.

Under `AUTO`, when neither side of a centered cutout fits any look, the placement falls back to the
ribbon only when the profile's ribbon fallback switch is on (an advanced-mode row on the Appearance
screen). Otherwise the narrowest look stays on the roomier side, overlapping a little, so the readout
never changes shape on its own.

With the side flip on, `StatusBarReaderService` (`app/.../service/StatusBarReaderService.kt`) reads
the status bar's icon bounds through the accessibility API and publishes the free span to
`StatusBarReadings`. `MonitorService` copies the latest reading into `DisplayGeometry.measuredBar`
while the active profile's `sideFlip` is on, and `OverlayPlacement` prefers that reading over its
clock and icon width estimates, keeping 2 dp against a measured edge and 8 dp against an estimate.
The service acts on window-changed events from any package and on content-changed events from System
UI, as triggers to read the bar again; it never reads an event's content, and every other event is
dropped.

`OverlayWindow` (`app/.../overlay/OverlayWindow.kt`) owns the single `WindowManager` overlay window: it
builds a window context on the default display, attaches or detaches the Compose host, moves the
window by dp offset, toggles visibility by alpha so the platform's "displaying over other apps" notice
does not reappear on every hide, and drives drag-to-reposition in edit mode with snap points at the
nine grid spots.

## UI structure

The app navigates with Navigation 3 (`androidx.navigation3`). `NavigationKeys.kt` declares one
`@Serializable data object` per destination: `Home`, each settings screen (`ContentSettings`,
`PositionSettings`, `AppearanceSettings`, `BehaviorSettings`, `PauseSettings`, `ProfilesSettings`,
`NotificationSettings`, `SystemSettings`), `Onboarding`, `Help` and its sub-pages, and `About` and its
sub-pages.

`AppNavigation` (`app/.../Navigation.kt`) reads the window's adaptive directive to decide whether the
list and detail panes show side by side. On a wide window it uses `ListDetailSceneStrategy`: Home is
the list pane, the Content settings screen is the detail pane's placeholder before any choice, and a
key in the top-level `listChoices` set replaces the current detail entry instead of stacking on it. On
a narrow window every destination pushes onto a single back stack.

Settings screens share `SettingsKit` (`app/.../ui/settings/SettingsKit.kt`): `SettingsScreen` supplies
the Pixel Settings page scaffold, and `settingsCard` groups a screen's rows into a segmented card.
`SwitchRow`, `SegmentedRow`, `ChoiceRow`, `PresetRow`, `SliderRow`, `LinkRow`, `ColorRow`, and `InfoRow`
are the row kinds every settings screen composes from.

Regular mode shows a curated set of rows and Advanced mode shows every row in place. A row hidden in
regular mode still shows while its value is in effect, which means it can hide, move, size, or
reformat the readout, or open the app to other apps. `rememberRevealed` latches that per visit: once
a row shows it stays shown until the screen is left, the mode changes, or the active profile
changes.
