package tools.zach.app.pixelspeed.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import tools.zach.app.pixelspeed.log.LogLine

/** The text kept for an uncaught exception: when, which build, which thread, the trace, the log. */
object CrashRecord {
  private val stampFormat: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
  private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

  fun render(
    at: Instant,
    zone: ZoneId,
    versionName: String,
    threadName: String,
    throwable: Throwable,
    log: List<LogLine>,
  ): String = buildString {
    appendLine("Crash at ${stampFormat.format(at.atZone(zone))} in version $versionName")
    appendLine("Thread: $threadName")
    appendLine()
    appendLine(throwable.stackTraceToString().trimEnd())
    appendLine()
    appendLine("Log before the crash (${log.size} lines, newest last):")
    for (line in log) {
      val stamp: String = timeFormat.format(Instant.ofEpochMilli(line.timeMillis).atZone(zone))
      appendLine("$stamp ${line.level} ${line.message}")
    }
  }
}
