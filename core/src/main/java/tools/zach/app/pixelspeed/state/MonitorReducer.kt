package tools.zach.app.pixelspeed.state

import java.time.Instant
import tools.zach.app.pixelspeed.settings.ReadoutSurface

/** The inputs the reducer needs to compute [MonitorState] for one instant. */
data class MonitorInputs(
  val enabled: Boolean,
  val hasOverlayPermission: Boolean,
  val paused: Boolean,
  val pausedUntilEpochMillis: Long?,
  val nowEpochMillis: Long,
  val suppressions: Set<SuppressionReason>,
  val surface: ReadoutSurface,
)

/** Computes [MonitorState] from settings and platform signals. Pure, no side effects. */
object MonitorReducer {
  private fun isPaused(inputs: MonitorInputs): Boolean =
    inputs.paused &&
      (inputs.pausedUntilEpochMillis == null ||
        inputs.pausedUntilEpochMillis > inputs.nowEpochMillis)

  /** The state the inputs imply: off, needing the permission, paused, or running. */
  fun reduce(inputs: MonitorInputs): MonitorState {
    if (!inputs.enabled) return MonitorState.Off
    if (!inputs.hasOverlayPermission) return MonitorState.NeedsPermission
    if (isPaused(inputs)) {
      val until = inputs.pausedUntilEpochMillis?.let { Instant.ofEpochMilli(it) }
      return MonitorState.Paused(until)
    }
    return MonitorState.Running(
      suppressions = inputs.suppressions,
      overlayOn = inputs.surface != ReadoutSurface.LIVE_UPDATE,
    )
  }

  /** Milliseconds until a pending pause deadline, or null when none is pending. */
  fun resumeDelayMillis(inputs: MonitorInputs): Long? {
    if (!inputs.enabled || !inputs.hasOverlayPermission) return null
    if (!isPaused(inputs)) return null
    val until = inputs.pausedUntilEpochMillis ?: return null
    return until - inputs.nowEpochMillis
  }
}
