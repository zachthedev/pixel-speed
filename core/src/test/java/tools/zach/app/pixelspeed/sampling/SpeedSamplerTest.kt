package tools.zach.app.pixelspeed.sampling

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A [CounterSource] fed from a queue of readings, holding on the last one once exhausted. */
private class QueueCounterSource(private val readings: MutableList<CounterReading>) :
  CounterSource {
  var readCount: Int = 0
    private set

  override fun read(): CounterReading {
    readCount++
    return if (readings.size > 1) readings.removeAt(0) else readings[0]
  }
}

/** A [CounterSource] whose [read] runs a caller-supplied action before returning a value. */
private class ScriptedCounterSource(private val steps: MutableList<() -> CounterReading>) :
  CounterSource {
  var readCount: Int = 0
    private set

  override fun read(): CounterReading {
    readCount++
    val step = if (steps.size > 1) steps.removeAt(0) else steps[0]
    return step()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedSamplerTest {

  private fun TestScope.elapsedNanosClock(): () -> Long = { currentTime * 1_000_000L }

  /**
   * Advances virtual time by [millis] and runs the tasks that land exactly on the new time.
   * [advanceTimeBy] alone only runs tasks scheduled strictly before the target instant, so a tick
   * scheduled for exactly the requested time waits for the next call unless [runCurrent] follows.
   */
  private fun TestScope.advanceAndDrain(millis: Long) {
    advanceTimeBy(millis)
    runCurrent()
  }

  private fun TestScope.launchCollector(
    flow: Flow<SpeedSample>,
    onEach: (SpeedSample) -> Unit,
  ): Job = backgroundScope.launch { flow.collect { onEach(it) } }

  private fun reading(rx: Long, tx: Long, supported: Boolean = true): CounterReading =
    CounterReading(rxBytes = rx, txBytes = tx, atElapsedNanos = 0, supported = supported)

  @Test
  fun `first read emits nothing`() = runTest {
    val source = QueueCounterSource(mutableListOf(reading(0, 0)))
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(1000)
    job.cancel()

    assertTrue(samples.isEmpty(), "baseline read must not emit")
  }

  @Test
  fun `steady counters give bytes per second from the delta`() = runTest {
    val source =
      QueueCounterSource(
        mutableListOf(reading(0, 0), reading(1_000_000, 250_000), reading(2_000_000, 500_000))
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.CELLULAR }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(2, samples.size)
    assertEquals(SpeedSample(1_000_000, 250_000, 1_000_000_000L, Transport.CELLULAR), samples[0])
    assertEquals(SpeedSample(1_000_000, 250_000, 2_000_000_000L, Transport.CELLULAR), samples[1])
  }

  @Test
  fun `elapsed time shorter than the interval divides by real elapsed time`() = runTest {
    // A tick that overruns its schedule by 100 ms re-baselines late; the next tick still lands on
    // the fixed 2000 ms schedule, so the real gap between the two is 900 ms, shorter than the
    // 1000 ms interval.
    val source =
      ScriptedCounterSource(
        mutableListOf(
          { reading(5_000_000, 0) },
          {
            testScheduler.advanceTimeBy(100)
            reading(1_000_000, 0)
          },
          { reading(1_900_000, 0) },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(1, samples.size, "the reset tick itself emits nothing")
    assertEquals(
      1_000_000L,
      samples[0].rxBytesPerSecond,
      "900 ms real gap, not the nominal 1000 ms",
    )
  }

  @Test
  fun `elapsed time longer than the interval divides by real elapsed time`() = runTest {
    val source =
      ScriptedCounterSource(
        mutableListOf(
          { reading(0, 0) },
          {
            testScheduler.advanceTimeBy(300)
            reading(1_300_000, 0)
          },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(1300)
    job.cancel()

    assertEquals(1, samples.size)
    assertEquals(1_000_000L, samples[0].rxBytesPerSecond)
  }

  @Test
  fun `stale cached read re-emits the previous rate then zero past the hold window`() = runTest {
    val source =
      QueueCounterSource(
        mutableListOf(
          reading(0, 0),
          reading(1_000_000, 0),
          reading(1_000_000, 0),
          reading(1_000_000, 0),
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(3000)
    job.cancel()

    assertEquals(3, samples.size)
    assertEquals(1_000_000L, samples[0].rxBytesPerSecond)
    assertEquals(1_000_000L, samples[1].rxBytesPerSecond, "hold window re-emits the previous rate")
    assertEquals(0L, samples[2].rxBytesPerSecond, "past the hold window the rate drops to zero")
  }

  @Test
  fun `identical counters from the start hold then emit zero`() = runTest {
    val source = QueueCounterSource(mutableListOf(reading(0, 0)))
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(1, samples.size, "the tick inside the hold window emits nothing")
    assertEquals(0L, samples[0].rxBytesPerSecond, "the tick past the hold window emits zero")
    assertEquals(0L, samples[0].txBytesPerSecond)
  }

  @Test
  fun `counter reset re-baselines instead of measuring a negative delta`() = runTest {
    val source =
      QueueCounterSource(
        mutableListOf(reading(5_000_000, 0), reading(1_000_000, 0), reading(2_000_000, 0))
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(1, samples.size, "the reset tick itself emits nothing")
    assertEquals(1_000_000L, samples[0].rxBytesPerSecond, "measured against the reset value")
  }

  @Test
  fun `unsupported reading emits an unsupported zero sample then re-baselines`() = runTest {
    val source =
      QueueCounterSource(
        mutableListOf(reading(0, 0), reading(0, 0, supported = false), reading(500_000, 0))
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.NONE }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(2, samples.size)
    assertEquals(false, samples[0].supported)
    assertEquals(0L, samples[0].rxBytesPerSecond)
    assertEquals(true, samples[1].supported)
    assertEquals(500_000L, samples[1].rxBytesPerSecond, "measured against the unsupported reading")
  }

  @Test
  fun `ticks land on interval multiples of the start even when reads take real time`() = runTest {
    val readStartTimes = mutableListOf<Long>()
    val source =
      ScriptedCounterSource(
        mutableListOf(
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            testScheduler.advanceTimeBy(300)
            reading(1_000_000, 0)
          },
          {
            readStartTimes.add(currentTime)
            testScheduler.advanceTimeBy(300)
            reading(2_000_000, 0)
          },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(listOf(0L, 1000L, 2000L), readStartTimes, "reads start on interval multiples")
  }

  @Test
  fun `a large clock jump emits once and resyncs without replaying missed ticks`() = runTest {
    // The second read runs a full 59.3 s late (a Doze window, say), landing 60.3 s after the
    // baseline. The sampler must emit once for that gap, then resync to the next 1000 ms
    // boundary rather than replaying each of the roughly 59 intervals it missed.
    val readStartTimes = mutableListOf<Long>()
    val source =
      ScriptedCounterSource(
        mutableListOf(
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            testScheduler.advanceTimeBy(59_300)
            reading(60_300_000, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(60_300_000, 0)
          },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) { samples.add(it) }

    advanceAndDrain(60_300)
    assertEquals(1, samples.size, "the jump tick emits exactly once, not once per missed interval")
    assertEquals(1_000_000L, samples[0].rxBytesPerSecond, "rate divides by the real 60.3 s gap")

    advanceAndDrain(700)
    job.cancel()

    assertEquals(
      listOf(0L, 1000L, 61_000L),
      readStartTimes,
      "the tick after the jump resyncs to the next interval multiple, not a burst of missed ticks",
    )
    assertEquals(3, source.readCount, "no extra reads happen while catching up")
  }

  @Test
  fun `an interval of zero coerces to the 500 ms minimum cadence`() = runTest {
    val readStartTimes = mutableListOf<Long>()
    val source =
      ScriptedCounterSource(
        mutableListOf(
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val job = launchCollector(sampler.samples(0, Smoothing.NONE)) {}
    advanceAndDrain(1000)
    job.cancel()

    assertEquals(listOf(0L, 500L, 1000L), readStartTimes, "a zero interval never skips the delay")
  }

  @Test
  fun `a negative interval coerces to the 500 ms minimum cadence`() = runTest {
    val readStartTimes = mutableListOf<Long>()
    val source =
      ScriptedCounterSource(
        mutableListOf(
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val job = launchCollector(sampler.samples(-5000, Smoothing.NONE)) {}
    advanceAndDrain(1000)
    job.cancel()

    assertEquals(
      listOf(0L, 500L, 1000L),
      readStartTimes,
      "a negative interval never skips the delay",
    )
  }

  @Test
  fun `an interval of 2500 ms scales both the cadence and the hold window`() = runTest {
    val readStartTimes = mutableListOf<Long>()
    val source =
      ScriptedCounterSource(
        mutableListOf(
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
          {
            readStartTimes.add(currentTime)
            reading(0, 0)
          },
        )
      )
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(2500, Smoothing.NONE)) { samples.add(it) }
    advanceAndDrain(5000)
    job.cancel()

    assertEquals(listOf(0L, 2500L, 5000L), readStartTimes, "ticks land on 2500 ms multiples")
    assertEquals(1, samples.size, "the 5000 ms hold window is 2x the 2500 ms interval")
    assertEquals(0L, samples[0].rxBytesPerSecond)
  }

  @Test
  fun `light smoothing applies an exponential moving average`() = runTest {
    val source =
      QueueCounterSource(mutableListOf(reading(0, 0), reading(1_000_000, 0), reading(4_000_000, 0)))
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.LIGHT)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(1_000_000L, samples[0].rxBytesPerSecond)
    assertEquals(2_000_000L, samples[1].rxBytesPerSecond)
  }

  @Test
  fun `heavy smoothing weights the previous sample more`() = runTest {
    val source =
      QueueCounterSource(mutableListOf(reading(0, 0), reading(1_000_000, 0), reading(4_000_000, 0)))
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val samples = mutableListOf<SpeedSample>()
    val job = launchCollector(sampler.samples(1000, Smoothing.HEAVY)) { samples.add(it) }
    advanceAndDrain(2000)
    job.cancel()

    assertEquals(1_000_000L, samples[0].rxBytesPerSecond)
    assertEquals(1_400_000L, samples[1].rxBytesPerSecond)
  }

  @Test
  fun `cancelling the collector stops reads`() = runTest {
    val source =
      QueueCounterSource(mutableListOf(reading(0, 0), reading(1_000_000, 0), reading(2_000_000, 0)))
    val sampler = SpeedSampler(source, elapsedNanosClock()) { Transport.WIFI }

    val job = launchCollector(sampler.samples(1000, Smoothing.NONE)) {}
    advanceAndDrain(1000)
    val countAtCancel = source.readCount
    job.cancel()
    advanceAndDrain(3000)

    assertEquals(countAtCancel, source.readCount, "no reads happen after cancellation")
  }
}
