package tools.zach.app.pixelspeed.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Steps a decoded settings object from whatever version it was written at up to [CURRENT_VERSION].
 */
object SettingsMigrations {
  /** The schema version this build writes; every stored file migrates up to it on read. */
  const val CURRENT_VERSION: Int = 1

  /**
   * One entry per version, keyed by the version being migrated away from. A missing entry for the
   * object's current version stops the walk there; the final `schemaVersion` stamp always runs.
   */
  private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

  /** Applies every migration step the object still needs, then stamps [CURRENT_VERSION]. */
  fun migrate(json: JsonObject): JsonObject = migrate(json, migrations, CURRENT_VERSION)

  /**
   * The walk over any [steps] toward any [target], which is what a test of the walk itself needs.
   */
  internal fun migrate(
    json: JsonObject,
    steps: Map<Int, (JsonObject) -> JsonObject>,
    target: Int,
  ): JsonObject {
    var current = json
    var version = (json["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 0
    while (version < target) {
      val step = steps[version] ?: break
      current = step(current)
      version += 1
    }
    val stamped = current.toMutableMap()
    stamped["schemaVersion"] = JsonPrimitive(target)
    return JsonObject(stamped)
  }
}
