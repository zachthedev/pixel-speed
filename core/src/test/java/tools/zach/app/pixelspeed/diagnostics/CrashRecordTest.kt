package tools.zach.app.pixelspeed.diagnostics

import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.zach.app.pixelspeed.log.LogLine

class CrashRecordTest {

  @Test
  fun `the record names the time, version, and thread, then the trace, then the log`() {
    val text: String =
      CrashRecord.render(
        at = Instant.parse("2026-09-09T20:15:30Z"),
        zone = ZoneOffset.UTC,
        versionName = "1.0.0",
        threadName = "main",
        throwable = IllegalStateException("boom"),
        log =
          listOf(
            LogLine(1_788_990_000_123L, 'I', "Service created", null),
            LogLine(1_788_990_001_000L, 'W', "Foreground start refused", null),
          ),
      )

    val lines: List<String> = text.lines()
    assertEquals("Crash at 2026-09-09T20:15:30Z in version 1.0.0", lines[0])
    assertEquals("Thread: main", lines[1])
    assertEquals("", lines[2])
    assertTrue(lines[3].startsWith("java.lang.IllegalStateException: boom"), lines[3])
    val logStart: Int = lines.indexOf("Log before the crash (2 lines, newest last):")
    assertTrue(logStart > 3, text)
    assertEquals("21:40:00.123 I Service created", lines[logStart + 1])
    assertEquals("21:40:01.000 W Foreground start refused", lines[logStart + 2])
  }

  @Test
  fun `an empty log still closes the record`() {
    val text: String =
      CrashRecord.render(
        at = Instant.EPOCH,
        zone = ZoneOffset.UTC,
        versionName = "development build",
        threadName = "worker",
        throwable = RuntimeException("x"),
        log = emptyList(),
      )

    assertTrue(text.trimEnd().endsWith("Log before the crash (0 lines, newest last):"), text)
  }
}
