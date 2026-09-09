package tools.zach.app.pixelspeed.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.service.MonitorController
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.ProfileShortcuts
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.MonitorStateHolder

/**
 * The one view model every settings screen shares. Screens read [settings] and write through
 * [updateProfile] or [updateSettings]; the service observes the store, so a change applies live.
 */
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  @ApplicationScope private val applicationScope: CoroutineScope,
  private val settingsStore: SettingsStore,
  private val stateHolder: MonitorStateHolder,
  val controller: MonitorController,
) : ViewModel() {

  val settings: StateFlow<Settings?> =
    settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val monitor: StateFlow<MonitorState> = stateHolder.state

  val editMode: StateFlow<Boolean> = stateHolder.editMode

  /** Rewrites the active profile in place; every other profile is untouched. */
  fun updateProfile(transform: (Profile) -> Profile) {
    updateSettings { settings ->
      val active = settings.activeProfile
      settings.copy(
        profiles = settings.profiles.map { if (it.id == active.id) transform(it) else it }
      )
    }
  }

  fun updateSettings(transform: (Settings) -> Settings) {
    viewModelScope.launch { write(transform) }
  }

  /**
   * A write that can rename or drop profiles (import, reset, rename) also brings the launcher
   * shortcuts back in line with the profiles that remain.
   */
  fun replaceSettings(transform: (Settings) -> Settings) {
    viewModelScope.launch {
      val written = write(transform) ?: return@launch
      // Binder calls and icon loads on a scope that outlives this screen, so leaving the screen
      // right after the write cannot strand a shortcut.
      applicationScope.launch(Dispatchers.Default) {
        ProfileShortcuts.reconcile(context, written.profiles)
      }
    }
  }

  /** The store's failure is the disk's; the screen keeps showing the last value it read. */
  private suspend fun write(transform: (Settings) -> Settings): Settings? =
    try {
      settingsStore.update(transform)
    } catch (e: IOException) {
      AppLog.e("Cannot persist a settings change", e)
      null
    }

  /** Makes the overlay draggable until Done, leaving the app, or the timeout. */
  fun startEditMode() {
    stateHolder.setEditMode(true)
  }

  fun stopEditMode() {
    stateHolder.setEditMode(false)
  }
}
