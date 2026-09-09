package tools.zach.app.pixelspeed.ui.overlay

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.Profile

class OverlayValuesTest {

  private val formatter = SpeedFormatter(Locale.US)
  private val profile = Profile()

  private fun sample(supported: Boolean): SpeedSample =
    SpeedSample(
      rxBytesPerSecond = 1_540_000,
      txBytesPerSecond = 150_000,
      atElapsedNanos = 0,
      transport = Transport.WIFI,
      supported = supported,
    )

  @Test
  fun `the wait for a first sample is loading and holds the dashes underneath`() {
    val values =
      (null as SpeedSample?).toValues(formatter, profile, OverlayStyle.CHIP, loading = true)

    assertTrue(values.loading)
    assertEquals(SpeedFormatter.UNSUPPORTED_DASH, values.downloadNumber)
    assertEquals("", values.downloadSpokenUnit)
  }

  @Test
  fun `a stopped source with no sample is not loading and shows the offline dashes`() {
    val values = (null as SpeedSample?).toValues(formatter, profile, OverlayStyle.CHIP)

    assertFalse(values.loading)
    assertEquals(SpeedFormatter.UNSUPPORTED_DASH, values.downloadNumber)
    assertEquals(SpeedFormatter.UNSUPPORTED_DASH, values.uploadNumber)
  }

  @Test
  fun `an unsupported counter is not loading and stays on the dashes`() {
    val values = sample(supported = false).toValues(formatter, profile, OverlayStyle.CHIP)

    assertFalse(values.loading)
    assertEquals(SpeedFormatter.UNSUPPORTED_DASH, values.uploadNumber)
  }

  @Test
  fun `the Auto label spells the unit out and drops it for the stack and the halves`() {
    val values = sample(supported = true).toValues(formatter, profile, OverlayStyle.CHIP)

    assertEquals("Mbps", values.downloadUnit)
    assertEquals("", values.downloadStackUnit)
    assertEquals(values.downloadNumber, values.downloadStackNumber, "one tier, one number")
    assertEquals("Mbps", values.downloadSpokenUnit, "a screen reader still hears the unit")
  }

  @Test
  fun `past a gigabit the bare stack number stays in megabits while the labeled looks climb`() {
    val gigabit = sample(supported = true).copy(rxBytesPerSecond = 150_000_000)
    val values = gigabit.toValues(formatter, profile, OverlayStyle.CHIP)

    assertEquals("Gbps", values.downloadUnit)
    assertEquals("1.20", values.downloadNumber.trim())
    assertEquals("1200", values.downloadStackNumber.trim())
    assertEquals("", values.downloadStackUnit)
  }

  @Test
  fun `an explicit short label shortens every look`() {
    val short =
      profile.copy(
        content =
          profile.content.copy(format = profile.content.format.copy(unitLabel = UnitLabel.SHORT))
      )
    val values = sample(supported = true).toValues(formatter, short, OverlayStyle.CHIP)

    assertEquals("Mb", values.downloadUnit)
    assertEquals("Mb", values.downloadStackUnit)
    assertEquals("Mbps", values.downloadSpokenUnit, "a screen reader always hears the full unit")
  }

  @Test
  fun `a sample is never loading, whatever the wait flag says`() {
    val values =
      sample(supported = true).toValues(formatter, profile, OverlayStyle.CHIP, loading = true)

    assertFalse(values.loading)
    assertTrue(values.downloadNumber.first().isDigit(), values.downloadNumber)
  }
}
