package tools.zach.app.pixelspeed.service

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import tools.zach.app.pixelspeed.log.AppLog

/**
 * Renders a pause deadline so a day-long pause never reads like a time that already passed. The
 * clock follows the device's 24-hour setting ([hour24]) rather than the locale's habit, the way the
 * status bar clock does, and the locale supplies its own pattern for that clock through
 * [bestPattern]: the hour letter, the day-period marker and where it sits, the separators, and any
 * quoted words. On device that is ICU's pattern generator; a JVM test passes its own.
 */
object DeadlineFormatter {
  fun format(
    until: Instant,
    nowEpochMillis: Long,
    locale: Locale,
    zone: ZoneId,
    hour24: Boolean,
    bestPattern: (Locale, String) -> String = DateFormat::getBestDateTimePattern,
  ): String {
    val sameDay =
      until.atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    val skeleton = (if (sameDay) "" else DAY) + (if (hour24) HOUR_24 else HOUR_12)
    val pattern = javaTimeLetters(bestPattern(locale, skeleton))
    render(pattern, locale, zone, until)?.let {
      return it
    }
    // Locale data updates apart from the app, so a pattern java.time refuses is a warning, not a
    // crash; the locale's own clock stands in for the device's.
    AppLog.w("java.time refuses the $locale pattern '$pattern' for $skeleton")
    val style: DateTimeFormatter =
      if (sameDay) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
      else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    return style.withLocale(locale).withZone(zone).format(until)
  }

  /** [until] in [pattern], or null when java.time rejects a letter or a reserved character. */
  internal fun render(pattern: String, locale: Locale, zone: ZoneId, until: Instant): String? =
    try {
      DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(until)
    } catch (e: IllegalArgumentException) {
      null
    }

  /**
   * [pattern] with each day-period letter that java.time does not read on every supported Android
   * version (`B` before API 34, `b` on none) swapped for `a`, which fills the same slot. Text
   * between apostrophes is literal and stays as it is.
   */
  internal fun javaTimeLetters(pattern: String): String {
    var quoted = false
    return buildString(pattern.length) {
      for (c in pattern) {
        if (c == '\'') quoted = !quoted
        append(if (!quoted && (c == 'B' || c == 'b')) 'a' else c)
      }
    }
  }

  /** ICU skeleton fields: abbreviated month with day, and the minute with a 24- or 12-hour hour. */
  private const val DAY = "MMMd"
  private const val HOUR_24 = "Hm"
  private const val HOUR_12 = "hm"
}
