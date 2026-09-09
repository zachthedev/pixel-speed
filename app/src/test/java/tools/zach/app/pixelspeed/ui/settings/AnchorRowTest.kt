package tools.zach.app.pixelspeed.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AnchorRowTest {

  @ParameterizedTest(name = "{0} items at a limit of {1} make rows of {2}")
  @CsvSource(
    "5, 4, 3|2",
    "5, 5, 5",
    "5, 2, 2|2|1",
    "5, 3, 3|2",
    "4, 4, 4",
    "0, 4, ''",
  )
  fun `rows come out as even as the limit allows`(size: Int, limit: Int, expected: String) {
    val rows = (1..size).toList().chunkedEvenly(limit).map { it.size }
    val want = expected.split("|").filter { it.isNotEmpty() }.map { it.toInt() }
    assertEquals(want, rows)
  }
}
