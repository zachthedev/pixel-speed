package tools.zach.app.pixelspeed.screenshot.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.Geometries
import tools.zach.app.pixelspeed.screenshot.ScreenVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.ui.onboarding.OnboardingActions
import tools.zach.app.pixelspeed.ui.onboarding.OnboardingContent
import tools.zach.app.pixelspeed.ui.onboarding.OnboardingStep
import tools.zach.app.pixelspeed.ui.onboarding.OnboardingSteps

private val DEVICE_STEPS: List<OnboardingStep> =
  OnboardingSteps.forDevice(
    sdkInt = 37,
    manufacturer = "Google",
    forceKeepAlive = false,
    deviceSecure = true,
    hasCenteredHole = true,
  )

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingWelcome() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = 0,
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = false,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = false,
      actions = OnboardingActions.NoOps,
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingOverlayGranted() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = DEVICE_STEPS.indexOf(OnboardingStep.OVERLAY),
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = true,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = false,
      actions = OnboardingActions.NoOps,
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingOverlayBlocked() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = DEVICE_STEPS.indexOf(OnboardingStep.OVERLAY),
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = false,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = true,
      actions = OnboardingActions.NoOps,
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingTile() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = DEVICE_STEPS.indexOf(OnboardingStep.TILE),
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = true,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = false,
      actions = OnboardingActions.NoOps,
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingSystemNotice() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = DEVICE_STEPS.indexOf(OnboardingStep.SYSTEM_NOTICE),
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = true,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = false,
      actions = OnboardingActions.NoOps,
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingSideFlip() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = DEVICE_STEPS.indexOf(OnboardingStep.SIDE_FLIP),
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = true,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = false,
      actions = OnboardingActions.NoOps,
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun OnboardingStart() {
  Shot {
    OnboardingContent(
      steps = DEVICE_STEPS,
      index = DEVICE_STEPS.indexOf(OnboardingStep.START),
      geometry = Geometries.phone,
      profile = Profile(),
      hasOverlayPermission = true,
      batteryExempt = false,
      sideFlipEnabled = false,
      tileAdded = false,
      tileDeclined = false,
      settingsPageMissing = false,
      showRestrictedSettingsCard = false,
      actions = OnboardingActions.NoOps,
    )
  }
}
