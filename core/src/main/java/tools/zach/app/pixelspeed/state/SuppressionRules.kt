package tools.zach.app.pixelspeed.state

import tools.zach.app.pixelspeed.settings.BehaviorSettings
import tools.zach.app.pixelspeed.settings.OfflineMode

/** The platform and network signals a suppression rule reads. */
data class SuppressionSignals(
  val screenInteractive: Boolean,
  val keyguardLocked: Boolean,
  val online: Boolean,
  val idle: Boolean,
  val fullscreen: Boolean,
  val landscape: Boolean,
  val powerSaveOn: Boolean,
)

/** Turns platform signals and behavior settings into the set of active suppression reasons. */
object SuppressionRules {
  /** The reasons the overlay stays hidden right now; empty means it shows. */
  fun evaluate(behavior: BehaviorSettings, signals: SuppressionSignals): Set<SuppressionReason> {
    val reasons = mutableSetOf<SuppressionReason>()
    if (!signals.screenInteractive) reasons.add(SuppressionReason.SCREEN_OFF)
    if (signals.keyguardLocked) reasons.add(SuppressionReason.KEYGUARD)
    if (!signals.online && behavior.offlineMode == OfflineMode.HIDE) {
      reasons.add(SuppressionReason.OFFLINE)
    }
    if (signals.idle && behavior.hideWhenIdle) reasons.add(SuppressionReason.IDLE)
    if (signals.fullscreen && behavior.hideInFullscreen) reasons.add(SuppressionReason.FULLSCREEN)
    if (signals.landscape && behavior.hideInLandscape) reasons.add(SuppressionReason.LANDSCAPE)
    if (signals.powerSaveOn && behavior.pauseOnBatterySaver) {
      reasons.add(SuppressionReason.BATTERY_SAVER)
    }
    return reasons
  }
}
