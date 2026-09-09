package tools.zach.app.pixelspeed.ui.onboarding

import android.os.Build

enum class OnboardingStep {
  WELCOME,
  OVERLAY,
  NOTIFICATIONS,
  TILE,
  KEEP_ALIVE,
  SYSTEM_NOTICE,
  SIDE_FLIP,
  START,
}

/** The step list for one device. Pure, so the per-device rules are table-tested. */
object OnboardingSteps {
  const val GOOGLE_MANUFACTURER: String = "Google"

  /**
   * The notification permission only exists from API 33. The tile step always shows: the system
   * prompt needs API 33, and older versions get the manual instructions. The keep-alive step is for
   * vendors with their own battery policy, which Google's devices do not have. The notice step
   * walks the shade route that silences the platform's overlay notice, which a keyguard re-posts
   * after every unlock, so it shows only on a device with a secure lock. The side flip step only
   * matters on a display with a centered hole to move around, so [hasCenteredHole] gates it; it
   * sits last, right before Start, so it reads as a finishing touch rather than a permission ask.
   */
  fun forDevice(
    sdkInt: Int,
    manufacturer: String,
    forceKeepAlive: Boolean,
    deviceSecure: Boolean,
    hasCenteredHole: Boolean,
  ): List<OnboardingStep> = buildList {
    add(OnboardingStep.WELCOME)
    add(OnboardingStep.OVERLAY)
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(OnboardingStep.NOTIFICATIONS)
    add(OnboardingStep.TILE)
    if (forceKeepAlive || !isGoogle(manufacturer)) add(OnboardingStep.KEEP_ALIVE)
    if (deviceSecure) add(OnboardingStep.SYSTEM_NOTICE)
    if (hasCenteredHole) add(OnboardingStep.SIDE_FLIP)
    add(OnboardingStep.START)
  }

  /** A persisted index is clamped, so a step list that shrank after an update still resumes. */
  fun resumeIndex(persisted: Int, steps: List<OnboardingStep>): Int =
    if (steps.isEmpty()) 0 else persisted.coerceIn(0, steps.lastIndex)

  /** The overlay permission cannot be skipped, and the last step ends the flow on its own. */
  fun isSkippable(step: OnboardingStep): Boolean =
    step != OnboardingStep.OVERLAY && step != OnboardingStep.START

  private fun isGoogle(manufacturer: String): Boolean =
    manufacturer.trim().equals(GOOGLE_MANUFACTURER, ignoreCase = true)
}

/**
 * Android 15 and later guard the overlay toggle for packages installed from a downloaded or local
 * file, and the app cannot read that guard. The hint therefore fires on the evidence available: the
 * user came back from the settings page without the grant, on a version that guards, from an
 * install source that is guarded.
 */
object RestrictedSettingsHint {
  fun applies(
    sdkInt: Int,
    packageSource: Int,
    granted: Boolean,
    returnedFromSettings: Boolean,
  ): Boolean =
    sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
      !granted &&
      returnedFromSettings &&
      (packageSource == PACKAGE_SOURCE_DOWNLOADED_FILE ||
        packageSource == PACKAGE_SOURCE_LOCAL_FILE)

  /**
   * The values of the matching `PackageInstaller.PACKAGE_SOURCE_*` constants, which exist from
   * API 33.
   */
  private const val PACKAGE_SOURCE_LOCAL_FILE: Int = 3
  private const val PACKAGE_SOURCE_DOWNLOADED_FILE: Int = 4
}
