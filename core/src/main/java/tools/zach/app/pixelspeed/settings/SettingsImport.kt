package tools.zach.app.pixelspeed.settings

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private val strictJson = Json {
  ignoreUnknownKeys = false
  encodeDefaults = true
}

private val lenientJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
  prettyPrint = true
}

/** Reads and writes the user-facing export file, which is stricter than the on-device store. */
object SettingsImport {
  /**
   * A reader stops at this many bytes, so a huge file never lands in memory, and the parser refuses
   * a text past it, so a caller that skipped the reader still never fully decodes a hostile file. A
   * UTF-8 text never has more characters than bytes, so one number serves both.
   */
  const val MAX_FILE_BYTES: Int = 1_000_000

  /** A profiles array longer than this is refused before decoding. */
  private val MAX_PROFILES: Int = SettingsBounds.PROFILE_COUNT.last

  /**
   * Decodes an exported [Settings] file. Fails when the text exceeds [MAX_FILE_BYTES], the file is
   * newer than [SettingsMigrations.CURRENT_VERSION], carries a top-level key the schema does not
   * know, has more than [MAX_PROFILES] profiles, or any profile violates a field bound.
   */
  fun parse(text: String): Result<Settings> {
    if (text.length > MAX_FILE_BYTES) {
      return Result.failure(
        IllegalArgumentException(
          "Settings file is ${text.length} characters, over the $MAX_FILE_BYTES character cap."
        )
      )
    }
    return try {
      val root =
        strictJson.parseToJsonElement(text) as? JsonObject
          ?: throw IllegalArgumentException("Settings file is not a JSON object.")
      val profilesElement =
        root["profiles"] ?: throw IllegalArgumentException("Settings file has no profiles field.")
      val profilesArray =
        profilesElement as? JsonArray
          ?: throw IllegalArgumentException("Settings file field profiles is not an array.")
      require(profilesArray.size <= MAX_PROFILES) {
        "Settings file has ${profilesArray.size} profiles, over the $MAX_PROFILES profile cap."
      }
      val fileVersion =
        (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
          ?: throw IllegalArgumentException("Settings file has no schemaVersion field.")
      require(fileVersion <= SettingsMigrations.CURRENT_VERSION) {
        "Settings file is at schema version $fileVersion, newer than the app's schema version " +
          "${SettingsMigrations.CURRENT_VERSION}."
      }
      val migrated = SettingsMigrations.migrate(root)
      val descriptor = Settings.serializer().descriptor
      val knownKeys =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
      val unknownKeys = migrated.keys - knownKeys
      require(unknownKeys.isEmpty()) {
        "Settings file has unknown keys: ${unknownKeys.forMessage()}."
      }
      val settings = strictJson.decodeFromJsonElement(Settings.serializer(), migrated)
      validate(settings)
      Result.success(settings)
    } catch (e: SerializationException) {
      Result.failure(e)
    } catch (e: IllegalArgumentException) {
      Result.failure(e)
    } catch (e: StackOverflowError) {
      // The parser recurses per nesting level, so a file nested deeper than the stack is refused.
      Result.failure(IllegalArgumentException("Settings file is nested too deeply to read.", e))
    }
  }

  /** Encodes [settings] for export, tolerating unknown keys on the next import of an older app. */
  fun serialize(settings: Settings): String =
    lenientJson.encodeToString(Settings.serializer(), settings)

  private fun validate(settings: Settings) {
    require(settings.profiles.size in SettingsBounds.PROFILE_COUNT) {
      "Settings file has ${settings.profiles.size} profiles, outside the allowed range " +
        SettingsBounds.PROFILE_COUNT.label() +
        "."
    }
    val ids = settings.profiles.map { it.id }
    val duplicateIds = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    require(duplicateIds.isEmpty()) {
      "Settings file has duplicate profile ids: ${duplicateIds.forMessage()}."
    }
    require(ids.any { it == settings.activeProfileId }) {
      "activeProfileId \"${settings.activeProfileId.forMessage()}\" does not match any profile."
    }
    settings.profiles.forEach(::validateProfile)
  }

  private fun validateProfile(profile: Profile) {
    val id = profile.id.forMessage()
    fun outOfRange(field: String, value: Any, range: String): Nothing =
      throw IllegalArgumentException(
        "profile \"$id\" field $field is $value, outside the allowed range $range."
      )

    fun check(field: String, value: Int, range: IntRange) {
      if (value !in range) outOfRange(field, value, range.label())
    }
    fun check(field: String, value: Long, range: LongRange) {
      if (value !in range) outOfRange(field, value, "${range.first} to ${range.last}")
    }

    check("id length", profile.id.length, SettingsBounds.TEXT_LENGTH)
    check("name length", profile.name.length, SettingsBounds.TEXT_LENGTH)
    for ((field, text) in listOf("id" to profile.id, "name" to profile.name)) {
      if (text.any(::isControlOrFormat)) {
        throw IllegalArgumentException(
          "profile \"$id\" field $field contains a control or formatting character."
        )
      }
    }
    val format = profile.content.format
    check("significantFigures", format.significantFigures, SettingsBounds.SIGNIFICANT_FIGURES)
    check("decimals", format.decimals, SettingsBounds.DECIMALS)

    val position = profile.position
    check("textSizeSp", position.textSizeSp, SettingsBounds.TEXT_SIZE_SP)
    check("chipPaddingDp", position.chipPaddingDp, SettingsBounds.CHIP_PADDING_DP)
    check("minWidthDp", position.minWidthDp, SettingsBounds.MIN_WIDTH_DP)
    check("offsetXdp", position.offsetXdp, SettingsBounds.OFFSET_DP)
    check("offsetYdp", position.offsetYdp, SettingsBounds.OFFSET_DP)
    check("customXdp", position.customXdp, SettingsBounds.CUSTOM_POSITION_DP)
    check("customYdp", position.customYdp, SettingsBounds.CUSTOM_POSITION_DP)

    val appearance = profile.appearance
    if (appearance.windowOpacity !in SettingsBounds.WINDOW_OPACITY) {
      val opacity = SettingsBounds.WINDOW_OPACITY
      outOfRange(
        "windowOpacity",
        appearance.windowOpacity,
        "${opacity.start} to ${opacity.endInclusive}",
      )
    }
    check("cornerRadiusDp", appearance.cornerRadiusDp, SettingsBounds.CORNER_RADIUS_DP)
    check(
      "thresholdBytesPerSecond",
      appearance.thresholdBytesPerSecond,
      SettingsBounds.THRESHOLD_BYTES_PER_SECOND,
    )

    val behavior = profile.behavior
    check("updateIntervalMs", behavior.updateIntervalMs, SettingsBounds.UPDATE_INTERVAL_MS)
    check("idleDelayMs", behavior.idleDelayMs, SettingsBounds.IDLE_DELAY_MS)
    check(
      "idleThresholdBytesPerSecond",
      behavior.idleThresholdBytesPerSecond,
      SettingsBounds.IDLE_THRESHOLD_BYTES_PER_SECOND,
    )

    val pause = profile.pause
    check(
      "pauseDurationsMinutes.size",
      pause.pauseDurationsMinutes.size,
      SettingsBounds.PAUSE_DURATION_COUNT,
    )
    if (pause.pauseDurationsMinutes.distinct().size != pause.pauseDurationsMinutes.size) {
      outOfRange("pauseDurationsMinutes", pause.pauseDurationsMinutes, "distinct values")
    }
    pause.pauseDurationsMinutes.forEach { minutes ->
      check("pauseDurationsMinutes", minutes, SettingsBounds.PAUSE_MINUTES)
    }
  }

  private fun IntRange.label(): String = "$first to $last"

  /**
   * Names from the file as a message can carry them: a bounded number, each cut to a name's own
   * length bound with control and formatting characters removed, so a hostile file cannot fill or
   * reshape the line that reports it.
   */
  private fun Collection<String>.forMessage(): String {
    val named = sorted().take(MESSAGE_NAMES).joinToString(", ") { name -> name.forMessage() }
    val rest = size - MESSAGE_NAMES
    return if (rest > 0) "$named, and $rest more" else named
  }

  private fun String.forMessage(): String =
    filterNot(::isControlOrFormat).take(SettingsBounds.TEXT_LENGTH.last)

  private const val MESSAGE_NAMES: Int = 5
}

/**
 * A control character (a newline among them) or a Unicode format character (the bidirectional
 * overrides among them): neither belongs in a name that titles a card or a row.
 */
fun isControlOrFormat(char: Char): Boolean =
  char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt()
