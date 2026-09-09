package tools.zach.app.pixelspeed.di

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.service.AndroidMonitorPlatform
import tools.zach.app.pixelspeed.service.MonitorPlatform
import tools.zach.app.pixelspeed.service.Uptime
import tools.zach.app.pixelspeed.settings.DataStoreSettingsStore
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsSerializer
import tools.zach.app.pixelspeed.settings.SettingsStore

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  private const val SETTINGS_FILE: String = "settings.json"

  @Provides
  fun connectivityManager(@ApplicationContext context: Context): ConnectivityManager =
    context.getSystemService(ConnectivityManager::class.java)

  /**
   * One DataStore per file for the whole process. A corrupt file is kept beside the new one so a
   * bug report can carry it, then replaced with defaults.
   */
  @Provides
  @Singleton
  fun settingsDataStore(@ApplicationContext context: Context): DataStore<Settings> {
    val file = context.dataStoreFile(SETTINGS_FILE)
    return DataStoreFactory.create(
      serializer = SettingsSerializer,
      corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
          AppLog.e("Settings file unreadable, resetting to defaults", exception)
          val backup = file.resolveSibling("$SETTINGS_FILE.corrupt")
          try {
            if (file.exists()) file.copyTo(backup, overwrite = true)
          } catch (e: IOException) {
            AppLog.e("Could not keep a copy of the corrupt settings file at $backup", e)
          }
          Settings()
        },
      scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
      produceFile = { file },
    )
  }

  @Provides
  @Singleton
  fun settingsStore(dataStore: DataStore<Settings>): SettingsStore =
    DataStoreSettingsStore(dataStore)

  @Provides fun clock(): Clock = Clock.systemUTC()

  @Provides fun uptime(): Uptime = Uptime { SystemClock.elapsedRealtime() }

  @Provides fun monitorPlatform(platform: AndroidMonitorPlatform): MonitorPlatform = platform

  @Provides
  @Singleton
  @ApplicationScope
  fun applicationScope(): CoroutineScope {
    // A failure in one launched job must not take the process down with it.
    val handler = CoroutineExceptionHandler { _, e ->
      AppLog.e("Unhandled failure on the application scope", e)
    }
    return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
  }
}
