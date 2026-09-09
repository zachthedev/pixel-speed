package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.InfoRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

@PreviewTest
@ComponentVariants
@Composable
fun InfoRowWithSubtitle() {
  Shot {
    InfoRow(
      shapes = settingsRowShapes(0, 1),
      title = "Package",
      subtitle = "tools.zach.app.pixelspeed",
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun InfoRowWithoutSubtitle() {
  Shot { InfoRow(shapes = settingsRowShapes(0, 1), title = "Build 2026.09.1") }
}
