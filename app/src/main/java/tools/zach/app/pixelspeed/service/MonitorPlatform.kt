package tools.zach.app.pixelspeed.service

import android.content.Context
import android.provider.Settings as AndroidSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the controller asks of the platform: the overlay grant, and the service's start and stop.
 */
interface MonitorPlatform {
  val hasOverlayPermission: Boolean

  /**
   * Returns false when the platform refuses a foreground start from the background. [userAsked]
   * marks a start the user's own switch requested, the only kind whose later failure may turn that
   * switch back off.
   */
  fun start(userAsked: Boolean): Boolean

  fun stop()
}

@Singleton
class AndroidMonitorPlatform @Inject constructor(@ApplicationContext private val context: Context) :
  MonitorPlatform {
  override val hasOverlayPermission: Boolean
    get() = AndroidSettings.canDrawOverlays(context)

  override fun start(userAsked: Boolean): Boolean = MonitorActions.start(context, userAsked)

  override fun stop() = MonitorActions.stop(context)
}
