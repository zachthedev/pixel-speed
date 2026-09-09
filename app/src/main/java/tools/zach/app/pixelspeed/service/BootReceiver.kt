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

/**
 * Restarts the overlay after a reboot or an app update, honoring the enabled switch and the
 * start-on-boot setting. A package replacement always restarts an enabled monitor, since the
 * process that was running it is gone either way.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

  @Inject lateinit var settingsStore: SettingsStore
  @Inject lateinit var controller: MonitorController
  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    val pendingResult = goAsync()
    applicationScope.launch {
      try {
        val settings = settingsStore.settings.first()
        val restarts =
          settings.enabled && (action == Intent.ACTION_MY_PACKAGE_REPLACED || settings.startOnBoot)
        if (restarts && !controller.startIfEnabled()) {
          AppLog.w("Foreground start refused for boot action $action")
        }
      } catch (e: Exception) {
        AppLog.e("Failed to evaluate startup settings for boot action $action", e)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
