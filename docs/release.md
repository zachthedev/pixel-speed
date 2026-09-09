# Release

## Versioning

A git tag is the only place a version is written. The app-versioning plugin, applied in
`app/build.gradle.kts`, reads the tag reachable from the build and derives both values from it.
Release tags follow `vMAJOR.MINOR.PATCH`. `versionName` drops the `v`. `versionCode` is
`major * 10000 + minor * 100 + patch`, so `v1.2.3` builds version code 10203. A build with no tag
carries an empty version, which is fine for a debug or a local release APK. An App Bundle needs
a version code, so `:app:bundleRelease` runs only on a tagged checkout.

## The release pull request

`.github/workflows/release.yml` runs release-please on every push to `main`, with
`release-please-config.json` and `.release-please-manifest.json` at the repository root. It reads the
conventional commits since the last release and opens or updates a pull request that changes
`CHANGELOG.md` and the manifest file, and nothing the Gradle build reads. Merging that pull request
creates the tag and the GitHub Release. The first release is 1.0.0 by the config's
`initial-version`.

The About screen shows release notes from `CHANGELOG.md`, which the build copies into the APK's
assets. Before the first release the file does not exist and the screen says so.

## The signed build

A second job in the same workflow runs right after release-please creates a release, on the tag it
made. A tag pushed with the workflow's own token starts no workflow of its own, so the job cannot
hang off a tag push. The same job runs by hand for an existing tag through `workflow_dispatch`.

The job refuses to run without the `RELEASE_KEYSTORE_BASE64` secret and the `RELEASE_CERT_SHA256`
repository variable. It decodes the keystore into the runner's temporary directory, writes a
`keystore.properties` pointing at it from the `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and
`RELEASE_KEY_PASSWORD` secrets, and builds with `REQUIRE_RELEASE_SIGNING` set, so a missing keystore
fails the build instead of signing with the debug key. It then checks that the APK's certificate
digest equals `RELEASE_CERT_SHA256`, that the package name is the app's, and that `versionName`
equals the tag without its `v`, attaches the APK to the GitHub Release as `pixel-speed-vX.Y.Z.apk`,
and keeps the App Bundle as a workflow artifact for the Play upload. The keystore and the properties
file are deleted at the end of the job whatever the outcome.

## The keystore

The keystore lives outside the repository. `.gitignore` excludes `*.jks`, `*.keystore`, and
`keystore.properties`. `app/build.gradle.kts` reads `keystore.properties` from the repository root
when it exists and builds the `release` signing config from `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword`. Without the file the release build type signs with the debug key, so
a local release build still installs on an emulator.

Create the key once, on the release machine, and back the file up somewhere that outlives the
machine. Losing it ends the update chain for every sideloaded install.

```powershell
$keystore = "$env:USERPROFILE\keys\pixel-speed.jks"
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $keystore -alias release `
    -keyalg RSA -keysize 4096 -validity 10000
```

Then write the four values into `keystore.properties` at the repository root, and add the same four
as repository secrets on GitHub. The keystore goes in base64 through standard input, never through
the clipboard or a command-line argument, since both keep a copy in a history.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)) |
    gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS --body release
