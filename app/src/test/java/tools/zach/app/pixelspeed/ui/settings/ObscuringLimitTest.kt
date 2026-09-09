package tools.zach.app.pixelspeed.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The limit is a global a privileged caller can write anything into, so every shape of it is a row.
 */
class ObscuringLimitTest {

  @ParameterizedTest(name = "a reported limit of {0} becomes {1}")
  @CsvSource(
    "0.8, 0.8",
    "0.53, 0.53",
    "1.0, 1.0",
    "0.0, 0.0",
    "-0.5, 0.0",
    "1.5, 1.0",
    "NaN, 0.8",
  )
  fun `the limit is held to zero through one and NaN takes the platform default`(
    reported: Float,
    expected: Float,
  ) {
    assertEquals(expected, clampObscuringLimit(reported), 0.0001f)
  }

  @ParameterizedTest(name = "a limit of {0} names {1} percent")
  @CsvSource("0.8, 80", "0.53, 52", "0.59, 58", "0.796, 79", "1.0, 100", "0.0, 0")
  fun `the percent a warning names is never above the real limit`(limit: Float, percent: Int) {
    val named = obscuringLimitPercent(limit)

    assertEquals(percent, named)
    assertTrue(named / 100f <= limit, "$named percent overstates a limit of $limit")
  }
}
