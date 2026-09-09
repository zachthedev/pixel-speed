package tools.zach.app.pixelspeed.service

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class DeadlineFormatterTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `a deadline follows the device clock in the locale's own pattern`(
    name: String,
    until: String,
    now: String,
    zone: String,
    locale: String,
    hour24: Boolean,
    present: List<String>,
    absent: List<String>,
  ) {
    val text =
      DeadlineFormatter.format(
        until = Instant.parse(until),
        nowEpochMillis = Instant.parse(now).toEpochMilli(),
        locale = Locale.forLanguageTag(locale),
        zone = ZoneId.of(zone),
        hour24 = hour24,
        bestPattern = CldrPatterns.bestPattern,
      )

    present.forEach { assertTrue(text.contains(it), "expected '$it' in '$text'") }
    absent.forEach { assertFalse(text.contains(it), "did not expect '$it' in '$text'") }
  }

  @ParameterizedTest(name = "{0} becomes {1}")
  @CsvSource(
    "'Bh:mm', 'ah:mm'",
    "'h:mm b', 'h:mm a'",
    "'HH:mm', 'HH:mm'",
    "'h:mm a', 'h:mm a'",
    "'''B'' HH:mm', '''B'' HH:mm'",
    "'h ''o''''clock'' B', 'h ''o''''clock'' a'",
    "'a ''ga'' h:mm', 'a ''ga'' h:mm'",
  )
  fun `day-period letters java time lacks become a outside quoted text`(
    pattern: String,
    expected: String,
  ) {
    assertEquals(expected, DeadlineFormatter.javaTimeLetters(pattern))
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource("'#h:mm a'", "'h:mm aa'", "'h:mm {a}'")
  fun `a pattern java time refuses renders as nothing`(pattern: String) {
    assertNull(
      DeadlineFormatter.render(pattern, Locale.US, ZoneId.of("UTC"), Instant.parse(AFTERNOON))
    )
  }

  @ParameterizedTest(name = "{0} hour24 {1} same day {2}")
  @MethodSource("shippedLocales")
  fun `every shipped locale renders its minute through its own pattern`(
    locale: String,
    hour24: Boolean,
    sameDay: Boolean,
  ) {
    val text =
      DeadlineFormatter.format(
        until = Instant.parse(if (sameDay) AFTERNOON else NEXT_AFTERNOON),
        nowEpochMillis = Instant.parse(MORNING).toEpochMilli(),
        locale = Locale.forLanguageTag(locale),
        zone = ZoneId.of("UTC"),
        hour24 = hour24,
        bestPattern = CldrPatterns.bestPattern,
      )

    assertTrue(text.contains("30"), "expected the minute in '$text'")
    assertEquals(hour24, text.contains("14"), "expected the hour to follow hour24 in '$text'")
  }

  companion object {
    private const val AFTERNOON = "2026-09-05T14:30:00Z"
    private const val MORNING = "2026-09-05T09:00:00Z"
    private const val NEXT_AFTERNOON = "2026-09-06T14:30:00Z"

    @JvmStatic
    fun rows(): Stream<Arguments> =
      Stream.of(
        row("US 12-hour on the same day", AFTERNOON, MORNING, "UTC", "en-US", false)
          .present("2:30", "PM")
          .absent("14", "Sep"),
        row("US 24-hour drops the day period", AFTERNOON, MORNING, "UTC", "en-US", true)
          .present("14:30")
          .absent("PM", "Sep"),
        row("US another day carries month and day", NEXT_AFTERNOON, MORNING, "UTC", "en-US", true)
          .present("Sep 6", "14:30")
          .absent("PM", "2026"),
        row("German 12-hour gains a day period", AFTERNOON, MORNING, "UTC", "de-DE", false)
          .present("2:30", "PM")
          .absent("14"),
        row(
            "German another day keeps its dotted day",
            NEXT_AFTERNOON,
            MORNING,
            "UTC",
            "de-DE",
            true,
          )
          .present("6. Sept.", "14:30")
          .absent("5."),
        row(
            "Canadian French keeps its quoted h separator",
            AFTERNOON,
            MORNING,
            "UTC",
            "fr-CA",
            true,
          )
          .present("14 h 30")
          .absent("HH", "p.m."),
        row(
            "Canadian French 12-hour keeps h and adds p.m.",
            AFTERNOON,
            MORNING,
            "UTC",
            "fr-CA",
            false,
          )
          .present("2 h 30", "p.m.")
          .absent("14"),
        row(
            "Traditional Chinese 12-hour keeps its leading period",
            AFTERNOON,
            MORNING,
            "UTC",
            "zh-TW",
            false,
          )
          .present("下午2:30")
          .absent("14"),
        row("Traditional Chinese 24-hour has no period", AFTERNOON, MORNING, "UTC", "zh-TW", true)
          .present("14:30")
          .absent("下午"),
        row(
            "Vietnamese keeps the time before the day",
            NEXT_AFTERNOON,
            MORNING,
            "UTC",
            "vi-VN",
            false,
          )
          .present("2:30", "CH 6 thg 9")
          .absent("14"),
        row("Sorbian keeps its quoted hour word", AFTERNOON, MORNING, "UTC", "hsb-DE", true)
          .present("14:30 hodź.")
          .absent("HH"),
        row(
            "Ewe keeps its quoted words around the period",
            AFTERNOON,
            MORNING,
            "UTC",
            "ee-GH",
            false,
          )
          .present("ɣetrɔ ga 2:30")
          .absent("14"),
        row(
            "a deadline past midnight shows its day",
            "2026-09-06T00:10:00Z",
            "2026-09-05T23:59:00Z",
            "UTC",
            "en-US",
            false,
          )
          .present("Sep 6", "12:10")
          .absent("Sep 5"),
        row(
            "the zone decides the day: 14:30 UTC is the sixth in Auckland",
            AFTERNOON,
            MORNING,
            "Pacific/Auckland",
            "en-US",
            false,
          )
          .present("Sep 6", "2:30")
          .absent("Sep 5"),
        row(
            "an expired deadline still formats as its time",
            "2026-09-05T08:00:00Z",
            MORNING,
            "UTC",
            "en-US",
            false,
          )
          .present("8:00", "AM")
          .absent("Sep"),
      )

    /** The locales the app ships strings for, each with both clocks and both day cases. */
    @JvmStatic
    fun shippedLocales(): Stream<Arguments> =
      listOf("en-US", "de-DE", "es-ES", "fr-FR", "hi-IN", "pt-BR", "zh-CN")
        .flatMap { locale ->
          listOf(true, false).flatMap { hour24 ->
            listOf(true, false).map { sameDay -> Arguments.of(locale, hour24, sameDay) }
          }
        }
        .stream()

    private class Row(
      val name: String,
      val until: String,
      val now: String,
      val zone: String,
      val locale: String,
      val hour24: Boolean,
      var present: List<String> = emptyList(),
    ) {
      fun present(vararg text: String): Row = apply { present = text.toList() }

      fun absent(vararg text: String): Arguments =
        Arguments.of(name, until, now, zone, locale, hour24, present, text.toList())
    }

    private fun row(
      name: String,
      until: String,
      now: String,
      zone: String,
      locale: String,
      hour24: Boolean,
    ): Row = Row(name, until, now, zone, locale, hour24)
  }
}
