package tools.zach.app.pixelspeed.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.displayContext
import tools.zach.app.pixelspeed.overlay.displayGeometry
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.service.sideFlipServiceEnabled
import tools.zach.app.pixelspeed.settings.SettingsImport
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.MonitorStateHolder

/**
 * Gathers the facts a diagnostics report holds and writes the rendered text to the app's cache,
 * where a content provider hands it to the share sheet. Nothing here names the person or the device
 * instance: the build, the phone model, the display, the permission state, the readout's state, the
 * settings export, the app's own log, and the last kept crash.
 */
class DiagnosticsCollector
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val settingsStore: SettingsStore,
  private val stateHolder: MonitorStateHolder,
) {
  /**
   * Writes the report and returns its file. Runs off the main thread. Each report gets its own name
   * and the earlier ones are removed: a share target that kept an earlier grant reads the report
   * the user reviewed, or nothing, never a later one.
   */
  suspend fun write(): File =
    withContext(Dispatchers.IO) {
      val text: String = DiagnosticsReport.render(collect())
      val dir = File(context.cacheDir, CACHE_DIR)
      if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
      dir.listFiles()?.forEach { old -> if (!old.delete()) AppLog.w("Cannot remove $old") }
      val stamp: String = FILE_STAMP.format(Instant.now().atZone(ZoneId.systemDefault()))
      File(dir, "pixel-speed-diagnostics-$stamp.txt").also { it.writeText(text) }
    }

  private suspend fun collect(): DiagnosticsInputs {
    val settings = settingsStore.settings.first()
    return DiagnosticsInputs(
      generatedAt = Instant.now(),
      zone = ZoneId.systemDefault(),
      app = appFacts(),
      device = deviceFacts(),
      display = displayFacts(),
      permissions = permissionFacts(),
      monitor = monitorFacts(),
      settingsJson = SettingsImport.serialize(settings),
      log = AppLog.lines(),
      lastCrash = lastCrash(),
    )
  }

  private fun appFacts(): List<Pair<String, String>> {
    val manager: PackageManager = context.packageManager
    val info =
      try {
        manager.getPackageInfo(context.packageName, 0)
      } catch (e: PackageManager.NameNotFoundException) {
        AppLog.w("Own package missing while collecting diagnostics", e)
        null
      }
    val installer: String =
      try {
        manager.getInstallSourceInfo(context.packageName).installingPackageName ?: "none"
      } catch (e: PackageManager.NameNotFoundException) {
        "unknown"
      }
    return listOf(
      "Package" to context.packageName,
      "Version" to (info?.versionName?.takeIf { it.isNotBlank() } ?: "development build"),
      "Version code" to (info?.longVersionCode?.toString() ?: "none"),
      "Installed by" to installer,
    )
  }

  private fun deviceFacts(): List<Pair<String, String>> =
    listOf(
      "Model" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
      "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      "Security patch" to Build.VERSION.SECURITY_PATCH,
      "Locales" to context.resources.configuration.locales.toLanguageTags(),
    )

  private fun displayFacts(): List<Pair<String, String>> {
    val configuration = context.resources.configuration
    val metrics = context.resources.displayMetrics
    // The application context has no display of its own; the default display is the one the
    // overlay window is placed on, so the geometry here is the one the placement used.
    val geometry: DisplayGeometry? =
      context
        .getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
        ?.let { display ->
          val windowContext = displayContext(context, display)
          windowContext.getSystemService(WindowManager::class.java)?.let {
            displayGeometry(windowContext, it)
          }
        }
    return listOf(
      "Density" to "${metrics.density} (${metrics.densityDpi} dpi)",
      "Font scale" to configuration.fontScale.toString(),
      "Pixels" to "${metrics.widthPixels} x ${metrics.heightPixels}",
      "Geometry" to (geometry?.toString() ?: "unavailable"),
    )
  }

  private fun permissionFacts(): List<Pair<String, String>> {
    val power: PowerManager? = context.getSystemService(PowerManager::class.java)
    return listOf(
      "Draw over other apps" to AndroidSettings.canDrawOverlays(context).toString(),
      "Notifications" to
        NotificationManagerCompat.from(context).areNotificationsEnabled().toString(),
      "Battery optimizations ignored" to
        (power?.isIgnoringBatteryOptimizations(context.packageName)?.toString() ?: "unknown"),
      "Side flip service enabled" to sideFlipServiceEnabled(context).toString(),
    )
  }

  private fun monitorFacts(): List<Pair<String, String>> {
    val sample: SpeedSample? = stateHolder.latestSample.value
    return listOf(
      "State" to stateHolder.state.value.toString(),
      "Loading" to stateHolder.loading.value.toString(),
      "Overlay fits" to stateHolder.overlayFits.value.toString(),
      "Edit mode" to stateHolder.editMode.value.toString(),
      "Latest sample" to
        (sample?.let {
          "rx ${it.rxBytesPerSecond} B/s, tx ${it.txBytesPerSecond} B/s, ${it.transport}, " +
            "supported ${it.supported}"
        } ?: "none"),
    )
  }

  private fun lastCrash(): String? {
    val file: File = CrashRecorder.fileIn(context.filesDir)
    if (!file.isFile) return null
    return try {
      file.readText()
    } catch (e: IOException) {
      AppLog.w("Cannot read the crash record", e)
      "Unreadable: ${e.message}"
    }
  }

  companion object {
    /** The cache folder the diagnostics provider exposes. */
    const val CACHE_DIR: String = "diagnostics"
    private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
  }
}
