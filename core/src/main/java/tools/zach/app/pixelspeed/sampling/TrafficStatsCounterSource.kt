package tools.zach.app.pixelspeed.sampling

import android.net.TrafficStats
import android.os.SystemClock

/**
 * Reads the kernel's cumulative byte counters through TrafficStats for one [CounterSourceKind].
 * [physicalInterfaces] and [defaultInterface] are the live interface names the network source
 * tracks; the service builds a new instance whenever they change so the sampler re-baselines.
 */
class TrafficStatsCounterSource(
  private val kind: CounterSourceKind,
  private val physicalInterfaces: Set<String>,
  private val defaultInterface: String?,
  private val elapsedNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) : CounterSource {

  override fun read(): CounterReading {
    val at = elapsedNanos()
    val (rx, tx) =
      when (kind) {
        CounterSourceKind.ALL_INTERFACES ->
          TrafficStats.getTotalRxBytes() to TrafficStats.getTotalTxBytes()
        CounterSourceKind.MOBILE_ONLY ->
          TrafficStats.getMobileRxBytes() to TrafficStats.getMobileTxBytes()
        CounterSourceKind.NON_MOBILE_ONLY -> nonMobile()
        CounterSourceKind.DEFAULT_NETWORK -> sumOf(listOfNotNull(defaultInterface))
        CounterSourceKind.PHYSICAL_INTERFACES -> sumOf(physicalInterfaces)
      }
    val supported =
      rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()
    return CounterReading(rxBytes = rx, txBytes = tx, atElapsedNanos = at, supported = supported)
  }

  private fun nonMobile(): Pair<Long, Long> {
    val totalRx = TrafficStats.getTotalRxBytes()
    val totalTx = TrafficStats.getTotalTxBytes()
    val mobileRx = TrafficStats.getMobileRxBytes()
    val mobileTx = TrafficStats.getMobileTxBytes()
    val unsupported = TrafficStats.UNSUPPORTED.toLong()
    if (totalRx == unsupported || totalTx == unsupported) return unsupported to unsupported
    // The two reads are separate kernel calls, so mobile can outrun total by a few bytes in
    // between.
    return (totalRx - mobileRx.coerceAtLeast(0)).coerceAtLeast(0) to
      (totalTx - mobileTx.coerceAtLeast(0)).coerceAtLeast(0)
  }

  /** Per-interface counters. An empty set reads as zero traffic rather than as unsupported. */
  private fun sumOf(interfaces: Collection<String>): Pair<Long, Long> {
    var rx = 0L
    var tx = 0L
    val unsupported = TrafficStats.UNSUPPORTED.toLong()
    for (name in interfaces) {
      val ifaceRx = TrafficStats.getRxBytes(name)
      val ifaceTx = TrafficStats.getTxBytes(name)
      if (ifaceRx == unsupported || ifaceTx == unsupported) return unsupported to unsupported
      rx += ifaceRx
      tx += ifaceTx
    }
    return rx to tx
  }
}
