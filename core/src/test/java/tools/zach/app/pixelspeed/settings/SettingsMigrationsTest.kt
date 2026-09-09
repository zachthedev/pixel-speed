package tools.zach.app.pixelspeed.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsMigrationsTest {
  @Test
  fun `an object with no schemaVersion comes out stamped at the current version`() {
    val migrated = SettingsMigrations.migrate(buildJsonObject { put("enabled", true) })

    assertEquals(
      SettingsMigrations.CURRENT_VERSION,
      (migrated["schemaVersion"] as JsonPrimitive).intOrNull,
    )
  }

  @Test
  fun `an object already at the current version is unchanged besides the stamp`() {
    val input = buildJsonObject {
      put("schemaVersion", SettingsMigrations.CURRENT_VERSION)
      put("enabled", true)
    }

    val migrated = SettingsMigrations.migrate(input)

    assertEquals(
      SettingsMigrations.CURRENT_VERSION,
      (migrated["schemaVersion"] as JsonPrimitive).intOrNull,
    )
    assertEquals(true, (migrated["enabled"] as JsonPrimitive).booleanOrNull())
  }

  @Test
  fun `the walk runs every step in order and stamps the target`() {
    val steps: Map<Int, (JsonObject) -> JsonObject> =
      mapOf(
        0 to { json -> JsonObject(json + ("a" to JsonPrimitive(true))) },
        1 to { json -> JsonObject(json + ("b" to JsonPrimitive((json["a"] != null)))) },
      )
    val input = buildJsonObject { put("schemaVersion", 0) }

    val migrated = SettingsMigrations.migrate(input, steps, target = 2)

    assertEquals(JsonPrimitive(true), migrated["a"], "the first step ran")
    assertEquals(JsonPrimitive(true), migrated["b"], "the second step ran after the first")
    assertEquals(2, (migrated["schemaVersion"] as JsonPrimitive).intOrNull)
  }

  @Test
  fun `a version with no step stops the walk and the stamp still lands`() {
    val steps: Map<Int, (JsonObject) -> JsonObject> =
      mapOf(
        0 to { json -> JsonObject(json + ("a" to JsonPrimitive(true))) },
        2 to { json -> JsonObject(json + ("c" to JsonPrimitive(true))) },
      )
    val input = buildJsonObject { put("schemaVersion", 0) }

    val migrated = SettingsMigrations.migrate(input, steps, target = 3)

    assertEquals(JsonPrimitive(true), migrated["a"], "the step before the gap ran")
    assertEquals(null, migrated["c"], "the step after the gap did not run")
    assertEquals(3, (migrated["schemaVersion"] as JsonPrimitive).intOrNull)
  }

  @Test
  fun `a version at or past the target runs no step`() {
    var ran = false
    val steps: Map<Int, (JsonObject) -> JsonObject> =
      mapOf(
        1 to
          { json ->
            ran = true
            json
          }
      )
    val input = buildJsonObject { put("schemaVersion", 1) }

    SettingsMigrations.migrate(input, steps, target = 1)

    assertEquals(false, ran)
  }

  /**
   * The version only moves up. [SCHEMA_VERSION_FLOOR] is raised with every bump and never lowered,
   * so an edit that walks it backwards fails here rather than on a user's device. The floor starts
   * at 1 with the pre-release schema, which nobody outside development has stored.
   */
  @Test
  fun `the schema version never moves down`() {
    assertEquals(
      true,
      SettingsMigrations.CURRENT_VERSION >= SCHEMA_VERSION_FLOOR,
      "CURRENT_VERSION ${SettingsMigrations.CURRENT_VERSION} is below the floor",
    )
  }

  private fun JsonPrimitive.booleanOrNull(): Boolean? = content.toBooleanStrictOrNull()

  private companion object {
    const val SCHEMA_VERSION_FLOOR: Int = 1
  }
}
