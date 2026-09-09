package tools.zach.app.pixelspeed.signals

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import tools.zach.app.pixelspeed.log.AppLog

/**
 * A platform value read through [read] and kept current through [watch], which registers a change
 * callback and returns the matching unregister. Every collector sees a value read after the watcher
 * is registered, so a change between subscribing and registering cannot be lost, and a collector
 * arriving after the watcher stopped gets a fresh read rather than the last value seen, since the
 * replay clears the moment the upstream stops. A collector joining a live watcher gets the live
 * value at once. Each read and its send happen under one lock, so a change delivered on another
 * thread between a read and its send cannot be overwritten by the older read. A watcher the
 * platform refuses still yields one read, logged, so a collector never waits forever.
 */
fun <T> watchedValue(
  scope: CoroutineScope,
  stopTimeoutMillis: Long,
  read: () -> T,
  watch: (onChange: () -> Unit) -> (() -> Unit),
): Flow<T> = callbackFlow {
  val lock = Any()
  fun readAndSend() = synchronized(lock) { sendLatest(read()) }
  val unregister: () -> Unit =
    try {
      watch(::readAndSend)
    } catch (e: RuntimeException) {
      AppLog.e("The platform refused the change watcher; the value will not follow changes", e)
      val none: () -> Unit = {}
      none
    }
  readAndSend()
  awaitClose { unregister() }
}
  .conflate()
  .shareIn(
    scope,
    SharingStarted.WhileSubscribed(
      stopTimeoutMillis = stopTimeoutMillis,
      replayExpirationMillis = 0,
    ),
    replay = 1,
  )
  .distinctUntilChanged()
