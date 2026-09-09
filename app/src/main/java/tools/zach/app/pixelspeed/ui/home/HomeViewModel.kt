package tools.zach.app.pixelspeed.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.service.MonitorController
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.signals.OverlayPermissionSource
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.MonitorStateHolder

data class HomeUiState(
  val settings: Settings,
  val monitor: MonitorState,
  val hasOverlayPermission: Boolean,
  val autoRevokeWhitelisted: Boolean,
  val overlayFits: Boolean,
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val settingsStore: SettingsStore,
  private val stateHolder: MonitorStateHolder,
  private val controller: MonitorController,
  private val overlayPermissionSource: OverlayPermissionSource,
) : ViewModel() {

  // Both checks are binder calls, so they run on resume, not on every state emission.
  private val overlayPermission: MutableStateFlow<Boolean> =
    MutableStateFlow(controller.hasOverlayPermission)
  // The auto-revoke check reaches the permission controller process, so it never runs on Main; the
  // card stays hidden until the answer arrives.
  private val autoRevokeWhitelisted: MutableStateFlow<Boolean> = MutableStateFlow(true)

  /**
   * The overlay app op as it changes, for Home to collect while visible: the op watcher is the only
   * signal for a grant that arrives without a resume, and collecting it under the screen's
   * lifecycle keeps the watcher registered only while Home is on screen.
   */
  val permissionGrants: Flow<Boolean>
    get() = overlayPermissionSource.granted

  val uiState: StateFlow<HomeUiState?> =
    combine(
        settingsStore.settings,
        stateHolder.state,
        overlayPermission,
        autoRevokeWhitelisted,
        stateHolder.overlayFits,
      ) { settings, monitor, permission, whitelisted, fits ->
        HomeUiState(
          settings = settings,
          monitor = monitor,
          hasOverlayPermission = permission,
          autoRevokeWhitelisted = whitelisted,
          overlayFits = fits,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /**
   * The state for the first frame, from settings the caller already holds, so Home composes its
   * cards with its title instead of after the combined flow's first emission.
   */
  fun initialState(settings: Settings): HomeUiState =
    HomeUiState(
      settings = settings,
      monitor = stateHolder.state.value,
      hasOverlayPermission = overlayPermission.value,
      autoRevokeWhitelisted = autoRevokeWhitelisted.value,
      overlayFits = stateHolder.overlayFits.value,
    )

  init {
    refreshAutoRevoke()
  }

  fun setEnabled(enabled: Boolean) {
    viewModelScope.launch { controller.setEnabled(enabled) }
  }

  /**
   * The overlay app op as Home sees it: the watcher replays its value each time Home becomes
   * visible and reports every change while it stays visible, in either pane of a two-pane window.
   * This is the one place Home starts the service, for a process that died with the switch on, for
   * a grant made on the system page, and for a grant that arrives without a resume. The start never
   * touches the switch: only the user's own tap rolls it back on a refusal, since a refusal here is
   * the platform's, not the user's.
   */
  fun onPermissionChanged(granted: Boolean) {
    overlayPermission.value = granted
    viewModelScope.launch {
      try {
        if (!controller.startIfEnabled()) AppLog.w("Foreground start refused on resume")
      } catch (e: CancellationException) {
        throw e
      } catch (e: IOException) {
        AppLog.e("Cannot read the settings on resume", e)
      } catch (e: RuntimeException) {
        // A start that throws must not take the screen down with it.
        AppLog.e("Foreground start failed on resume", e)
      }
    }
  }

  /**
   * Re-reads both checks when the user returns from a system page, so the first frame back is
   * current; the watcher's replay of the app op lands a dispatch later.
   */
  fun onResumed() {
    overlayPermission.value = controller.hasOverlayPermission
    refreshAutoRevoke()
  }

  private fun refreshAutoRevoke() {
    viewModelScope.launch(Dispatchers.IO) {
      autoRevokeWhitelisted.value = context.packageManager.isAutoRevokeWhitelisted
    }
  }

  fun dismissAutoRevokeNotice() {
    viewModelScope.launch {
      try {
        settingsStore.update { it.copy(autoRevokeNoticeDismissed = true) }
      } catch (e: IOException) {
        AppLog.e("Cannot persist the dismissed notice", e)
      }
    }
  }

  /** [minutes] null pauses until resumed. */
  fun pause(minutes: Int?) {
    viewModelScope.launch { controller.pause(minutes) }
  }

  fun resume() {
    viewModelScope.launch {
      controller.resume()
      controller.startIfEnabled()
    }
  }

  fun selectProfile(id: String) {
    viewModelScope.launch { controller.selectProfile(id) }
  }
}
