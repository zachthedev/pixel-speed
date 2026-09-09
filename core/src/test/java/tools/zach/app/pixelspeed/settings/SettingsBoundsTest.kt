package tools.zach.app.pixelspeed.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SettingsBoundsTest {

  @ParameterizedTest(name = "API {0} floors the interval at {1} ms")
  @CsvSource("31, 500", "34, 500", "35, 1000", "37, 1000")
  fun `the interval floor follows the platform counter cache`(sdkInt: Int, floorMs: Int) {
    assertEquals(floorMs, SettingsBounds.updateIntervalFloorMs(sdkInt))
  }

  @ParameterizedTest(name = "API {1} ticks a {0} ms interval every {2} ms")
  @CsvSource(
    "1000, 34, 1000",
    "500, 34, 500",
    "200, 34, 500",
    "1000, 35, 1100",
    "500, 35, 1100",
    "1050, 37, 1100",
    "1100, 37, 1100",
    "1500, 37, 1500",
    "5000, 37, 5000",
    "2147483647, 37, 2147483647",
    "-2147483648, 37, 1100",
    "2147483647, 34, 2147483647",
  )
  fun `the sampler tick floors the interval and clears the counter cache`(
    intervalMs: Int,
    sdkInt: Int,
    tickMs: Int,
  ) {
    assertEquals(tickMs, SettingsBounds.samplerTickMs(intervalMs, sdkInt))
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
    "the opacity range in percent matches the float range, 30, 100",
    "the threshold bytes match the megabit slider, 125000, 125000000",
    "the idle threshold bytes match the kilobyte slider, 0, 1000000",
    "the idle delay millis match the seconds slider, 1000, 60000",
  )
  fun `derived ranges follow the slider ranges`(name: String, first: Long, last: Long) {
    val (actualFirst, actualLast) =
      when {
        name.startsWith("the opacity") ->
          (SettingsBounds.WINDOW_OPACITY.start * 100).toLong() to
            (SettingsBounds.WINDOW_OPACITY.endInclusive * 100).toLong()
        name.startsWith("the threshold") ->
          SettingsBounds.THRESHOLD_BYTES_PER_SECOND.first to
            SettingsBounds.THRESHOLD_BYTES_PER_SECOND.last
        name.startsWith("the idle threshold") ->
          SettingsBounds.IDLE_THRESHOLD_BYTES_PER_SECOND.first to
            SettingsBounds.IDLE_THRESHOLD_BYTES_PER_SECOND.last
        else ->
          SettingsBounds.IDLE_DELAY_MS.first.toLong() to SettingsBounds.IDLE_DELAY_MS.last.toLong()
      }
    assertEquals(first, actualFirst, "$name: first")
    assertEquals(last, actualLast, "$name: last")
  }
}
