package tools.zach.app.pixelspeed.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import tools.zach.app.pixelspeed.log.LogLine

/**
 * Everything a diagnostics report holds, collected by the app and rendered by [DiagnosticsReport].
 * Every value is a fact about the app, the phone, or the display; none names the person or the
 * device instance. [settingsJson] is the user's own export, which they read before sharing.
 */
data class DiagnosticsInputs(
  val generatedAt: Instant,
  val zone: ZoneId,
  val app: List<Pair<String, String>>,
  val device: List<Pair<String, String>>,
  val display: List<Pair<String, String>>,
  val permissions: List<Pair<String, String>>,
  val monitor: List<Pair<String, String>>,
  val settingsJson: String,
  val log: List<LogLine>,
  /** The last crash's record as the crash handler wrote it, or null when none is kept. */
  val lastCrash: String?,
)

/** Renders [DiagnosticsInputs] as the plain text the user shares. */
object DiagnosticsReport {
  private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
  private val stampFormat: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  fun render(inputs: DiagnosticsInputs): String = buildString {
    appendLine("Pixel Speed diagnostics")
    appendLine("Generated ${stampFormat.format(inputs.generatedAt.atZone(inputs.zone))}")
    appendLine()
    section("App", inputs.app)
    section("Device", inputs.device)
    section("Display", inputs.display)
    section("Permissions", inputs.permissions)
    section("Monitor", inputs.monitor)
    heading("Settings")
    appendLine(inputs.settingsJson.trimEnd())
    appendLine()
    heading("Log (${inputs.log.size} lines, newest last)")
    for (line in inputs.log) appendLine(formatLine(line, inputs.zone))
    appendLine()
    heading("Last crash")
    appendLine(inputs.lastCrash?.trimEnd() ?: "None recorded")
  }

  private fun StringBuilder.section(title: String, facts: List<Pair<String, String>>) {
    heading(title)
    for ((key, value) in facts) appendLine("$key: $value")
    appendLine()
  }

  private fun StringBuilder.heading(title: String) {
    appendLine("## $title")
  }

  private fun formatLine(line: LogLine, zone: ZoneId): String {
    val stamp: String = timeFormat.format(Instant.ofEpochMilli(line.timeMillis).atZone(zone))
    val head = "$stamp ${line.level} ${line.message}"
    val trace: String? = line.trace
    return if (trace == null) head else head + "\n" + trace.trimEnd().prependIndent("    ")
  }
}
