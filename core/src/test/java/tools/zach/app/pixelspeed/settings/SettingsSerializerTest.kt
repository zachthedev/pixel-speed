package tools.zach.app.pixelspeed.settings

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SettingsSerializerTest {
  @Test
  fun `defaultValue is Settings with no fields set`() {
    assertEquals(Settings(), SettingsSerializer.defaultValue)
  }

  @Test
  fun `corrupt bytes raise CorruptionException`() {
    val input = ByteArrayInputStream("not json".toByteArray())

    assertThrows(CorruptionException::class.java) {
      runBlocking { SettingsSerializer.readFrom(input) }
    }
  }

  @Test
  fun `a file stamped newer than this build raises CorruptionException`() {
    val newer = SettingsMigrations.CURRENT_VERSION + 1
    val input = ByteArrayInputStream("{\"schemaVersion\": $newer}".toByteArray())

    val error =
      assertThrows(CorruptionException::class.java) {
        runBlocking { SettingsSerializer.readFrom(input) }
      }

    assertEquals(true, error.message?.contains(newer.toString()), "names the file's version")
  }

  @Test
  fun `a quoted integer stamp reads as its number`() {
    val input = ByteArrayInputStream("{\"schemaVersion\": \"1\"}".toByteArray())

    val decoded = runBlocking { SettingsSerializer.readFrom(input) }

    assertEquals(SettingsMigrations.CURRENT_VERSION, decoded.schemaVersion)
  }

  @ParameterizedTest(name = "a schemaVersion of {0} raises CorruptionException")
  @ValueSource(strings = ["9.0", "true", "2147483648", "[]", "{}"])
  fun `a schemaVersion that is not an integer raises CorruptionException`(stamp: String) {
    val input = ByteArrayInputStream("{\"schemaVersion\": $stamp}".toByteArray())

    assertThrows(CorruptionException::class.java) {
      runBlocking { SettingsSerializer.readFrom(input) }
    }
  }

  @ParameterizedTest(name = "a root of {0} raises CorruptionException")
  @ValueSource(strings = ["[]", "5", "null"])
  fun `a non-object root raises CorruptionException`(rootText: String) {
    val input = ByteArrayInputStream(rootText.toByteArray())

    assertThrows(CorruptionException::class.java) {
      runBlocking { SettingsSerializer.readFrom(input) }
    }
  }

  @Test
  fun `an empty profiles array raises CorruptionException`() {
    val input = ByteArrayInputStream("{\"profiles\":[]}".toByteArray())

    assertThrows(CorruptionException::class.java) {
      runBlocking { SettingsSerializer.readFrom(input) }
    }
  }

  @Test
  fun `a file missing every field decodes to the schema defaults`() = runBlocking {
    val input = ByteArrayInputStream("{}".toByteArray())

    val decoded = SettingsSerializer.readFrom(input)

    assertEquals(Settings(), decoded)
  }

  @Test
  fun `an unknown top-level key is tolerated`() = runBlocking {
    val output = ByteArrayOutputStream()
    SettingsSerializer.writeTo(Settings(), output)
    val withExtraKey =
      output.toString(Charsets.UTF_8).trimEnd().removeSuffix("}") + ",\"futureField\":42}"

    val decoded = SettingsSerializer.readFrom(ByteArrayInputStream(withExtraKey.toByteArray()))

    assertEquals(Settings(), decoded)
  }

  @Test
  fun `writeTo then readFrom round trips a customized settings object`() = runBlocking {
    val customized =
      Settings(
        enabled = true,
        startOnBoot = false,
        paused = true,
        pausedUntilEpochMillis = 42L,
        profiles = listOf(Profile(id = "custom", name = "Custom")),
        activeProfileId = "custom",
        automationIntents = true,
      )
    val output = ByteArrayOutputStream()

    SettingsSerializer.writeTo(customized, output)
    val decoded = SettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

    assertEquals(customized, decoded)
  }
}
