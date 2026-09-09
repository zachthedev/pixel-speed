package tools.zach.app.pixelspeed

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import dagger.hilt.android.HiltAndroidApp
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.diagnostics.CrashRecorder
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.settings.ProfileShortcuts
import tools.zach.app.pixelspeed.settings.SettingsStore

@HiltAndroidApp
class PixelSpeedApp : Application() {

  @Inject lateinit var settingsStore: SettingsStore
  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

  private var locales: LocaleList = LocaleList.getEmptyLocaleList()

  /** True after the platform refused a relabel, until a foreground start retries it. */
  @Volatile private var refreshPending: Boolean = false

  override fun onCreate() {
    super.onCreate()
    CrashRecorder(CrashRecorder.fileIn(filesDir), versionName()).install()
    locales = resources.configuration.locales
    // A launcher shortcut keeps the label it was published with, so a locale that changed while
    // the process was gone, or changes while it runs, republishes the shortcuts in the new one.
    refreshShortcuts()
    registerActivityLifecycleCallbacks(
      object : ActivityLifecycleCallbacks {
        // The platform's shortcut quota resets when the app comes to the foreground.
        override fun onActivityStarted(activity: Activity) {
          if (refreshPending) refreshShortcuts()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      }
    )
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (newConfig.locales != locales) {
      locales = newConfig.locales
      refreshShortcuts()
    }
  }

  private fun refreshShortcuts() {
    if (!ProfileShortcuts.anyPublished(this)) return
    applicationScope.launch {
      try {
        val profiles =
          withTimeout(SETTINGS_READ_TIMEOUT_MS) { settingsStore.settings.first().profiles }
        refreshPending = ProfileShortcuts.reconcile(this@PixelSpeedApp, profiles).isNotEmpty()
      } catch (e: IOException) {
        AppLog.w("Cannot read the settings, so the launcher shortcuts keep their labels", e)
      } catch (e: TimeoutCancellationException) {
        AppLog.w("The settings did not arrive within $SETTINGS_READ_TIMEOUT_MS ms; shortcuts kept")
      }
    }
  }

  private fun versionName(): String =
    try {
      packageManager.getPackageInfo(packageName, 0).versionName?.takeIf { it.isNotBlank() }
        ?: "development build"
    } catch (e: PackageManager.NameNotFoundException) {
      AppLog.w("Own package missing while reading the version", e)
      "unknown"
    }

  private companion object {
    const val SETTINGS_READ_TIMEOUT_MS: Long = 10_000L
  }
}
