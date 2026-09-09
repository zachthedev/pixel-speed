package tools.zach.app.pixelspeed.sampling

import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tools.zach.app.pixelspeed.settings.SettingsBounds

/**
 * Runs a fixed-cadence sampling loop over [source] until the collector cancels. Each collection
 * starts from a fresh baseline and a fresh smoothing state, so restarting the flow after a
 * counter-set change never carries a rate over from the old set.
 */
class SpeedSampler(
  private val source: CounterSource,
  private val elapsedNanos: () -> Long,
  private val transport: () -> Transport,
) {

  private companion object {
    /** The profile's lowest legal interval. A shorter or non-positive request never delays. */
    val MIN_INTERVAL_MS: Long = SettingsBounds.UPDATE_INTERVAL_MS.first.toLong()

    /** Below this, the elapsed time since the baseline is measurement noise, not a real rate. */
    const val MIN_DT_NANOS: Long = 1_000_000L
  }

  /** One tick's counter values and the time they were read, in the [elapsedNanos] clock. */
  private data class Baseline(val rxBytes: Long, val txBytes: Long, val atElapsedNanos: Long)

  /** Exponential moving average, held per direction, that passes the first sample through as is. */
  private class ExponentialAverage(private val alpha: Double) {
    private var rx: Double? = null
    private var tx: Double? = null

    fun apply(rawRx: Long, rawTx: Long): Pair<Long, Long> {
      val nextRx: Double = rx?.let { alpha * rawRx + (1 - alpha) * it } ?: rawRx.toDouble()
      val nextTx: Double = tx?.let { alpha * rawTx + (1 - alpha) * it } ?: rawTx.toDouble()
      rx = nextRx
      tx = nextTx
      return nextRx.roundToLong() to nextTx.roundToLong()
    }
  }

  fun samples(intervalMs: Long, smoothing: Smoothing): Flow<SpeedSample> = flow {
    val intervalNanos: Long = intervalMs.coerceAtLeast(MIN_INTERVAL_MS) * 1_000_000L
    val holdWindow: Long = 2 * intervalNanos
    val average = ExponentialAverage(smoothing.alpha)

    val firstReading: CounterReading = source.read()
    val startAt: Long = elapsedNanos()
    var baseline = Baseline(firstReading.rxBytes, firstReading.txBytes, startAt)
    var lastTick: Long = startAt
    var previousSample: SpeedSample? = null

    while (true) {
      var scheduledTick: Long = lastTick + intervalNanos
      val beforeWait: Long = elapsedNanos()
      if (scheduledTick <= beforeWait) {
        // The collector was descheduled past one or more intervals (for example a Doze window).
        // Resync to the boundary at or before now instead of replaying every missed interval back
        // to back with no delay between them.
        lastTick = beforeWait - ((beforeWait - startAt) % intervalNanos)
        scheduledTick = lastTick + intervalNanos
      }
      val waitNanos: Long = scheduledTick - elapsedNanos()
      if (waitNanos > 0) {
        delay(waitNanos.nanoseconds)
      }
      lastTick = scheduledTick

      val reading: CounterReading = source.read()
      val now: Long = elapsedNanos()

      if (!reading.supported) {
        emit(SpeedSample(0, 0, now, transport(), supported = false))
        baseline = Baseline(reading.rxBytes, reading.txBytes, now)
        previousSample = null
        continue
      }

      val rxEqual: Boolean = reading.rxBytes == baseline.rxBytes
      val txEqual: Boolean = reading.txBytes == baseline.txBytes
      if (rxEqual && txEqual) {
        if (now - baseline.atElapsedNanos < holdWindow) {
          previousSample?.let { emit(it.copy(atElapsedNanos = now, transport = transport())) }
        } else {
          val sample = SpeedSample(0, 0, now, transport(), supported = true)
          emit(sample)
          previousSample = sample
        }
        continue
      }

      if (reading.rxBytes < baseline.rxBytes || reading.txBytes < baseline.txBytes) {
        baseline = Baseline(reading.rxBytes, reading.txBytes, now)
        previousSample = null
        continue
      }

      val elapsedSinceBaseline: Long = now - baseline.atElapsedNanos
      if (elapsedSinceBaseline < MIN_DT_NANOS) {
        // A near-zero divisor turns a normal delta into an absurd rate; treat it as a stale read.
        previousSample?.let { emit(it.copy(atElapsedNanos = now, transport = transport())) }
        continue
      }

      val dtSeconds: Double = elapsedSinceBaseline / 1_000_000_000.0
      val rawRx: Long = ((reading.rxBytes - baseline.rxBytes) / dtSeconds).roundToLong()
      val rawTx: Long = ((reading.txBytes - baseline.txBytes) / dtSeconds).roundToLong()
      val (smoothedRx, smoothedTx) = average.apply(rawRx, rawTx)
      val sample = SpeedSample(smoothedRx, smoothedTx, now, transport(), supported = true)
      emit(sample)
      baseline = Baseline(reading.rxBytes, reading.txBytes, now)
      previousSample = sample
    }
  }
}
