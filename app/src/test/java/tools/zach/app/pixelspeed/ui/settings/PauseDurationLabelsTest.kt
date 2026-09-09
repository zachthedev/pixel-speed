package tools.zach.app.pixelspeed.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PauseDurationLabelsTest {

  @ParameterizedTest
  @CsvSource(
    "1, MINUTES, 1",
    "2, MINUTES, 2",
    "30, MINUTES, 30",
    "59, MINUTES, 59",
    "60, HOURS, 1",
    "90, MINUTES, 90",
    "120, HOURS, 2",
    "240, HOURS, 4",
    "480, HOURS, 8",
    "1439, MINUTES, 1439",
    "1440, DAYS, 1",
  )
  fun `resourceFor buckets minutes by the hour and day boundary`(
    minutes: Int,
    unit: String,
    quantity: Int,
  ) {
    val resource = PauseDurationLabels.resourceFor(minutes)
    assertEquals(PauseDurationLabels.PauseUnit.valueOf(unit), resource.unit)
    assertEquals(quantity, resource.quantity)
  }
}
