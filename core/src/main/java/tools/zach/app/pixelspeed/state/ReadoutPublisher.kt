package tools.zach.app.pixelspeed.state

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Fans one readout out to a set of sinks so that no sink can harm another or the caller: each sink
 * runs in its own coroutine on [dispatcher], a sink that throws anything short of cancellation is
 * reported through [onFailure] and skipped, and a sink that stays suspended past [timeoutMillis] is
 * cut off. A sink that blocks its thread without suspending cannot be cut off; the dispatcher keeps
 * that off the caller's thread.
 */
class ReadoutPublisher(
  private val dispatcher: CoroutineDispatcher,
  private val timeoutMillis: Long,
  private val onFailure: (sink: ReadoutSink, error: Throwable) -> Unit,
) {
  /** Delivers [readout] to every sink at once; returns when each finished or was cut off. */
  suspend fun publish(readout: Readout, targets: List<ReadoutSink>) {
    if (targets.isEmpty()) return
    withContext(dispatcher) {
      coroutineScope { targets.map { sink -> async { publishOne(sink, readout) } }.awaitAll() }
    }
  }

  private suspend fun publishOne(sink: ReadoutSink, readout: Readout) {
    try {
      withTimeout(timeoutMillis) { sink.publish(readout) }
    } catch (e: TimeoutCancellationException) {
      onFailure(sink, e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      onFailure(sink, e)
    }
  }
}
