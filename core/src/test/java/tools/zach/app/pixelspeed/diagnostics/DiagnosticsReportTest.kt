package tools.zach.app.pixelspeed.diagnostics

import java.time.Instant
import java.time.ZoneOffset
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.log.LogLine

class DiagnosticsReportTest {

  private fun inputs(
    log: List<LogLine> = emptyList(),
    lastCrash: String? = null,
    settingsJson: String = "{\"schemaVersion\":1}",
  ): DiagnosticsInputs =
    DiagnosticsInputs(
      generatedAt = Instant.parse("2026-09-09T20:15:30Z"),
      zone = ZoneOffset.UTC,
      app = listOf("Version" to "1.0.0"),
      device = listOf("Model" to "Google Pixel 9 Pro (caiman)"),
      display = listOf("Density" to "3.0 (480 dpi)"),
      permissions = listOf("Draw over other apps" to "true"),
      monitor = listOf("State" to "Running(suppressions=[], overlayOn=true)"),
      settingsJson = settingsJson,
      log = log,
      lastCrash = lastCrash,
    )

  @Test
  fun `the sections appear in order under their headings with their facts`() {
    val text: String = DiagnosticsReport.render(inputs())

    val headings: List<String> = text.lines().filter { it.startsWith("## ") }
    assertEquals(
      listOf(
        "## App",
        "## Device",
        "## Display",
        "## Permissions",
        "## Monitor",
        "## Settings",
        "## Log (0 lines, newest last)",
        "## Last crash",
      ),
      headings,
    )
    assertTrue(text.startsWith("Pixel Speed diagnostics\nGenerated 2026-09-09T20:15:30Z\n"), text)
    assertTrue("Version: 1.0.0" in text)
    assertTrue("Model: Google Pixel 9 Pro (caiman)" in text)
    assertTrue("State: Running(suppressions=[], overlayOn=true)" in text)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("logLines")
  fun `a log line renders as time, level, and message, with its trace indented`(
    label: String,
    line: LogLine,
    expected: String,
  ) {
    val text: String = DiagnosticsReport.render(inputs(log = listOf(line)))

    assertTrue(expected in text, "$label\n$text")
    assertTrue("## Log (1 lines, newest last)" in text, label)
  }

  @Test
  fun `no crash renders as none recorded and a crash is included verbatim`() {
    val none: String = DiagnosticsReport.render(inputs())
    val crash = "Crash at 2026-09-09T19:00:00Z in version 1.0.0\nThread: main\n\nboom"
    val some: String = DiagnosticsReport.render(inputs(lastCrash = crash + "\n\n"))

    assertTrue(none.trimEnd().endsWith("## Last crash\nNone recorded"), none)
    assertTrue(some.trimEnd().endsWith("## Last crash\n$crash"), some)
  }

  @Test
  fun `the settings export is included untouched`() {
    val json = "{\n  \"schemaVersion\": 1,\n  \"profiles\": [{\"name\": \"Work\"}]\n}"

    val text: String = DiagnosticsReport.render(inputs(settingsJson = json))

    assertTrue("## Settings\n$json\n" in text, text)
  }

  companion object {
    @JvmStatic
    fun logLines(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "a plain line",
          LogLine(
            timeMillis = 1_788_990_000_123L,
            level = 'I',
            message = "Overlay shown",
            trace = null,
          ),
          "21:40:00.123 I Overlay shown",
        ),
        Arguments.of(
          "a line with a trace",
          LogLine(
            timeMillis = 1_788_990_000_123L,
            level = 'E',
            message = "Cannot persist",
            trace = "java.io.IOException: disk\n\tat a.b(C.kt:1)\n",
          ),
          "21:40:00.123 E Cannot persist\n    java.io.IOException: disk\n    \tat a.b(C.kt:1)\n",
        ),
      )
  }
}
