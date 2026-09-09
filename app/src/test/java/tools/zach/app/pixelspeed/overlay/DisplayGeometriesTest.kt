package tools.zach.app.pixelspeed.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DisplayGeometriesTest {

  @ParameterizedTest(name = "a platform corner padding of {0} dp becomes {1} dp")
  @CsvSource(
    "-1, 0",
    "0, 0",
    "32, 32",
    "48, 48",
    "49, 48",
    "2147483647, 48",
    "-2147483648, 0",
  )
  fun `the corner padding is held to the plausible range`(reported: Int, expected: Int) {
    assertEquals(expected, clampCornerPadding(reported))
  }

  @ParameterizedTest(name = "{0} px at density {1} spans {2} whole dp and rounds to {3}")
  @CsvSource(
    "1280, 3.0, 426, 427",
    "2856, 3.0, 952, 952",
    "1281, 3.0, 427, 427",
    "1080, 2.625, 411, 411",
    "1440, 3.5, 411, 411",
    "2208, 2.625, 841, 841",
    "0, 3.0, 0, 0",
  )
  fun `a display's bounds floor to the dp that fit while rects keep the shared rounding`(
    px: Int,
    density: Float,
    floored: Int,
    rounded: Int,
  ) {
    assertEquals(floored, pxToDpFloor(px, density))
    assertEquals(rounded, pxToDp(px, density))
    // A window clamped to the floored bounds never reaches past the last physical pixel.
    assertTrue(dpToPx(pxToDpFloor(px, density), density) <= px) {
      "the floored bounds convert back past the display's $px px"
    }
  }
}
