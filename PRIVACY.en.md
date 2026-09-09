# Privacy policy

Pixel Speed collects no data.

## What the app reads

The app reads the phone's own network traffic counters through Android's `TrafficStats` interface,
computes a speed from them, and draws it on screen. The counters never leave the phone.

With the side flip turned on, an accessibility service reads the status bar window: which icons it
shows and where they sit. It reads no other window and no content. That stays on the phone too.

Android also tells the service when windows on screen change, including other apps' windows. That
signal only tells it when to look at the status bar again. Of any window at the top of the screen it
reads only which app owns it, to tell the status bar apart. It reads content from no window but the
status bar.

The app declares no network permission, so it cannot send anything anywhere. It contains no
analytics, no crash reporting service, no advertising, and no third-party data SDK.

## What the app stores

Your settings (profiles, position, appearance, behavior, pause state) are stored in a file inside the
app's private storage on the phone. Android's backup may include that file according to your
device's backup settings. Nothing in it identifies you.

Export writes your settings to a file you pick. Import reads a file you pick. Both happen only when
you choose them.

If the app crashes, it keeps a record of that crash in its private storage: the time, the app
version, the stack trace, and the app's own last log lines. Help > Report a problem builds a
diagnostics file on request: the app version, the phone model and Android version, the display
geometry, which permissions are granted, the readout's state, your settings export, the app's recent
log lines, and that crash record if there is one. It holds no account, device id, serial number, or
location, and nothing from other apps. The app never sends it; you read it, then share it yourself
if you want to.

## Permissions

Every permission the app declares, and what it is for:

- Display over other apps (SYSTEM_ALERT_WINDOW): draws the readout.
- Notifications (POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS): the foreground service
  notification that carries pause and resume, and the Live Update form of it on Android 16 and
  later.
- Foreground service (FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE): keeps the readout
  running while the overlay is on.
- Accessibility service (BIND_ACCESSIBILITY_SERVICE): optional, off by default. Reads the status
  bar's icons and their positions so the readout can move to the side of the camera with room.
  Nothing else.
- Network state (ACCESS_NETWORK_STATE): tells whether the phone is online and which link is
  active, so the readout shows dashes offline and counts the right interface. Not internet
  access.
- Run at startup (RECEIVE_BOOT_COMPLETED): starts the overlay after a reboot when you turn that
  on.
- Ignore battery optimizations (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS): offered on phones whose
  maker closes background apps, so the overlay keeps running. Optional.

There is no network permission.

## Contact

Questions about this policy: open an issue at https://github.com/zachthedev/pixel-speed/issues.

This policy applies to Pixel Speed from version 1.0.0. Changes to it are published in this file.
