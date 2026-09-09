package tools.zach.app.pixelspeed.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Builds the diagnostics file for the Help page and reports whether a build is under way. */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val collector: DiagnosticsCollector) :
  ViewModel() {
  private val mutableBusy: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val lock: Mutex = Mutex()
  private var inFlight: Deferred<File>? = null

  /** True from the first call until the file is written or the write fails. */
  val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

  /**
   * Writes the report. A call while a write is under way joins it and gets the same file, so two
   * taps never write one path at once.
   */
  suspend fun write(): File {
    val job: Deferred<File> = lock.withLock {
      inFlight
        ?: viewModelScope
          .async {
            try {
              collector.write()
            } finally {
              lock.withLock { inFlight = null }
              mutableBusy.value = false
            }
          }
          .also {
            inFlight = it
            mutableBusy.value = true
          }
    }
    return job.await()
  }
}
