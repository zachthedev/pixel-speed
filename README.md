# Pixel Speed

Live network speed, always on top. Pixel Speed draws a small readout of your download and upload
speed in the status bar, beside the camera cutout, over whatever is on screen.

It reads the phone's own traffic counters once a second. No network permission, no analytics,
nothing leaves the phone.

## Install

**Google Play** is the main channel:
[Pixel Speed on Google Play](https://play.google.com/store/apps/details?id=tools.zach.app.pixelspeed),
live once the listing clears Play's closed test.

**Obtainium** installs and updates straight from this repository's releases. Add
`https://github.com/zachthedev/pixel-speed` as an app in Obtainium.

**Direct APK**: every [release](https://github.com/zachthedev/pixel-speed/releases) carries one
universal APK. Android 15 and later block the overlay switch for an app installed from a browser or a
file. To unblock it:

1. Long-press the Pixel Speed icon and open App info.
2. Tap the menu in the top corner, then Allow restricted settings.
3. Open Pixel Speed again and turn on the overlay.

Obtainium and Play installs need none of that.

## What it does

- A readout in the status bar: download and upload, one line or stacked, at the cutout or in a
  corner, with nine looks and a color that follows your wallpaper.
- Pause from the app, the Quick Settings tile, the notification, or a launcher shortcut, for five
  minutes, an hour, or until you resume.
- Hides itself when the screen is off, the phone is locked, a fullscreen app runs, the link is idle,
  or the phone turns sideways, each one a switch.
- Profiles with their own content, position, look, and rules, switched from the tile.
- Regular mode shows the settings most people change. Advanced mode reveals every setting in place.
- Works on any phone running Android 12 or later, and is designed for the newest Android first.

## Privacy

Pixel Speed collects nothing. See [PRIVACY.en.md](PRIVACY.en.md).

## Build

See [docs/build-and-run.md](docs/build-and-run.md). The app is Kotlin and Jetpack Compose, built
with the Gradle wrapper on JDK 25.

## Translating

Translations are edited on [Weblate](https://hosted.weblate.org/projects/pixel-speed/). Pick a
language there, or ask for a new one; Weblate opens the pull request.

## License

[Apache License 2.0](LICENSE).
