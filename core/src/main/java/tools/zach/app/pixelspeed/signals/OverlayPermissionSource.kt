package tools.zach.app.pixelspeed.signals

import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings as SystemSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import tools.zach.app.pixelspeed.di.ApplicationScope

/**
 * Whether the app may draw over other apps, kept current through the app op that backs the
 * permission. The system delivers no broadcast on a revoke, so the op watcher is the only signal.
 */
@Singleton
class OverlayPermissionSource
@Inject
constructor(
  @ApplicationContext private val context: Context,
  @ApplicationScope scope: CoroutineScope,
) {

  private val appOps: AppOpsManager = context.getSystemService(AppOpsManager::class.java)

  /**
   * Read after the op watcher registers and re-read on every change; a collector arriving after the
   * watcher stopped gets a fresh read, never the value from before a grant, since the service stops
   * itself on a missing permission and must not act on a stale one.
   */
  val granted: Flow<Boolean> =
    watchedValue(
      scope = scope,
      stopTimeoutMillis = STOP_TIMEOUT_MS,
      read = ::current,
      watch = { onChange ->
        val listener = AppOpsManager.OnOpChangedListener { _, _ -> onChange() }
        appOps.startWatchingMode(
          AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
          context.packageName,
          listener,
        )
        val unregister: () -> Unit = { appOps.stopWatchingMode(listener) }
        unregister
      },
    )

  /** A fresh read for callers outside a collector, such as a resume check. */
  fun current(): Boolean = SystemSettings.canDrawOverlays(context)
}
