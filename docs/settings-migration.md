# Settings migration

## The schema version stamp

`Settings.schemaVersion` (`core/.../settings/Settings.kt`) defaults to
`SettingsMigrations.CURRENT_VERSION`, which is `1`. Every persisted or imported settings object
carries this stamp, and every read stamps the result with the build's current version regardless of
what it started at.

## How `SettingsMigrations` walks step functions

`SettingsMigrations.migrate(json: JsonObject)` (`core/.../settings/SettingsMigrations.kt`) delegates to
an internal `migrate(json, steps, target)` that a test can call directly. `steps` is a map from a
version number to a `(JsonObject) -> JsonObject` function that upgrades an object at that version to
the next one. Starting from the object's own `schemaVersion` field (read as `0` when absent, for a file
written before the stamp existed), the walk repeatedly looks up the step for the current version and
applies it, incrementing the version each time, until either the target is reached or no step exists
for the current version. The final object is stamped with `target` regardless of how far the walk got,
so a version between two known steps still ends up marked current. `steps` is empty, since
the schema has had one version so far.

## How the serializer refuses a bad stamp

`SettingsSerializer.readFrom` (`core/.../settings/SettingsSerializer.kt`) parses the stored file,
reads the `schemaVersion` field, and throws `CorruptionException` when: the root is not a JSON object,
the field is present but not an integer, the field's value is greater than
`SettingsMigrations.CURRENT_VERSION` (a file from a newer build would silently drop fields this build
does not know on the next write), the migrated object's `profiles` array is empty, the text fails to
parse as JSON for the current schema, or the parsed shape does not match `Settings`. Jetpack DataStore
calls the registered corruption handler on any of these, which replaces the stored file with
`SettingsSerializer.defaultValue`, a fresh default `Settings`.

## How import differs from the stored file

`SettingsImport` (`core/.../settings/SettingsImport.kt`) parses a user-supplied export file with a
stricter reader than the on-device store: `strictJson` sets `ignoreUnknownKeys = false`, so a top-level
key the current schema does not recognize fails the import outright rather than being silently dropped.
`SettingsImport.parse` additionally refuses: text over `MAX_FILE_BYTES` (1,000,000 characters), a
`profiles` array longer than `SettingsBounds.PROFILE_COUNT.last` before decoding, a missing or absent
`schemaVersion` field, and a file newer than `SettingsMigrations.CURRENT_VERSION`. After migrating and
decoding, `validate(settings)` checks the whole object: the profile count is within
`SettingsBounds.PROFILE_COUNT`, every profile id is unique, `activeProfileId` names one of the
profiles, and every profile passes `validateProfile`, which checks every numeric field named in
`SettingsBounds` against its range and refuses a control or formatting character in a profile's `id`
or `name`. `SettingsImport.serialize` writes with the lenient, unknown-key-tolerant settings, so a file
exported from this build still imports into an older one that does not know every field.

## Adding a migration step

When a field's shape or meaning changes in a way an old file cannot decode as is:

1. Bump `SettingsMigrations.CURRENT_VERSION`.
2. Add an entry to the `migrations` map keyed by the version being migrated away from, whose function
   takes the old shape's `JsonObject` and returns one that decodes under the new `Settings` schema.
3. Add a test that builds a `JsonObject` at the old version, runs it through the internal
   `migrate(json, steps, target)`, and asserts the result decodes to the intended new value.
4. If the changed field also has a bound in `SettingsBounds`, update `SettingsImport.validateProfile`
   to check the new field, and add an import test that exercises both a valid and an out-of-range
   value.
