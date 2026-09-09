package tools.zach.app.pixelspeed.ui.onboarding

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.service.MonitorController
import tools.zach.app.pixelspeed.service.StatusBarReadings
import tools.zach.app.pixelspeed.service.sideFlipServiceEnabled
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsStore

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val settingsStore: SettingsStore,
  private val controller: MonitorController,
  statusBarReadings: StatusBarReadings,
) : ViewModel() {

  /** The bar's live free span while the reader service runs, null before its first read. */
  val measuredBar: StateFlow<MeasuredBar?> = statusBarReadings.readings

  val settings: StateFlow<Settings?> =
    settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  // Every check here is a binder call, so it runs on resume rather than on every recomposition.
  private val overlayPermission: MutableStateFlow<Boolean> =
    MutableStateFlow(controller.hasOverlayPermission)
  private val batteryExempt: MutableStateFlow<Boolean> =
    MutableStateFlow(isIgnoringBatteryOptimizations())
  private val sideFlipOn: MutableStateFlow<Boolean> =
    MutableStateFlow(sideFlipServiceEnabled(context))

  val hasOverlayPermission: StateFlow<Boolean> = overlayPermission.asStateFlow()
  val ignoringBatteryOptimizations: StateFlow<Boolean> = batteryExempt.asStateFlow()
  val sideFlipEnabled: StateFlow<Boolean> = sideFlipOn.asStateFlow()

  /** Re-reads every system grant after the user returns from a settings page or dialog. */
  fun refreshGrants() {
    overlayPermission.value = controller.hasOverlayPermission
    batteryExempt.value = isIgnoringBatteryOptimizations()
    sideFlipOn.value = sideFlipServiceEnabled(context)
  }

  /**
   * Turns the side flip on for the active profile once the user has enabled its service; the
   * enabled service is the permission, this is the intent.
   */
  fun enableSideFlip() {
    viewModelScope.launch {
      try {
        settingsStore.update { settings ->
          val active = settings.activeProfile
          settings.copy(
            profiles =
              settings.profiles.map { profile ->
                if (profile.id == active.id) {
                  profile.copy(position = profile.position.copy(sideFlip = true))
                } else {
                  profile
                }
              }
          )
        }
      } catch (e: IOException) {
        AppLog.e("Cannot persist the side flip", e)
      }
    }
  }

  /** The step index is persisted so a killed process resumes on the same page. */
  fun saveStep(index: Int) {
    viewModelScope.launch {
      try {
        settingsStore.update { it.copy(onboardingStep = index) }
      } catch (e: IOException) {
        AppLog.e("Cannot persist onboarding step $index", e)
      }
    }
  }

  /** Marks the flow complete, turns the monitor on, then hands control back to navigation. */
  fun start(onStarted: () -> Unit) {
    viewModelScope.launch {
      try {
        settingsStore.update { it.copy(onboardingCompleted = true, onboardingStep = 0) }
        if (!controller.setEnabled(true)) {
          AppLog.w("Foreground start refused at the end of onboarding")
        }
      } catch (e: IOException) {
        AppLog.e("Cannot persist the end of onboarding", e)
      }
      onStarted()
    }
  }

  private fun isIgnoringBatteryOptimizations(): Boolean =
    context
      .getSystemService(PowerManager::class.java)
      .isIgnoringBatteryOptimizations(context.packageName)
}
