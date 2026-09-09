package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.AnchorRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

@PreviewTest
@ComponentVariants
@Composable
fun AnchorRowNarrow() {
  Shot {
    Box(Modifier.width(360.dp)) {
      AnchorRow(
        shapes = settingsRowShapes(0, 1),
        title = "Position",
        selected = Anchor.STATUS_BAR_CUTOUT,
        onSelected = {},
      )
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun AnchorRowWide() {
  Shot {
    Box(Modifier.width(600.dp)) {
      AnchorRow(
        shapes = settingsRowShapes(0, 1),
        title = "Position",
        selected = Anchor.STATUS_BAR_CUTOUT,
        onSelected = {},
      )
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun AnchorRowDisabled() {
  Shot {
    Box(Modifier.width(360.dp)) {
      AnchorRow(
        shapes = settingsRowShapes(0, 1),
        title = "Position",
        selected = Anchor.STATUS_BAR_CUTOUT,
        onSelected = {},
        enabled = false,
      )
    }
  }
}
