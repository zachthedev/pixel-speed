package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.ColorRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

private val SATURATED_ORANGE: Color = Color(0xFFFF6D00)

@PreviewTest
@ComponentVariants
@Composable
fun ColorRowSaturated() {
  Shot {
    ColorRow(
      shapes = settingsRowShapes(0, 1),
      title = "Readout color",
      color = SATURATED_ORANGE,
      onClick = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun ColorRowDisabled() {
  Shot {
    ColorRow(
      shapes = settingsRowShapes(0, 1),
      title = "Readout color",
      color = SATURATED_ORANGE,
      onClick = {},
      enabled = false,
    )
  }
}
