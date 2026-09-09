package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.LinkRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

@PreviewTest
@ComponentVariants
@Composable
fun LinkRowPlain() {
  Shot { LinkRow(shapes = settingsRowShapes(0, 1), title = "Appearance", onClick = {}) }
}

@PreviewTest
@ComponentVariants
@Composable
fun LinkRowWithIconAndSubtitle() {
  Shot {
    LinkRow(
      shapes = settingsRowShapes(0, 1),
      title = "Notifications",
      onClick = {},
      subtitle = "Ongoing while the overlay runs",
      icon = ImageVector.vectorResource(R.drawable.ic_notifications),
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun LinkRowWithTrailing() {
  Shot {
    LinkRow(
      shapes = settingsRowShapes(0, 1),
      title = "App version",
      onClick = {},
      trailing = { Text("1.4.0") },
    )
  }
}
