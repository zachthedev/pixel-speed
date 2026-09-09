package tools.zach.app.pixelspeed.state

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.settings.Settings

class PauseTransitionsTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("isValidDurationRows")
  fun `is valid duration accepts one minute to one day`(
    name: String,
    minutes: Int,
    expected: Boolean,
  ) {
    assertEquals(expected, PauseTransitions.isValidDuration(minutes))
  }

  companion object {
    @JvmStatic
    fun isValidDurationRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of("a negative duration is refused", -5, false),
        Arguments.of("zero is refused", 0, false),
        Arguments.of("one minute is the floor", 1, true),
        Arguments.of("a duration inside the day is accepted", 720, true),
        Arguments.of("one day is the ceiling", 1440, true),
        Arguments.of("a minute past a day is refused", 1441, false),
      )

    @JvmStatic
    fun isPausedRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of("flag off is not paused", false, null, 1000L, false),
        Arguments.of("flag on with no deadline is paused", true, null, 1000L, true),
        Arguments.of("flag on with a deadline ahead is paused", true, 2000L, 1000L, true),
        Arguments.of(
          "flag on with a deadline equal to now is not paused",
          true,
          1000L,
          1000L,
          false,
        ),
        Arguments.of("flag on with a deadline behind now is not paused", true, 500L, 1000L, false),
      )

    @JvmStatic
    fun pauseDurationRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of("null duration gives a null deadline", null),
        Arguments.of("zero duration gives a null deadline", 0),
        Arguments.of("negative duration gives a null deadline", -1),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("isPausedRows")
  fun `isPaused matches the flag and deadline`(
    name: String,
    paused: Boolean,
    until: Long?,
    now: Long,
    expected: Boolean,
  ) {
    val settings = Settings(paused = paused, pausedUntilEpochMillis = until)
    assertEquals(expected, PauseTransitions.isPaused(settings, now))
  }

  @Test
  fun `pause with 15 minutes sets the deadline 900000 millis ahead`() {
    val settings = Settings()
    val result = PauseTransitions.pause(settings, nowEpochMillis = 1000L, durationMinutes = 15)
    assertEquals(true, result.paused)
    assertEquals(901000L, result.pausedUntilEpochMillis)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pauseDurationRows")
  fun `pause with a non-positive or absent duration is until resumed`(
    name: String,
    durationMinutes: Int?,
  ) {
    val result =
      PauseTransitions.pause(Settings(), nowEpochMillis = 1000L, durationMinutes = durationMinutes)
    assertEquals(true, result.paused)
    assertEquals(null, result.pausedUntilEpochMillis)
  }

  @Test
  fun `resume clears both the flag and the deadline`() {
    val settings = Settings(paused = true, pausedUntilEpochMillis = 2000L)
    val result = PauseTransitions.resume(settings)
    assertEquals(false, result.paused)
    assertEquals(null, result.pausedUntilEpochMillis)
  }

  @Test
  fun `toggle on a live pause resumes`() {
    val settings = Settings(paused = true, pausedUntilEpochMillis = 2000L)
    val result = PauseTransitions.toggle(settings, nowEpochMillis = 1000L, durationMinutes = 15)
    assertEquals(false, result.paused)
    assertEquals(null, result.pausedUntilEpochMillis)
  }

  @Test
  fun `toggle on an expired pause pauses again with the new deadline`() {
    val settings = Settings(paused = true, pausedUntilEpochMillis = 500L)
    val result = PauseTransitions.toggle(settings, nowEpochMillis = 1000L, durationMinutes = 15)
    assertEquals(true, result.paused)
    assertEquals(901000L, result.pausedUntilEpochMillis)
  }
}
