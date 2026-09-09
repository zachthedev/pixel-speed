package tools.zach.app.pixelspeed.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.format.FormatOptions

class SettingsImportTest {
  private val customized =
    Settings(
      enabled = true,
      startOnBoot = false,
      paused = true,
      pausedUntilEpochMillis = 99L,
      profiles = listOf(Profile(id = "custom", name = "Custom")),
      activeProfileId = "custom",
      automationIntents = true,
    )

  @Test
  fun `serialize then parse of a fully customized settings object yields an equal object`() {
    val exported = SettingsImport.serialize(customized)

    val result = SettingsImport.parse(exported)

    assertTrue(result.isSuccess, "expected parse to succeed but got ${result.exceptionOrNull()}")
    assertEquals(customized, result.getOrNull())
  }

  @Test
  fun `an unknown top-level key is rejected with the key named`() {
    val exported = SettingsImport.serialize(Settings())
    val withUnknownKey = exported.trimEnd().removeSuffix("}") + ",\"futureField\":42}"

    val result = SettingsImport.parse(withUnknownKey)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("futureField"), "message did not name the unknown key: $message")
  }

  @Test
  fun `a schema version newer than current is rejected with both versions named`() {
    val exported = SettingsImport.serialize(Settings())
    val newerVersion =
      exported.replaceFirst(
        "\"schemaVersion\": ${SettingsMigrations.CURRENT_VERSION}",
        "\"schemaVersion\": 99",
      )

    val result = SettingsImport.parse(newerVersion)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("99"), "message did not name the file version: $message")
    assertTrue(
      message.contains(SettingsMigrations.CURRENT_VERSION.toString()),
      "message did not name the app's schema version: $message",
    )
  }

  @Test
  fun `a file with no schemaVersion field is refused naming the field`() {
    val exported = SettingsImport.serialize(Settings())
    val withoutVersion =
      exported.lineSequence().filterNot { it.contains("\"schemaVersion\"") }.joinToString("\n")

    val result = SettingsImport.parse(withoutVersion)

    assertTrue(result.isFailure, "a file with no version is not an export of this app")
    assertTrue(result.exceptionOrNull()?.message?.contains("schemaVersion") == true)
  }

  @Test
  fun `a profile name with a control or formatting character is refused naming the field`() {
    val profile = Profile(id = "default", name = "Work‮emoh")
    val exported = SettingsImport.serialize(Settings(profiles = listOf(profile)))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure, "a bidi override in a name is not a name")
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("name"), "message did not name the field: $message")
  }

  @Test
  fun `empty profiles are rejected`() {
    val invalid = Settings(profiles = emptyList())
    val exported = SettingsImport.serialize(invalid)

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("profiles"), "message did not name the field: $message")
  }

  @Test
  fun `a hostile activeProfileId reaches the message bounded and without control characters`() {
    val hostile = "\u202E" + "x".repeat(10_000)
    val exported = SettingsImport.serialize(Settings(activeProfileId = hostile))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.length < 200, "message is ${message.length} characters")
    assertTrue('\u202E' !in message, "message carries the bidi override")
  }

  @Test
  fun `a profile id with a control character is refused naming the field`() {
    val profile = Profile(id = "a\nb", name = "Fine")
    val exported =
      SettingsImport.serialize(Settings(profiles = listOf(profile), activeProfileId = "a\nb"))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("field id"), "message did not name the field: $message")
  }

  @Test
  fun `an activeProfileId that matches nothing is rejected`() {
    val invalid = Settings(activeProfileId = "missing")
    val exported = SettingsImport.serialize(invalid)

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("activeProfileId"), "message did not name the field: $message")
  }

  @Test
  fun `a hostile inactive profile is rejected naming its id and field`() {
    val safe = Profile(id = "safe")
    val evil =
      Profile(
        id = "evil",
        content = ContentSettings(format = FormatOptions(significantFigures = 0)),
        behavior = BehaviorSettings(updateIntervalMs = 0),
      )
    val hostile = Settings(profiles = listOf(safe, evil), activeProfileId = "safe")
    val exported = SettingsImport.serialize(hostile)

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure, "expected the inactive profile's bad field to be caught")
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("evil"), "message did not name the profile: $message")
    assertTrue(
      message.contains("significantFigures"),
      "message did not name the field: $message",
    )
  }

  @Test
  fun `a text over the character cap is rejected naming the cap`() {
    val oversized = "x".repeat(SettingsImport.MAX_FILE_BYTES + 1)

    val result = SettingsImport.parse(oversized)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(
      message.contains(SettingsImport.MAX_FILE_BYTES.toString()),
      "message did not name the cap: $message",
    )
  }

  @Test
  fun `13 profiles in the raw document is rejected before decoding naming the count`() {
    val profiles = (1..13).map { Profile(id = "p$it") }
    val exported = SettingsImport.serialize(Settings(profiles = profiles, activeProfileId = "p1"))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("13"), "message did not name the count: $message")
  }

  @Test
  fun `a file nested deeper than the parser's stack is refused, not a crash`() {
    val depth = 10_000
    val nested = "{\"profiles\":" + "[".repeat(depth) + "]".repeat(depth) + "}"

    val result = SettingsImport.parse(nested)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("nested"), "message did not say nested: $message")
  }

  @Test
  fun `a missing profiles field is rejected as missing, not as the wrong type`() {
    val root = Json.encodeToJsonElement(Settings.serializer(), Settings()) as JsonObject
    val withoutProfiles = JsonObject(root.toMutableMap().apply { remove("profiles") })

    val result = SettingsImport.parse(withoutProfiles.toString())

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("no profiles field"), "message did not say missing: $message")
  }

  @Test
  fun `a profiles value that is not an array is rejected`() {
    val root = Json.encodeToJsonElement(Settings.serializer(), Settings()) as JsonObject
    val withScalarProfiles =
      JsonObject(root.toMutableMap().apply { put("profiles", JsonPrimitive(1)) })

    val result = SettingsImport.parse(withScalarProfiles.toString())

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("profiles"), "message did not name the field: $message")
  }

  @Test
  fun `a malformed document reports the serialization failure rather than throwing`() {
    val malformed = "{not valid json"

    val result = SettingsImport.parse(malformed)

    assertTrue(result.isFailure)
  }

  @Test
  fun `more than 12 profiles is rejected`() {
    val profiles = (1..13).map { Profile(id = "p$it") }
    val exported = SettingsImport.serialize(Settings(profiles = profiles, activeProfileId = "p1"))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("profiles"), "message did not name the field: $message")
  }

  @Test
  fun `12 profiles is accepted`() {
    val profiles = (1..12).map { Profile(id = "p$it") }
    val exported = SettingsImport.serialize(Settings(profiles = profiles, activeProfileId = "p1"))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isSuccess, "expected parse to succeed but got ${result.exceptionOrNull()}")
  }

  @Test
  fun `duplicate profile ids are rejected naming the id`() {
    val profiles = listOf(Profile(id = "dup", name = "One"), Profile(id = "dup", name = "Two"))
    val exported = SettingsImport.serialize(Settings(profiles = profiles, activeProfileId = "dup"))

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure)
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("dup"), "message did not name the duplicate id: $message")
  }

  companion object {
    @JvmStatic
    fun outOfRangeRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "windowOpacity below range",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(windowOpacity = 0.1f)))
          ),
          "windowOpacity",
        ),
        Arguments.of(
          "windowOpacity above range",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(windowOpacity = 1.05f)))
          ),
          "windowOpacity",
        ),
        Arguments.of(
          "no pause durations",
          Settings(
            profiles = listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf())))
          ),
          "pauseDurationsMinutes.size",
        ),
        Arguments.of(
          "duplicate pause durations",
          Settings(
            profiles = listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(5, 5))))
          ),
          "pauseDurationsMinutes",
        ),
        Arguments.of(
          "name longer than 64 characters",
          Settings(profiles = listOf(Profile(name = "n".repeat(65)))),
          "name length",
        ),
        Arguments.of(
          "empty id",
          Settings(profiles = listOf(Profile(id = "")), activeProfileId = ""),
          "id length",
        ),
        Arguments.of(
          "textSizeSp below range",
          Settings(profiles = listOf(Profile(position = PositionSettings(textSizeSp = 8)))),
          "textSizeSp",
        ),
        Arguments.of(
          "textSizeSp above range",
          Settings(profiles = listOf(Profile(position = PositionSettings(textSizeSp = 25)))),
          "textSizeSp",
        ),
        Arguments.of(
          "updateIntervalMs below range",
          Settings(profiles = listOf(Profile(behavior = BehaviorSettings(updateIntervalMs = 499)))),
          "updateIntervalMs",
        ),
        Arguments.of(
          "updateIntervalMs above range",
          Settings(
            profiles = listOf(Profile(behavior = BehaviorSettings(updateIntervalMs = 5001)))
          ),
          "updateIntervalMs",
        ),
        Arguments.of(
          "significantFigures below range",
          Settings(
            profiles =
              listOf(
                Profile(content = ContentSettings(format = FormatOptions(significantFigures = 0)))
              )
          ),
          "significantFigures",
        ),
        Arguments.of(
          "significantFigures above range",
          Settings(
            profiles =
              listOf(
                Profile(content = ContentSettings(format = FormatOptions(significantFigures = 7)))
              )
          ),
          "significantFigures",
        ),
        Arguments.of(
          "decimals below range",
          Settings(
            profiles =
              listOf(Profile(content = ContentSettings(format = FormatOptions(decimals = -1))))
          ),
          "decimals",
        ),
        Arguments.of(
          "decimals above range",
          Settings(
            profiles =
              listOf(Profile(content = ContentSettings(format = FormatOptions(decimals = 7))))
          ),
          "decimals",
        ),
        Arguments.of(
          "chipPaddingDp below range",
          Settings(profiles = listOf(Profile(position = PositionSettings(chipPaddingDp = -1)))),
          "chipPaddingDp",
        ),
        Arguments.of(
          "chipPaddingDp above range",
          Settings(profiles = listOf(Profile(position = PositionSettings(chipPaddingDp = 17)))),
          "chipPaddingDp",
        ),
        Arguments.of(
          "minWidthDp below range",
          Settings(profiles = listOf(Profile(position = PositionSettings(minWidthDp = -1)))),
          "minWidthDp",
        ),
        Arguments.of(
          "minWidthDp above range",
          Settings(profiles = listOf(Profile(position = PositionSettings(minWidthDp = 241)))),
          "minWidthDp",
        ),
        Arguments.of(
          "offsetXdp below range",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetXdp = -201)))),
          "offsetXdp",
        ),
        Arguments.of(
          "offsetXdp above range",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetXdp = 201)))),
          "offsetXdp",
        ),
        Arguments.of(
          "offsetYdp below range",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetYdp = -201)))),
          "offsetYdp",
        ),
        Arguments.of(
          "offsetYdp above range",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetYdp = 201)))),
          "offsetYdp",
        ),
        Arguments.of(
          "customXdp below range",
          Settings(profiles = listOf(Profile(position = PositionSettings(customXdp = -4097)))),
          "customXdp",
        ),
        Arguments.of(
          "customYdp above range",
          Settings(profiles = listOf(Profile(position = PositionSettings(customYdp = 4097)))),
          "customYdp",
        ),
        Arguments.of(
          "cornerRadiusDp below range",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(cornerRadiusDp = -1)))
          ),
          "cornerRadiusDp",
        ),
        Arguments.of(
          "cornerRadiusDp above range",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(cornerRadiusDp = 1000)))
          ),
          "cornerRadiusDp",
        ),
        Arguments.of(
          "thresholdBytesPerSecond below range",
          Settings(
            profiles =
              listOf(Profile(appearance = AppearanceSettings(thresholdBytesPerSecond = 124_999L)))
          ),
          "thresholdBytesPerSecond",
        ),
        Arguments.of(
          "thresholdBytesPerSecond above range",
          Settings(
            profiles =
              listOf(
                Profile(appearance = AppearanceSettings(thresholdBytesPerSecond = 125_000_001L))
              )
          ),
          "thresholdBytesPerSecond",
        ),
        Arguments.of(
          "idleDelayMs below range",
          Settings(profiles = listOf(Profile(behavior = BehaviorSettings(idleDelayMs = 999)))),
          "idleDelayMs",
        ),
        Arguments.of(
          "idleDelayMs above range",
          Settings(profiles = listOf(Profile(behavior = BehaviorSettings(idleDelayMs = 60001)))),
          "idleDelayMs",
        ),
        Arguments.of(
          "idleThresholdBytesPerSecond below range",
          Settings(
            profiles =
              listOf(Profile(behavior = BehaviorSettings(idleThresholdBytesPerSecond = -1L)))
          ),
          "idleThresholdBytesPerSecond",
        ),
        Arguments.of(
          "idleThresholdBytesPerSecond above range",
          Settings(
            profiles =
              listOf(Profile(behavior = BehaviorSettings(idleThresholdBytesPerSecond = 1_000_001L)))
          ),
          "idleThresholdBytesPerSecond",
        ),
        Arguments.of(
          "pauseDurationsMinutes value below range",
          Settings(
            profiles = listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(0))))
          ),
          "pauseDurationsMinutes",
        ),
        Arguments.of(
          "pauseDurationsMinutes value above range",
          Settings(
            profiles = listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(1441))))
          ),
          "pauseDurationsMinutes",
        ),
        Arguments.of(
          "pauseDurationsMinutes with more than 5 entries",
          Settings(
            profiles =
              listOf(
                Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(1, 2, 3, 4, 5, 6)))
              )
          ),
          "pauseDurationsMinutes",
        ),
      )

    @JvmStatic
    fun inRangeRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "windowOpacity at low bound",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(windowOpacity = 0.3f)))
          ),
        ),
        Arguments.of(
          "windowOpacity at high bound",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(windowOpacity = 1.0f)))
          ),
        ),
        Arguments.of(
          "textSizeSp at low bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(textSizeSp = 9)))),
        ),
        Arguments.of(
          "textSizeSp at high bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(textSizeSp = 24)))),
        ),
        Arguments.of(
          "updateIntervalMs at low bound",
          Settings(profiles = listOf(Profile(behavior = BehaviorSettings(updateIntervalMs = 500)))),
        ),
        Arguments.of(
          "updateIntervalMs at high bound",
          Settings(
            profiles = listOf(Profile(behavior = BehaviorSettings(updateIntervalMs = 5000)))
          ),
        ),
        Arguments.of(
          "significantFigures at low bound",
          Settings(
            profiles =
              listOf(
                Profile(content = ContentSettings(format = FormatOptions(significantFigures = 1)))
              )
          ),
        ),
        Arguments.of(
          "significantFigures at high bound",
          Settings(
            profiles =
              listOf(
                Profile(content = ContentSettings(format = FormatOptions(significantFigures = 6)))
              )
          ),
        ),
        Arguments.of(
          "decimals at low bound",
          Settings(
            profiles =
              listOf(Profile(content = ContentSettings(format = FormatOptions(decimals = 0))))
          ),
        ),
        Arguments.of(
          "decimals at high bound",
          Settings(
            profiles =
              listOf(Profile(content = ContentSettings(format = FormatOptions(decimals = 6))))
          ),
        ),
        Arguments.of(
          "chipPaddingDp at low bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(chipPaddingDp = 0)))),
        ),
        Arguments.of(
          "chipPaddingDp at high bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(chipPaddingDp = 16)))),
        ),
        Arguments.of(
          "minWidthDp at low bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(minWidthDp = 0)))),
        ),
        Arguments.of(
          "minWidthDp at high bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(minWidthDp = 240)))),
        ),
        Arguments.of(
          "offsetXdp at low bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetXdp = -200)))),
        ),
        Arguments.of(
          "offsetXdp at high bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetXdp = 200)))),
        ),
        Arguments.of(
          "offsetYdp at low bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetYdp = -200)))),
        ),
        Arguments.of(
          "offsetYdp at high bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(offsetYdp = 200)))),
        ),
        Arguments.of(
          "customXdp at low bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(customXdp = -4096)))),
        ),
        Arguments.of(
          "customYdp at high bound",
          Settings(profiles = listOf(Profile(position = PositionSettings(customYdp = 4096)))),
        ),
        Arguments.of(
          "cornerRadiusDp at low bound",
          Settings(profiles = listOf(Profile(appearance = AppearanceSettings(cornerRadiusDp = 0)))),
        ),
        Arguments.of(
          "cornerRadiusDp at high bound",
          Settings(
            profiles = listOf(Profile(appearance = AppearanceSettings(cornerRadiusDp = 999)))
          ),
        ),
        Arguments.of(
          "thresholdBytesPerSecond at low bound",
          Settings(
            profiles =
              listOf(Profile(appearance = AppearanceSettings(thresholdBytesPerSecond = 125_000L)))
          ),
        ),
        Arguments.of(
          "thresholdBytesPerSecond at high bound",
          Settings(
            profiles =
              listOf(
                Profile(appearance = AppearanceSettings(thresholdBytesPerSecond = 125_000_000L))
              )
          ),
        ),
        Arguments.of(
          "idleDelayMs at low bound",
          Settings(profiles = listOf(Profile(behavior = BehaviorSettings(idleDelayMs = 1000)))),
        ),
        Arguments.of(
          "idleDelayMs at high bound",
          Settings(profiles = listOf(Profile(behavior = BehaviorSettings(idleDelayMs = 60000)))),
        ),
        Arguments.of(
          "idleThresholdBytesPerSecond at low bound",
          Settings(
            profiles =
              listOf(Profile(behavior = BehaviorSettings(idleThresholdBytesPerSecond = 0L)))
          ),
        ),
        Arguments.of(
          "idleThresholdBytesPerSecond at high bound",
          Settings(
            profiles =
              listOf(Profile(behavior = BehaviorSettings(idleThresholdBytesPerSecond = 1_000_000L)))
          ),
        ),
        Arguments.of(
          "pauseDurationsMinutes value at low bound",
          Settings(
            profiles = listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(1))))
          ),
        ),
        Arguments.of(
          "pauseDurationsMinutes value at high bound",
          Settings(
            profiles = listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(1440))))
          ),
        ),
        Arguments.of(
          "pauseDurationsMinutes with exactly 5 entries",
          Settings(
            profiles =
              listOf(Profile(pause = PauseSettings(pauseDurationsMinutes = listOf(1, 2, 3, 4, 5))))
          ),
        ),
      )
  }

  @ParameterizedTest(name = "{0} is rejected naming the field")
  @MethodSource("outOfRangeRows")
  fun `validation rejects a field outside its range`(
    rowName: String,
    settings: Settings,
    expectedFieldInMessage: String,
  ) {
    val exported = SettingsImport.serialize(settings)

    val result = SettingsImport.parse(exported)

    assertTrue(result.isFailure, "$rowName: expected parse to fail")
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(
      message.contains(expectedFieldInMessage),
      "$rowName: message did not name $expectedFieldInMessage: $message",
    )
  }

  @ParameterizedTest(name = "{0} is accepted")
  @MethodSource("inRangeRows")
  fun `validation accepts a field at its boundary`(rowName: String, settings: Settings) {
    val exported = SettingsImport.serialize(settings)

    val result = SettingsImport.parse(exported)

    assertTrue(
      result.isSuccess,
      "$rowName: expected parse to succeed but got ${result.exceptionOrNull()}",
    )
  }
}
