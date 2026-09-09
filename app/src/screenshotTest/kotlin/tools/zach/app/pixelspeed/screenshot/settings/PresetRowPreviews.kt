package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.Preset
import tools.zach.app.pixelspeed.ui.settings.PresetRow
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

private const val WARNING: String = "Above 80%, taps on the overlay don't reach what's under it"

private val OPACITY_PRESETS: List<Preset<Int>> =
  listOf(
    Preset("Opaque", 100, description = WARNING),
    Preset("Translucent", 80),
    Preset("Subtle", 50),
  )

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowSelected() {
  Shot {
    PresetRow(
      shapes = settingsRowShapes(0, 1),
      title = "Opacity",
      presets = OPACITY_PRESETS,
      value = 80,
      valueLabel = { "$it%" },
      onSelected = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowOffPresetSelectsNothing() {
  Shot {
    PresetRow(
      shapes = settingsRowShapes(0, 1),
      title = "Opacity",
      presets = OPACITY_PRESETS,
      value = 65,
      valueLabel = { "$it%" },
      onSelected = {},
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowWithWarning() {
  Shot {
    PresetRow(
      shapes = settingsRowShapes(0, 1),
      title = "Opacity",
      presets = OPACITY_PRESETS,
      value = 100,
      valueLabel = { "$it%" },
      onSelected = {},
      warning = WARNING,
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowNarrowFallsBackToChoiceRow() {
  Shot {
    Box(Modifier.width(200.dp)) {
      PresetRow(
        shapes = settingsRowShapes(0, 1),
        title = "Opacity",
        presets = OPACITY_PRESETS,
        value = 65,
        valueLabel = { "$it%" },
        onSelected = {},
      )
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowNarrowWithWarning() {
  Shot {
    Box(Modifier.width(200.dp)) {
      PresetRow(
        shapes = settingsRowShapes(0, 1),
        title = "Opacity",
        presets = OPACITY_PRESETS,
        value = 100,
        valueLabel = { "$it%" },
        onSelected = {},
        warning = WARNING,
      )
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowDisabledWithWarning() {
  Shot {
    PresetRow(
      shapes = settingsRowShapes(0, 1),
      title = "Opacity",
      presets = OPACITY_PRESETS,
      value = 100,
      valueLabel = { "$it%" },
      onSelected = {},
      warning = WARNING,
      enabled = false,
    )
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun PresetRowDisabled() {
  Shot {
    PresetRow(
      shapes = settingsRowShapes(0, 1),
      title = "Opacity",
      presets = OPACITY_PRESETS,
      value = 80,
      valueLabel = { "$it%" },
      onSelected = {},
      enabled = false,
    )
  }
}
