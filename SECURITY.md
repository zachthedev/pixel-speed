# Security

## Reporting

Report a vulnerability privately through GitHub:
[Report a vulnerability](https://github.com/zachthedev/pixel-speed/security/advisories/new). Do not open
a public issue for it. Expect an acknowledgment within a week and a fix in the next release once the
report is confirmed.

## Supported versions

The latest release on Google Play, GitHub Releases, and F-Droid is supported. Older versions get no
separate fixes; update to the current one.

## What the app exposes

Pixel Speed has no network permission and stores its settings in app-private storage. Its surface
is small and worth knowing when you look for problems:

- An exported broadcast receiver for automation apps, gated by the Automation broadcasts setting
  (off by default), which can only pause, resume, or toggle the readout.
- A settings import that parses a JSON file the user picks. Every value is bounded on import.
- An accessibility service, off by default, that reads only the status bar window's icon bounds
  for the side flip feature. See [PRIVACY.en.md](PRIVACY.en.md) for exactly what it reads.
- An always-on-top overlay window that draws the readout and nothing else. It is never touchable
  outside edit mode, and above the platform's touch-obscuring opacity limit the app warns before it
  lets the user go there.
