package tools.zach.app.pixelspeed.overlay

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class BarSpanReaderTest {

  companion object {
    // A Pixel 9 Pro bar: clock at 28-71 dp, one notification icon before a hole centered at 213 dp
    // (the hole spans 195-232), the signal, wifi and battery icons after it. Density 1 keeps every
    // px equal to its dp, so a case can state edges once.
    private val clock = NodeBox(28, 10, 71, 30)
    private val oneNotification = NodeBox(79, 12, 103, 28)
    private val signal = NodeBox(306, 12, 330, 28)
    private val wifi = NodeBox(338, 12, 362, 28)
    private val battery = NodeBox(370, 12, 400, 28)
    private const val BAR_WIDTH_PX = 427
    private const val BAR_HEIGHT_PX = 68
    private const val HOLE_CENTER_PX = 213

    @JvmStatic
    fun rows(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "one icon each side of the hole",
          listOf(clock, oneNotification, signal, wifi, battery),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          false,
          MeasuredBar(103, 306),
        ),
        Arguments.of(
          "four notification icons push the start free edge toward the hole",
          listOf(
            clock,
            NodeBox(79, 12, 95, 28),
            NodeBox(99, 12, 115, 28),
            NodeBox(119, 12, 135, 28),
            NodeBox(139, 12, 155, 28),
            signal,
            wifi,
            battery,
          ),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          false,
          MeasuredBar(155, 306),
        ),
        Arguments.of(
          "RTL mirrors each edge across the bar's width and swaps which zone feeds which output",
          listOf(clock, oneNotification, signal, wifi, battery),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          true,
          MeasuredBar(BAR_WIDTH_PX - 306, BAR_WIDTH_PX - 103),
        ),
        Arguments.of(
          "a box straddling the center falls into neither zone, both fall back to the bar's edges",
          listOf(NodeBox(200, 10, 230, 30)),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          false,
          MeasuredBar(0, BAR_WIDTH_PX),
        ),
        Arguments.of(
          "a straddling box is ignored while the boxes on either side of it still count",
          listOf(clock, NodeBox(200, 10, 230, 30), signal),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          false,
          MeasuredBar(71, 306),
        ),
        Arguments.of(
          "a box outside the bar's height is ignored even though it would otherwise win its zone",
          listOf(clock, NodeBox(0, 70, 120, 90), signal),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          false,
          MeasuredBar(71, 306),
        ),
        Arguments.of(
          "no boxes at all is not a measurement",
          emptyList<NodeBox>(),
          BAR_WIDTH_PX,
          BAR_HEIGHT_PX,
          HOLE_CENTER_PX,
          1f,
          false,
          null,
        ),
        Arguments.of(
          "a fractional pixel-to-dp conversion rounds to the nearest dp",
          listOf(NodeBox(70, 10, 181, 30)),
          1068,
          170,
          533,
          2.5f,
          false,
          MeasuredBar(72, 427),
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `read follows the before-after-center zone rule`(
    name: String,
    boxes: List<NodeBox>,
    barWidthPx: Int,
    barHeightPx: Int,
    centerPx: Int,
    density: Float,
    rtl: Boolean,
    expected: MeasuredBar?,
  ) {
    assertEquals(
      expected,
      BarSpanReader.read(boxes, barWidthPx, barHeightPx, centerPx, density, rtl),
      name,
    )
  }
}
