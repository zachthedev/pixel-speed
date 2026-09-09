package tools.zach.app.pixelspeed.state

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.zach.app.pixelspeed.sampling.SpeedSample

/**
 * Process-wide state shared by the service, the app, the tile, and the notification. The latest
 * sample lives in its own flow so state collectors do not wake on every tick.
 */
@Singleton
class MonitorStateHolder @Inject constructor() {
  private val mutableState: MutableStateFlow<MonitorState> = MutableStateFlow(MonitorState.Off)
  private val mutableSample: MutableStateFlow<SpeedSample?> = MutableStateFlow(null)
  private val mutableLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
  private val mutableEditMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val mutableOverlayFits: MutableStateFlow<Boolean> = MutableStateFlow(true)

  /** The monitor state the service last reduced. */
  val state: StateFlow<MonitorState> = mutableState.asStateFlow()
  /** The newest sample; null before the first one and while sampling is stopped. */
  val latestSample: StateFlow<SpeedSample?> = mutableSample.asStateFlow()
  /**
   * True while no sample has arrived and nothing has said there will be none: the wait the overlay
   * draws. A fresh or [clear]ed holder waits, so a readout drawn before the sampler collector runs
   * shows the wait and never a dash. Only [setSample] with null ends it without a sample.
   */
  val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()

  /** True while the overlay is touchable and draggable; the service applies it to the window. */
  val editMode: StateFlow<Boolean> = mutableEditMode.asStateFlow()

  /** False while the placed look overruns the clock, the icons, or the bar. */
  val overlayFits: StateFlow<Boolean> = mutableOverlayFits.asStateFlow()

  /** Publishes the reduced state. */
  fun setState(state: MonitorState) {
    mutableState.value = state
  }

  /** Publishes a sample, or null when no sampler runs or the source has nothing to read. */
  fun setSample(sample: SpeedSample?) {
    mutableSample.value = sample
    mutableLoading.value = false
  }

  /** A sampler is starting; until it publishes, a readout with nothing to show waits. */
  fun expectSample() {
    mutableLoading.value = mutableSample.value == null
  }

  /** Forgets the sample and waits for the next sampler's word, as a fresh holder does. */
  fun clear() {
    mutableSample.value = null
    mutableLoading.value = true
  }

  /** Enters or leaves edit mode. */
  fun setEditMode(editing: Boolean) {
    mutableEditMode.value = editing
  }

  /** Records whether the placed look fits its bar. */
  fun setOverlayFits(fits: Boolean) {
    mutableOverlayFits.value = fits
  }
}
