package tools.zach.app.pixelspeed

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.service.MonitorController
import tools.zach.app.pixelspeed.state.MonitorStateHolder
import tools.zach.app.pixelspeed.ui.settings.SettingsViewModel
import tools.zach.app.pixelspeed.ui.theme.PixelSpeedTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var stateHolder: MonitorStateHolder
  @Inject lateinit var controller: MonitorController

  // The same instance the navigation root reads, so the splash lifts on the value it composes.
  private val settingsViewModel: SettingsViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen: SplashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Home starts the service once its first composition collects the permission. A user who
    // leaves during the splash never reaches that, so the launch itself asks as well.
    lifecycleScope.launch {
      try {
        if (!controller.startIfEnabled()) AppLog.w("Foreground start refused at launch")
      } catch (e: CancellationException) {
        throw e
      } catch (e: IOException) {
        AppLog.e("Cannot read the settings at launch", e)
      } catch (e: RuntimeException) {
        // A start that throws before the first frame must not take the launch down with it.
        AppLog.e("Foreground start failed at launch", e)
      }
    }
    // Whether the first screen is onboarding or Home is only known once the settings are read.
    splashScreen.setKeepOnScreenCondition { settingsViewModel.settings.value == null }
    val forceKeepAlive =
      isDebuggable() && intent.getBooleanExtra(EXTRA_FORCE_KEEP_ALIVE_STEP, false)
    setContent { PixelSpeedTheme { AppNavigation(forceKeepAlive = forceKeepAlive) } }
  }

  private fun isDebuggable(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

  /** Leaving the app ends edit mode, so the overlay never stays touchable over another app. */
  override fun onStop() {
    stateHolder.setEditMode(false)
    super.onStop()
  }

  private companion object {
    /** Debug builds honor this boolean extra to show the keep-alive step on any device. */
    const val EXTRA_FORCE_KEEP_ALIVE_STEP: String =
      "tools.zach.app.pixelspeed.extra.FORCE_KEEP_ALIVE_STEP"
  }
}
