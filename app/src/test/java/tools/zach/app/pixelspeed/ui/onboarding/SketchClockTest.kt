package tools.zach.app.pixelspeed.ui.onboarding

import java.util.Locale
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.service.CldrPatterns

class SketchClockTest {

  companion object {
    private const val NARROW_NO_BREAK_SPACE = ' '
    private const val NO_BREAK_SPACE = ' '
    private const val RIGHT_TO_LEFT_MARK = '‏'
    private const val ARABIC_LETTER_MARK = '؜'

    @JvmStatic
    fun patternRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of("a trailing marker goes with its space", "h:mm a", "h:mm"),
        Arguments.of("a leading marker goes with its space", "a h:mm", "h:mm"),
        Arguments.of("a marker glued to the hour goes", "ah:mm", "h:mm"),
        Arguments.of("a day period letter goes like a marker", "h:mm B", "h:mm"),
        Arguments.of(
          "a trailing marker behind a narrow no-break space goes with it",
          "h:mm${NARROW_NO_BREAK_SPACE}a",
          "h:mm",
        ),
        Arguments.of(
          "a leading marker before a narrow no-break space goes with it",
          "a${NARROW_NO_BREAK_SPACE}h:mm",
          "h:mm",
        ),
        Arguments.of("a no-break space counts as spacing", "h:mm${NO_BREAK_SPACE}a", "h:mm"),
        Arguments.of(
          "a bidi mark beside the marker goes with it",
          "h:mm $RIGHT_TO_LEFT_MARK a",
          "h:mm",
        ),
        Arguments.of(
          "an Arabic letter mark beside the marker goes with it",
          "h:mm ${ARABIC_LETTER_MARK}a",
          "h:mm",
        ),
        Arguments.of("a 24-hour pattern is unchanged", "HH:mm", "HH:mm"),
        Arguments.of("quoted text stays, letters and all", "HH 'h' mm", "HH 'h' mm"),
        Arguments.of("a quoted marker letter stays", "h:mm 'a'", "h:mm 'a'"),
        Arguments.of("an empty pattern stays empty", "", ""),
        Arguments.of("a marker alone leaves nothing", "a", ""),
        Arguments.of(
          "the JDK's own US pattern loses its marker",
          CldrPatterns.bestPattern(Locale.US, "hm"),
          "h:mm",
        ),
      )

    @JvmStatic
    fun clockRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of("US 12-hour", Locale.US, false, "10:10"),
        Arguments.of("US 24-hour", Locale.US, true, "10:10"),
        Arguments.of("German 24-hour", Locale.GERMANY, true, "10:10"),
        Arguments.of(
          "Turkish 12-hour, marker first",
          Locale.forLanguageTag("tr-TR"),
          false,
          "10:10",
        ),
        Arguments.of("Chinese 12-hour, marker first", Locale.SIMPLIFIED_CHINESE, false, "10:10"),
        Arguments.of(
          "Hebrew 12-hour, right to left",
          Locale.forLanguageTag("he-IL"),
          false,
          "10:10",
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("patternRows")
  fun `withoutDayPeriod strips the marker and its spacing`(
    name: String,
    pattern: String,
    expected: String,
  ) {
    assertEquals(expected, withoutDayPeriod(pattern), name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("clockRows")
  fun `the drawn clock is the hour and minute alone`(
    name: String,
    locale: Locale,
    hour24: Boolean,
    expected: String,
  ) {
    assertEquals(expected, sketchClockText(locale, hour24, CldrPatterns.bestPattern), name)
  }

  @Test
  fun `a pattern the formatter refuses gives way to the plain hour and minute`() {
    assertEquals("10:10", sketchClockText(Locale.US, false) { _, _ -> "h:mm 'a" })
  }

  @Test
  fun `a pattern that is only a marker gives way to the plain hour and minute`() {
    assertEquals("10:10", sketchClockText(Locale.US, true) { _, _ -> "a" })
  }
}
