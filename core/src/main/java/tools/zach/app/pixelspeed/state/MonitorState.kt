package tools.zach.app.pixelspeed.state

import java.time.Instant

/**
 * A rule that hides the overlay without the user asking. The app names each one on Home.
 * [stopsSampling] marks the reasons under which the counters are not worth reading. The lock screen
 * keeps sampling: the first sample takes a whole tick, so a sampler started on the unlock would
 * show the loading dots on every return, while one started when the screen wakes has a reading
 * ready by the time the unlock shows the readout.
 */
enum class SuppressionReason(val stopsSampling: Boolean) {
  SCREEN_OFF(true),
  KEYGUARD(false),
  OFFLINE(true),
  IDLE(false),
  FULLSCREEN(false),
  LANDSCAPE(false),
  BATTERY_SAVER(true),
}

/** What the monitor is doing right now. The service writes it; every surface reads it. */
sealed interface MonitorState {
  /** The monitor is disabled. */
  data object Off : MonitorState

  /** Enabled, but the overlay permission is not held. */
  data object NeedsPermission : MonitorState

  /**
   * Enabled with the permission held. [overlayOn] is false when the readout surface is the Live
   * Update notification alone: the window is never shown but sampling continues.
   */
  data class Running(val suppressions: Set<SuppressionReason>, val overlayOn: Boolean = true) :
    MonitorState {
    val visible: Boolean
      get() = overlayOn && suppressions.isEmpty()

    val sampling: Boolean
      get() = suppressions.none { it.stopsSampling }
  }

  /** The user asked for a stop. [until] is null for a pause that lasts until resumed. */
  data class Paused(val until: Instant?) : MonitorState
}
