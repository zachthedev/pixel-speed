package tools.zach.app.pixelspeed.screenshot.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ScreenVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.ui.settings.PositionContent

@PreviewTest
@ScreenVariants
@Composable
fun Position() {
  Shot {
    PositionContent(
      settings = Settings(),
      monitor = MonitorState.Running(suppressions = emptySet()),
      onBack = {},
      onUpdateProfile = {},
      onStartEditMode = {},
    )
  }
}

@PreviewTest
@ScreenVariants
@Composable
fun PositionAdvanced() {
  Shot {
    PositionContent(
      settings = Settings(advancedMode = true),
      monitor = MonitorState.Running(suppressions = emptySet()),
      onBack = {},
      onUpdateProfile = {},
      onStartEditMode = {},
    )
  }
}
