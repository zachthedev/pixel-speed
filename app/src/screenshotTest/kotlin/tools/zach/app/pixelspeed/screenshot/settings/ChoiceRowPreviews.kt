package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.ChoiceRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

private val NETWORK_OPTIONS: List<String> = listOf("Wi-Fi only", "Any network", "Never")

/** A pause duration, where null is the option that pauses until the user resumes. */
private val PAUSE_OPTIONS: List<Int?> = listOf(5, 15, 60, null)

@PreviewTest
@ComponentVariants
@Composable
fun ChoiceRowClosed() {
  Shot {
    ChoiceRow(
      shapes = settingsRowShapes(0, 1),
      title = "Update over",
      options = NETWORK_OPTIONS,
      selected = "Any network",
      label = { it },
      onSelected = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun ChoiceRowNullOptionSelected() {
  Shot {
    ChoiceRow(
      shapes = settingsRowShapes(0, 1),
      title = "Tile pause duration",
      options = PAUSE_OPTIONS,
      selected = null,
      label = { minutes -> minutes?.let { "$it min" } ?: "Until I resume" },
      onSelected = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun ChoiceRowDisabled() {
  Shot {
    ChoiceRow(
      shapes = settingsRowShapes(0, 1),
      title = "Update over",
      options = NETWORK_OPTIONS,
      selected = "Any network",
      label = { it },
      onSelected = {},
      enabled = false,
    )
  }
}
