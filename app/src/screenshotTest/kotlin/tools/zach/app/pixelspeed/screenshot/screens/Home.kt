package tools.zach.app.pixelspeed.screenshot.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ScreenVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.ui.home.HomeContent
import tools.zach.app.pixelspeed.ui.home.HomeUiState

private val TWO_PROFILES: List<Profile> =
  listOf(Profile(id = "home", name = "Home"), Profile(id = "work", name = "Work"))

@PreviewTest
@ScreenVariants
@Composable
fun HomeOff() {
  Shot {
    HomeContent(
      state =
        HomeUiState(
          settings = Settings(profiles = TWO_PROFILES, activeProfileId = "home", enabled = false),
          monitor = MonitorState.Off,
          hasOverlayPermission = true,
          autoRevokeWhitelisted = true,
          overlayFits = true,
        ),
      onEnabledChange = {},
      onDismissAutoRevoke = {},
      onPause = {},
      onResume = {},
      onSelectProfile = {},
      onNavigate = {},
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun HomeRunning() {
  Shot {
    HomeContent(
      state =
        HomeUiState(
          settings = Settings(profiles = TWO_PROFILES, activeProfileId = "home", enabled = true),
          monitor = MonitorState.Running(suppressions = emptySet()),
          hasOverlayPermission = true,
          autoRevokeWhitelisted = true,
          overlayFits = true,
        ),
      onEnabledChange = {},
      onDismissAutoRevoke = {},
      onPause = {},
      onResume = {},
      onSelectProfile = {},
      onNavigate = {},
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun HomeAdvanced() {
  Shot {
    HomeContent(
      state =
        HomeUiState(
          settings =
            Settings(
              profiles = TWO_PROFILES,
              activeProfileId = "home",
              enabled = true,
              advancedMode = true,
            ),
          monitor = MonitorState.Running(suppressions = emptySet()),
          hasOverlayPermission = true,
          autoRevokeWhitelisted = true,
          overlayFits = true,
        ),
      onEnabledChange = {},
      onDismissAutoRevoke = {},
      onPause = {},
      onResume = {},
      onSelectProfile = {},
      onNavigate = {},
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun HomeNeedsPermission() {
  Shot {
    HomeContent(
      state =
        HomeUiState(
          settings = Settings(profiles = TWO_PROFILES, activeProfileId = "home", enabled = true),
          monitor = MonitorState.NeedsPermission,
          hasOverlayPermission = false,
          autoRevokeWhitelisted = true,
          overlayFits = true,
        ),
      onEnabledChange = {},
      onDismissAutoRevoke = {},
      onPause = {},
      onResume = {},
      onSelectProfile = {},
      onNavigate = {},
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun HomeOverruns() {
  Shot {
    HomeContent(
      state =
        HomeUiState(
          settings = Settings(profiles = TWO_PROFILES, activeProfileId = "home", enabled = true),
          monitor = MonitorState.Running(suppressions = emptySet()),
          hasOverlayPermission = true,
          autoRevokeWhitelisted = true,
          overlayFits = false,
        ),
      onEnabledChange = {},
      onDismissAutoRevoke = {},
      onPause = {},
      onResume = {},
      onSelectProfile = {},
      onNavigate = {},
    )
  }
}
