package tools.zach.app.pixelspeed.sampling

import kotlinx.serialization.Serializable

/** The transport of the default network, for the optional glyph and the offline rule. */
enum class Transport {
  WIFI,
  CELLULAR,
  ETHERNET,
  VPN,
  OTHER,
  NONE,
}

/** Exponential moving average strength applied to bytes per second before formatting. */
@Serializable
enum class Smoothing(val alpha: Double) {
  NONE(1.0),
  LIGHT(0.5),
  HEAVY(0.2),
}

/** Which kernel counters the sampler differentiates. */
@Serializable
enum class CounterSourceKind {
  PHYSICAL_INTERFACES,
  DEFAULT_NETWORK,
  ALL_INTERFACES,
  MOBILE_ONLY,
  NON_MOBILE_ONLY,
}

/**
 * One read of the cumulative counters. [supported] is false when the platform reports
 * TrafficStats.UNSUPPORTED for the selected source.
 */
data class CounterReading(
  val rxBytes: Long,
  val txBytes: Long,
  val atElapsedNanos: Long,
  val supported: Boolean = true,
)

/** Something that reads cumulative byte counters. The service supplies the platform one. */
fun interface CounterSource {
  fun read(): CounterReading
}

/** One tick of the meter. Rates are bytes per second over the real elapsed time. */
data class SpeedSample(
  val rxBytesPerSecond: Long,
  val txBytesPerSecond: Long,
  val atElapsedNanos: Long,
  val transport: Transport,
  val supported: Boolean = true,
)
