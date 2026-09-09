package tools.zach.app.pixelspeed.ui.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CenteredInSlotTest {

  @ParameterizedTest(name = "content {1} in slot {0} with {2} px of room starts at {3}")
  @CsvSource(
    "354, 300, 900, 27",
    "354, 354, 900, 0",
    "354, 300, 354, 27",
    "354, 300, 320, 20",
    "354, 300, 300, 0",
    "354, 300, 200, 0",
    "440, 410, 411, 1",
  )
  fun `the live content centers in its slot until the display's end stops it`(
    slotWidth: Int,
    contentWidth: Int,
    roomPx: Int,
    expected: Int,
  ) {
    assertEquals(expected, centeredInSlot(slotWidth, contentWidth, roomPx))
  }
}
