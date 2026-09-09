package tools.zach.app.pixelspeed.service

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.PauseTransitions

/** The static shortcut actions declared in `res/xml/shortcuts.xml`, targeting this activity. */
object ShortcutActions {
  private const val PREFIX: String = "tools.zach.app.pixelspeed.action."
  const val SHORTCUT_PAUSE: String = PREFIX + "SHORTCUT_PAUSE"
  const val SHORTCUT_TOGGLE: String = PREFIX + "SHORTCUT_TOGGLE"
  const val SHORTCUT_PROFILE: String = PREFIX + "SHORTCUT_PROFILE"

  /** String extra on [SHORTCUT_PROFILE]: the id of the profile to switch to. */
  const val EXTRA_PROFILE_ID: String = "tools.zach.app.pixelspeed.extra.PROFILE_ID"
}

/**
 * A trampoline with no UI: it dispatches the shortcut action to [MonitorController] and finishes
 * immediately, which `Theme.NoDisplay` requires before `onCreate` returns.
 */
@AndroidEntryPoint
class ShortcutActivity : ComponentActivity() {

  @Inject lateinit var controller: MonitorController
  @Inject lateinit var settingsStore: SettingsStore
  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val action = intent?.action
    when (action) {
      ShortcutActions.SHORTCUT_PAUSE -> {
        val minutes = minutesExtra(intent)
        if (minutes == null) {
          AppLog.w("Refusing the pause shortcut: minutes extra missing or out of range")
        } else {
          applicationScope.launch {
            controller.pause(minutes)
            if (!controller.startIfEnabled()) {
              AppLog.w("Foreground start refused after the pause shortcut")
            }
          }
        }
      }
      ShortcutActions.SHORTCUT_TOGGLE ->
        applicationScope.launch {
          val minutes = settingsStore.settings.first().activeProfile.pause.tilePauseDurationMinutes
          controller.togglePause(minutes)
          if (!controller.startIfEnabled()) {
            AppLog.w("Foreground start refused after the toggle shortcut")
          }
        }
      ShortcutActions.SHORTCUT_PROFILE -> {
        val profileId = intent?.getStringExtra(ShortcutActions.EXTRA_PROFILE_ID)
        if (profileId == null) {
          AppLog.w("Refusing the profile shortcut: profile id extra missing")
        } else {
          applicationScope.launch {
            val exists = settingsStore.settings.first().profiles.any { it.id == profileId }
            if (!exists) {
              AppLog.w("Refusing the profile shortcut: no profile with id $profileId")
            } else {
              settingsStore.update { it.copy(activeProfileId = profileId) }
              if (!controller.startIfEnabled()) {
                AppLog.w("Foreground start refused after the profile shortcut")
              }
            }
          }
        }
      }
      else -> AppLog.w("Ignoring unknown shortcut action $action")
    }
    finish()
  }

  /**
   * The platform types a shortcut XML extra by its literal, so a decimal value arrives as an int
   * and anything else as a string. Both forms are accepted; a value outside the pause range is
   * refused as null.
   */
  private fun minutesExtra(intent: Intent?): Int? {
    if (intent == null || !intent.hasExtra(MonitorActions.EXTRA_MINUTES)) return null
    val value =
      intent.getStringExtra(MonitorActions.EXTRA_MINUTES)?.toIntOrNull()
        ?: intent.getIntExtra(MonitorActions.EXTRA_MINUTES, 0)
    return value.takeIf { PauseTransitions.isValidDuration(it) }
  }
}
