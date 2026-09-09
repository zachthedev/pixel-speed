package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.SliderRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

private val OPACITY_RANGE: IntRange = 20..100

@PreviewTest
@ComponentVariants
@Composable
fun SliderRowMidRange() {
  Shot {
    SliderRow(
      shapes = settingsRowShapes(0, 1),
      title = "Overlay opacity",
      value = 60,
      range = OPACITY_RANGE,
      onValueChange = {},
      format = { "$it%" },
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SliderRowAtFloor() {
  Shot {
    SliderRow(
      shapes = settingsRowShapes(0, 1),
      title = "Overlay opacity",
      value = OPACITY_RANGE.first,
      range = OPACITY_RANGE,
      onValueChange = {},
      format = { "$it%" },
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SliderRowWithSteps() {
  Shot {
    SliderRow(
      shapes = settingsRowShapes(0, 1),
      title = "Significant figures",
      value = 3,
      range = 1..6,
      onValueChange = {},
      format = { "Figures: $it" },
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SliderRowWithWarning() {
  Shot {
    SliderRow(
      shapes = settingsRowShapes(0, 1),
      title = "Overlay opacity",
      value = 90,
      range = OPACITY_RANGE,
      onValueChange = {},
      format = { "$it%" },
      warning = "Above 80%, taps on the overlay don't reach what's under it",
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SliderRowDisabled() {
  Shot {
    SliderRow(
      shapes = settingsRowShapes(0, 1),
      title = "Overlay opacity",
      value = 60,
      range = OPACITY_RANGE,
      onValueChange = {},
      format = { "$it%" },
      enabled = false,
    )
  }
}