gh secret set RELEASE_KEY_PASSWORD
```

The release job also needs the certificate's SHA-256 digest as a repository variable, which is
public information:

```powershell
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --print-certs .\app\build\outputs\apk\release\app-release.apk
gh variable set RELEASE_CERT_SHA256 --body <the digest, no colons>
```

## Reproducible builds

The release build type keeps PNGs uncrunched, excludes VCS metadata, and excludes Play's encrypted
dependency block, so one source tree gives one set of bytes. The licenses list the About screen shows
is generated at build time in offline mode from the resolved dependency graph. Checked so far: the
generated JSON and the APK are byte-identical across two clean builds on the release machine. The
check on F-Droid's own builder happens with the fdroiddata submission.

## Google Play

Play is the first channel. A personal developer account needs identity verification, then a closed
test with 12 opted-in testers for 14 days before production is available.

### Account and app entry

1. Create the developer account at https://play.google.com/console under the legal name, with a
   government ID and a card under that name. Verification takes days.
2. Create the app: name "Pixel Speed", default language English (United States), app, free.
3. Enable Play App Signing and upload the release key as the app signing key at enrollment, so a Play
   install and a sideloaded APK share one signature and update each other. Export the key with
   Google's PEPK tool from the Console's instructions, from the keystore above.
4. Store listing: title and descriptions from `fastlane/metadata/android/<locale>`, the icon and
   the feature graphic from `en-US/images`, screenshots from `en-US/images/phoneScreenshots`.
5. Privacy policy URL: https://github.com/zachthedev/pixel-speed/blob/main/PRIVACY.en.md
6. App content declarations, in the Console's Policy section, with the text below.
7. Closed testing: create a track, upload the App Bundle from the tag workflow's artifact, add 12
   testers by email list, share the opt-in link, and keep them opted in for 14 days.
8. Apply for production access after the 14 days, then promote the same build.

### Declaration text

**Foreground service, special use.** "Pixel Speed draws a small always-on-top overlay in the status
bar showing the live download and upload speed. The user turns it on, and it stays until the user
pauses it or turns it off. No listed foreground service type fits a persistent overlay, so the
service declares specialUse. The service reads the phone's traffic counters once a second and
updates the overlay. It has no network permission. The demo video shows the overlay running, a
pause and a resume from the Quick Settings tile, and the app's Home screen."

**Ignore battery optimizations.** "The app's only function is a persistent on-screen speed readout.
On phones from makers other than Google, the maker's battery rules stop background apps within
minutes, which ends the overlay the user asked for. The app asks for the exemption only on those
phones, once, during setup, on a screen that explains it, and works without it. Without the
exemption the core function breaks on those phones."

**Display over other apps.** "The overlay is the app. Setup opens the system page for the user to
grant it. The app never draws anything but the readout."

**Accessibility API.** "Pixel Speed declares an AccessibilityService for one optional feature, the
side flip: the readout moves to the side of the camera cutout that has room, which needs the real
positions of the status bar's icons. The service reads only the status bar window, only the icons'
bounds and content descriptions, and nothing else on screen. Its configuration names no package,
because window-changed events carry no package name and cannot be scoped. The service acts on window
changes and on content changes from System UI only, as a signal to read the status bar again, and
never reads an event's source or text. Of a window at the top of the screen it reads only the owning
package, to tell the status bar apart, and it reads content from no window but the status bar. It is
off by default, isAccessibilityTool
is false, and it is turned on only by the user in Accessibility settings after an in-app page during
setup that shows the feature and states exactly what is read. Nothing leaves the device; the app has
no network permission. The privacy policy carries the same statement." The prominent disclosure is the
onboarding step named Follow the icons and the Position setting's row of the same name; both show
before the system page opens.

**Data safety.** Collects no data, shares no data. Nothing is encrypted in transit because nothing
leaves the device. There is nothing to delete because nothing is collected. The app declares no
INTERNET permission. Settings stay in app-private storage on the device.

**Content rating.** Utility. No user-generated content, no ads, no purchases.

### Demo video

The special-use declaration asks for a video of the feature. Record it on the API 37 emulator: the
overlay over the launcher with traffic flowing, a pause and a resume from the tile, then the Home
screen. Host it anywhere the Console accepts a link, such as an unlisted YouTube video.

### Play uploads from the workflow

The Gradle Play Publisher plugin is applied to `:app` and stays disabled until a service account
key path is set in `PLAY_SERVICE_ACCOUNT_FILE`. The release workflow sets it from the
`PLAY_SERVICE_ACCOUNT_JSON` secret and runs `:app:publishReleaseBundle`, which uploads the signed
App Bundle to the internal track. Play requires the first bundle of a new app to go through the
Console by hand, so the secret is added after that upload: create a service account in Google
Cloud, grant it release access to the app under the Console's Users and permissions page, download
its JSON key, and store the file's contents with `gh secret set PLAY_SERVICE_ACCOUNT_JSON` from
standard input. Promotion from internal to the closed test and to production stays in the Console.

## GitHub Releases and Obtainium

Each GitHub Release carries exactly one universal APK, so Obtainium tracks the repository URL and
updates silently. `versionName` equals the tag without its `v`, which is what Obtainium compares.

On Android 15 and later an APK installed from a browser or a file has its overlay switch locked
behind "Allow restricted settings". Obtainium, F-Droid clients, and Play are session installers and
are exempt. The README carries the three unblocking steps for the direct path.

## F-Droid

Submit to fdroiddata after the first tag exists. The file `metadata/tools.zach.app.pixelspeed.yml`
in the fdroiddata repository:

```yaml
Categories:
  - System
License: Apache-2.0
SourceCode: https://github.com/zachthedev/pixel-speed
IssueTracker: https://github.com/zachthedev/pixel-speed/issues
Changelog: https://github.com/zachthedev/pixel-speed/blob/main/CHANGELOG.md

AutoName: Pixel Speed

RepoType: git
Repo: https://github.com/zachthedev/pixel-speed

Binaries: https://github.com/zachthedev/pixel-speed/releases/download/v%v/pixel-speed-v%v.apk

Builds:
  - versionName: 1.0.0
    versionCode: 10000
    commit: v1.0.0
    subdir: app
    sudo:
      - apt-get update
      - apt-get install -y openjdk-25-jdk
    gradle:
      - yes

AllowedAPKSigningKeys: <sha256 of the release certificate, from apksigner verify --print-certs>

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 10000
```

F-Droid's build image is Debian trixie with OpenJDK 21 and toolchain downloads turned off, so the
`sudo` block installs Debian's `openjdk-25-jdk`, which Gradle's toolchain detection finds without a
download. `Binaries` names the APK from the GitHub Release. F-Droid builds the tag itself, compares its build
with that APK, and publishes the zachthedev-signed APK when the two match, with
`AllowedAPKSigningKeys` pinning which certificate that binary must carry. `fastlane/metadata/android/<locale>` in this repository feeds the F-Droid listing: title,
short and full description, a changelog per version code, the icon, the feature graphic, and the
phone screenshots.
