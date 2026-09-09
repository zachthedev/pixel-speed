package tools.zach.app.pixelspeed.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tools.zach.app.pixelspeed.overlay.MeasuredBar

/**
 * The status bar's most recent measurement. [StatusBarReaderService] writes it as its accessibility
 * events arrive; null means the service has read no bar yet, or has stopped reading one. A
 * [requestRead] from the overlay's side asks the service for a fresh read outside those events, and
 * [overlayVisible] tells it when those events are worth a read at all.
 */
@Singleton
class StatusBarReadings @Inject constructor() {
  private val state: MutableStateFlow<MeasuredBar?> = MutableStateFlow(null)
  val readings: StateFlow<MeasuredBar?> = state

  /**
   * Counts every publish: a read that measured the bar, and the service clearing the reading. A
   * read that held its reading, because the bar was hidden or had not settled, does not count, so a
   * caller waiting for a fresh measurement waits for this to pass its value at the request.
   */
  private val readCount: MutableStateFlow<Long> = MutableStateFlow(0L)
  val reads: StateFlow<Long> = readCount

  // One pending request is enough: the service coalesces them into a single read anyway.
  private val requestFlow: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
  val requests: SharedFlow<Unit> = requestFlow

  /**
   * True while the readout is on screen. System UI's events cost a read only then: a hidden readout
   * asks for its own fresh read when it shows, so the lock screen's window churn is never walked.
   */
  private val visible: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val overlayVisible: StateFlow<Boolean> = visible

  fun setOverlayVisible(shown: Boolean) {
    visible.value = shown
  }

  fun publish(reading: MeasuredBar?) {
    state.value = reading
    readCount.update { it + 1 }
  }

  fun requestRead() {
    requestFlow.tryEmit(Unit)
  }
}
