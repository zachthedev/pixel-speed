package tools.zach.app.pixelspeed.state

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport

class MonitorStateHolderTest {

  private val holder = MonitorStateHolder()

  private val sample =
    SpeedSample(
      rxBytesPerSecond = 1_000,
      txBytesPerSecond = 100,
      atElapsedNanos = 0,
      transport = Transport.WIFI,
      supported = true,
    )

  @Test
  fun `a sampler starting with nothing to show waits for its first sample`() = runTest {
    holder.expectSample()

    assertTrue(holder.loading.value)
    assertNull(holder.latestSample.value)
  }

  @Test
  fun `the first sample ends the wait`() = runTest {
    holder.expectSample()

    holder.setSample(sample)

    assertFalse(holder.loading.value)
    assertEquals(sample, holder.latestSample.value)
  }

  @Test
  fun `a sampler restarting over a held sample keeps it and never waits`() = runTest {
    holder.setSample(sample)

    holder.expectSample()

    assertFalse(holder.loading.value)
    assertEquals(sample, holder.latestSample.value)
  }

  @Test
  fun `clearing the readout ends the wait without a sample`() = runTest {
    holder.expectSample()

    holder.setSample(null)

    assertFalse(holder.loading.value)
    assertNull(holder.latestSample.value)
  }

  @Test
  fun `a fresh holder waits for the first sampler's word`() = runTest {
    assertTrue(holder.loading.value)
    assertNull(holder.latestSample.value)
  }

  @Test
  fun `a source with nothing to read ends the wait for good`() = runTest {
    holder.setSample(null)

    assertFalse(holder.loading.value)
  }

  @Test
  fun `clearing forgets the sample and waits again`() = runTest {
    holder.setSample(sample)
    holder.setSample(null)

    holder.clear()

    assertTrue(holder.loading.value)
    assertNull(holder.latestSample.value)
  }
}
