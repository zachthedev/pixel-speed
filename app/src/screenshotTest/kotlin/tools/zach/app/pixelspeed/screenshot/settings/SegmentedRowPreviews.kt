package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.SegmentedRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

private val THREE_OPTIONS: List<String> = listOf("Off", "Auto", "On")
private val FOUR_OPTIONS: List<String> = listOf("1x", "2x", "3x", "4x")

@PreviewTest
@ComponentVariants
@Composable
fun SegmentedRowThreeOptions() {
  Shot {
    SegmentedRow(
      shapes = settingsRowShapes(0, 1),
      title = "Refresh rate",
      options = THREE_OPTIONS,
      selected = "Auto",
      label = { it },
      onSelected = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SegmentedRowFourOptions() {
  Shot {
    SegmentedRow(
      shapes = settingsRowShapes(0, 1),
      title = "Text scale",
      options = FOUR_OPTIONS,
      selected = "2x",
      label = { it },
      onSelected = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SegmentedRowFourOptionsNarrowFallsBackToChoiceRow() {
  Shot {
    Box(Modifier.width(240.dp)) {
      SegmentedRow(
        shapes = settingsRowShapes(0, 1),
        title = "Text scale",
        options = FOUR_OPTIONS,
        selected = "2x",
        label = { it },
        onSelected = {},
      )
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SegmentedRowDisabled() {
  Shot {
    SegmentedRow(
      shapes = settingsRowShapes(0, 1),
      title = "Refresh rate",
      options = THREE_OPTIONS,
      selected = "Auto",
      label = { it },
      onSelected = {},
      enabled = false,
    )
  }
}
