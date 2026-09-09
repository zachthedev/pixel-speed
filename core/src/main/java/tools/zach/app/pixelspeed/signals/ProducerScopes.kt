package tools.zach.app.pixelspeed.signals

import kotlinx.coroutines.channels.ProducerScope
import tools.zach.app.pixelspeed.log.AppLog

/**
 * Offers [value] from a platform callback to a conflated callback flow. Conflation means the only
 * refusal is a closed channel, which every callback outlives by a moment, so that result is
 * expected and dropped. Any other refusal means the flow lost its conflation; it is logged rather
 * than thrown, since this runs on a platform callback thread where a throw kills the process.
 */
fun <T> ProducerScope<T>.sendLatest(value: T) {
  val result = trySend(value)
  if (!result.isSuccess && !result.isClosed) {
    AppLog.w("A conflated channel refused a signal; the flow lost its conflate() operator")
  }
}

/** How long a shared signal keeps its receiver registered after its last collector leaves. */
internal const val STOP_TIMEOUT_MS: Long = 5_000L
