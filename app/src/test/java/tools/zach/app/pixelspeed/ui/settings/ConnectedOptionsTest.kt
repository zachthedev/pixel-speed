package tools.zach.app.pixelspeed.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConnectedOptionsTest {

  @ParameterizedTest(name = "font scale {0} with {1} dp for the options holds {2}")
  @CsvSource(
    // A kit row on a 427 dp phone gives its options 363 dp: four, then three, then two, never
    // fewer.
    "0.85, 363, 4",
    "1.0, 363, 4",
    "1.15, 363, 3",
    "1.3, 363, 3",
    "1.5, 363, 3",
    "2.0, 363, 2",
    "3.0, 363, 2",
    // Home's pause row on the same phone has 355 dp after the screen and card padding.
    "1.0, 355, 4",
    // Narrow rows at scale 1: the largest display size on a Pixel, a 360 dp phone, a 320 dp one.
    "1.0, 277, 3",
    "1.0, 296, 3",
    "1.0, 256, 3",
    "1.0, 347, 4",
    // A list pane on a folded-open window, at scale 1 and 1.5.
    "1.0, 340, 4",
    "1.5, 340, 2",
    // Wide rows never exceed four.
    "1.0, 776, 4",
    "2.0, 1216, 4",
    // Degenerate inputs clamp rather than throw or overflow.
    "1.0, 0, 2",
    "2.0, -40, 2",
    "0, 363, 4",
    "NaN, 363, 4",
    "1.0, -2147483648, 2",
  )
  fun `the connected row budget follows the options width and the font scale`(
    fontScale: Float,
    optionsWidthDp: Int,
    expected: Int,
  ) {
    assertEquals(expected, maxConnectedOptions(fontScale, optionsWidthDp))
  }
}
