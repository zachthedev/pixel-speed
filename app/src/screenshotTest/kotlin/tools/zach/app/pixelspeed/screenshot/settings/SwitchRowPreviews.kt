package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.SwitchRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

@PreviewTest
@ComponentVariants
@Composable
fun SwitchRowOn() {
  Shot {
    SwitchRow(
      shapes = settingsRowShapes(0, 1),
      title = "Start on boot",
      checked = true,
      onCheckedChange = {},
      subtitle = "The overlay comes back after a restart",
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SwitchRowOff() {
  Shot {
    SwitchRow(
      shapes = settingsRowShapes(0, 1),
      title = "Start on boot",
      checked = false,
      onCheckedChange = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SwitchRowOffWithIconAndSubtitle() {
  Shot {
    SwitchRow(
      shapes = settingsRowShapes(0, 1),
      title = "Show in quick settings",
      checked = false,
      onCheckedChange = {},
      subtitle = "Adds a tile that toggles the overlay",
      icon = ImageVector.vectorResource(R.drawable.ic_readout),
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SwitchRowTwoLineSubtitleCentersTheSwitch() {
  Shot {
    SwitchRow(
      shapes = settingsRowShapes(0, 1),
      title = "Automation broadcasts",
      checked = false,
      onCheckedChange = {},
      subtitle = "Lets automation apps send pause, resume, and toggle broadcasts to the overlay",
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SwitchRowDisabled() {
  Shot {
    SwitchRow(
      shapes = settingsRowShapes(0, 1),
      title = "Start on boot",
      checked = true,
      onCheckedChange = {},
      subtitle = "Requires the notification permission",
      enabled = false,
    )
  }
}
