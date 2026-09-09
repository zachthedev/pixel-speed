package tools.zach.app.pixelspeed.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The latch behind every regular-mode escape hatch: once shown, a row stays shown. */
class RevealTest {

  @ParameterizedTest(name = "shown {0}, in effect {1} gives {2}")
  @CsvSource(
    "false, false, false",
    "false, true, true",
    "true, true, true",
    "true, false, true",
  )
  fun `a row shows once its value is in effect and then stays`(
    shown: Boolean,
    inEffect: Boolean,
    expected: Boolean,
  ) {
    assertEquals(expected, reveal(shown, inEffect))
  }

  @ParameterizedTest(name = "advanced {0}, in effect {1} starts {2}")
  @CsvSource("false, false, false", "true, false, true", "false, true, true", "true, true, true")
  fun `the first composition shows a row in advanced mode or with its value in effect`(
    advanced: Boolean,
    inEffect: Boolean,
    expected: Boolean,
  ) {
    assertEquals(expected, reveal(advanced, inEffect))
  }
}
