package tools.zach.app.pixelspeed.screenshot.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ScreenVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.ui.tile.TileProfileDialog

private val THREE_PROFILES: List<Profile> =
  listOf(
    Profile(id = "home", name = "Home"),
    Profile(id = "office", name = "Office"),
    Profile(id = "travel", name = "Travel"),
  )

@PreviewTest
@ScreenVariants
@Composable
fun TileDialogThreeProfiles() {
  Shot {
    TileProfileDialog(
      settings = Settings(profiles = THREE_PROFILES, activeProfileId = "office"),
      monitor = MonitorState.Running(suppressions = emptySet()),
      onSelect = {},
      onPause = {},
      onResume = {},
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun TileDialogPaused() {
  Shot {
    TileProfileDialog(
      settings = Settings(profiles = THREE_PROFILES, activeProfileId = "office"),
      monitor = MonitorState.Paused(until = null),
      onSelect = {},
      onPause = {},
      onResume = {},
    )
  }
}
