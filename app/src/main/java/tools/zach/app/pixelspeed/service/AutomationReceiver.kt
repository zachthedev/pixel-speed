package tools.zach.app.pixelspeed.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.PauseTransitions

/** The broadcast actions third-party automation apps use to control the monitor. */
object AutomationActions {
  private const val PREFIX: String = "tools.zach.app.pixelspeed.action."
  const val AUTOMATION_PAUSE: String = PREFIX + "AUTOMATION_PAUSE"
  const val AUTOMATION_RESUME: String = PREFIX + "AUTOMATION_RESUME"
  const val AUTOMATION_TOGGLE: String = PREFIX + "AUTOMATION_TOGGLE"

  /** The pause length in minutes on [AUTOMATION_PAUSE] and [AUTOMATION_TOGGLE]. */
  val MINUTES: String = MonitorActions.EXTRA_MINUTES
}

/**
 * Answers pause, resume, and toggle broadcasts from automation apps, gated by
 * [tools.zach.app.pixelspeed.settings.Settings.automationIntents]. The receiver is open to every
 * app on purpose, so it only ever writes the pause flag, and a minutes extra outside
 * [PauseTransitions.MIN_MINUTES] to [PauseTransitions.MAX_MINUTES] is refused rather than read as
 * an indefinite pause. A resume broadcast that arrives while the service is dead can be refused by
 * the platform: a broadcast from another app grants this app no foreground start allowance, so
 * [MonitorController.startIfEnabled] can return false here in a way it would not from an in-app
 * caller.
 */
@AndroidEntryPoint
class AutomationReceiver : BroadcastReceiver() {

  @Inject lateinit var settingsStore: SettingsStore
  @Inject lateinit var controller: MonitorController
  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    if (
      action != AutomationActions.AUTOMATION_PAUSE &&
        action != AutomationActions.AUTOMATION_RESUME &&
        action != AutomationActions.AUTOMATION_TOGGLE
    ) {
      AppLog.w("Ignoring unknown automation action $action")
      return
    }
    val minutes: Int? =
      if (intent.hasExtra(AutomationActions.MINUTES)) {
        val value = intent.getIntExtra(AutomationActions.MINUTES, 0)
        if (!PauseTransitions.isValidDuration(value)) {
          AppLog.w("Refusing automation action $action: minutes $value out of range")
          return
        }
        value
      } else {
        null
      }
    val pendingResult = goAsync()
    applicationScope.launch {
      try {
        val settings = settingsStore.settings.first()
        if (!settings.automationIntents) {
          AppLog.w("Dropping automation intent $action: automation intents are disabled")
          return@launch
        }
        when (action) {
          AutomationActions.AUTOMATION_PAUSE -> controller.pause(minutes)
          AutomationActions.AUTOMATION_RESUME -> controller.resume()
          else -> controller.togglePause(minutes)
        }
        if (!controller.startIfEnabled()) {
          AppLog.w("Foreground start refused after automation action $action")
        }
      } catch (e: Exception) {
        AppLog.e("Automation action $action failed", e)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
