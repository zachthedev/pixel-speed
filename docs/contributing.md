# Contributing

## Commits

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org). `release-please`
reads them to build `CHANGELOG.md` and decide the next version. See `release.md`.

## Before committing

Run the same gate CI runs, described in `build-and-run.md`: format check, both modules' lint, both
modules' unit tests, the screenshot test validation, and a release assembly. Fix a lint warning rather
than suppressing it. `warningsAsErrors` is set for both modules, so a warning fails the build the same
as an error.

## Changing a dependency

`gradle/verification-metadata.xml` pins a checksum for every artifact the build resolves, on every
platform. A version bump, by hand or from Dependabot, fails the build until the file carries the new
artifacts. Run the gate's tasks once with `--write-verification-metadata sha256` in front of them,
read the diff (only the bumped artifacts may appear), and commit the file with the bump. Two traps:
Windows records the Windows `aapt2` jar alone, so the Linux and macOS entries for a new AGP are
added by hand from Google Maven; and Gradle verifies BOM and parent POM metadata only when it
downloads them, so a write on a warm cache never records them and CI, always cold, fails on them.
Run the write with an empty `GRADLE_USER_HOME` (a throwaway container does), or expect to add
the files CI names by hand.

## Formatting

The project uses ktfmt in Google style (`ktfmt { googleStyle() }` in both build files). Run
`.\gradlew.bat ktfmtFormat` to rewrite files in place, or `ktfmtCheck` to verify without writing.

## Comments

A comment explains why the code is shaped the way it is, not what it does or how it came to be that
way. No design document, phase, decision, or history belongs in a comment: state the constraint the
code satisfies today, in the present tense, as if no earlier version ever existed.

## Tests

Tests are table-driven or parameterized by default and assert the behavior a requirement implies,
not whatever the code returns today. Both modules run JUnit Jupiter on the JVM. `:core` stubs
`android.util.Log` to no-ops through `testOptions { unitTests { isReturnDefaultValues = true } }`,
so core code under test can log without a mock. `:app` has no such stub, so a JVM test there keeps
platform calls behind an injected seam. Compose screens are covered by the Preview screenshot tests
under `app/src/screenshotTest`.

## Adding a string

The app ships six locales beyond the default: German, Spanish, French, Hindi, Brazilian Portuguese, and
Simplified Chinese, each under its own `values-*` resource directory. The default strings are split by screen under `app/src/main/res/values/strings*.xml`. Add a new
string to the matching file there. Translations come through Weblate
(https://hosted.weblate.org/projects/pixel-speed/), which opens a pull request per language; a
pull request merges once its language is complete, since lint treats a string missing from any
locale as an error. A maintainer adds the same key to every locale directory in the same change
when a string must ship before its translations arrive. Unit symbols and the app name are marked
non-translatable on purpose. Only `strings*.xml` files may sit under a `values-<language>`
directory: Weblate reads every such directory as a language, so a strings file under a qualifier
such as `values-v34` would read as a language named v34.

A short XML comment above a string is what Weblate shows a translator as context. Add one to any
string whose meaning depends on the screen it sits on, such as a bare choice label.

## Adding a settings field

1. Add the field to the relevant settings data class in `core/.../settings/Settings.kt`, with a
   default value that matches the behavior before the field existed.
2. If the field has a numeric range, add the range to `SettingsBounds`
   (`core/.../settings/SettingsBounds.kt`) and reference it from any UI slider or choice.
3. Add a check for the field to `SettingsImport.validateProfile` or `validate`
   (`core/.../settings/SettingsImport.kt`) so an imported file cannot carry an out-of-range value.
4. If the field changes the shape or meaning of an existing stored field rather than adding a new one
   with a safe default, add a migration step. See `settings-migration.md`.
5. Add a row for the field to the relevant settings screen under `app/.../ui/settings/`, using a
   `SettingsKit` row kind (`SwitchRow`, `SegmentedRow`, `ChoiceRow`, `PresetRow`, `SliderRow`, or
   `ColorRow`) that matches the field's shape.
6. If the field affects a summary line shown on Home or in a card title, update the corresponding
   summary function under `app/.../ui/home/HomeSummaries.kt`.
7. Add or extend a test for the new field's bound and its import validation.
