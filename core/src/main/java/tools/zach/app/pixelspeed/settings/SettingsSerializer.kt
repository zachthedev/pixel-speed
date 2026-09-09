package tools.zach.app.pixelspeed.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private val settingsJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
  prettyPrint = true
}

/**
 * Persists [Settings] as pretty-printed JSON, migrating older schema versions on read. A file from
 * a newer build is corruption to this one: reading it would drop the fields this build does not
 * know on the next write, so the store's corruption handler replaces it instead.
 */
object SettingsSerializer : Serializer<Settings> {
  override val defaultValue: Settings = Settings()

  override suspend fun readFrom(input: InputStream): Settings {
    val text = input.readBytes().decodeToString()
    try {
      val root =
        settingsJson.parseToJsonElement(text) as? JsonObject
          ?: throw CorruptionException("Settings file root is not a JSON object.")
      // An absent stamp is a file from before stamps; a stamp that is not an integer is a file
      // this build cannot place, and reading it as version 0 would drop its fields on the next
      // write.
      val stamp: JsonElement? = root["schemaVersion"]
      val version: Int =
        when (stamp) {
          null -> 0
          is JsonPrimitive ->
            stamp.intOrNull
              ?: throw CorruptionException("Settings file schemaVersion is not an integer.")
          else -> throw CorruptionException("Settings file schemaVersion is not an integer.")
        }
      if (version > SettingsMigrations.CURRENT_VERSION) {
        throw CorruptionException(
          "Settings file is at schema version $version, newer than this build's " +
            "${SettingsMigrations.CURRENT_VERSION}."
        )
      }
      val migrated = SettingsMigrations.migrate(root)
      val profiles = migrated["profiles"]
      if (profiles is JsonArray && profiles.isEmpty()) {
        throw CorruptionException("Settings file has no profiles.")
      }
      return settingsJson.decodeFromJsonElement(Settings.serializer(), migrated)
    } catch (exception: SerializationException) {
      throw CorruptionException(
        "Settings file is not valid JSON for the current schema.",
        exception,
      )
    } catch (exception: IllegalArgumentException) {
      throw CorruptionException(
        "Settings file does not match the expected schema shape.",
        exception,
      )
    }
  }

  override suspend fun writeTo(t: Settings, output: OutputStream) {
    val text = settingsJson.encodeToString(Settings.serializer(), t)
    output.write(text.encodeToByteArray())
  }
}
