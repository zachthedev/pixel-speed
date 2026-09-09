package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.LinkRow
import tools.zach.app.pixelspeed.ui.settings.SliderRow
import tools.zach.app.pixelspeed.ui.settings.SwitchRow
import tools.zach.app.pixelspeed.ui.settings.settingsCard

@PreviewTest
@ComponentVariants
@Composable
fun SettingsCardThreeRows() {
  Shot {
    LazyColumn {
      settingsCard(title = "Display") {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = "Start on boot",
            checked = true,
            onCheckedChange = {},
          )
        }
        row { shapes -> LinkRow(shapes = shapes, title = "Appearance", onClick = {}) }
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = "Overlay opacity",
            value = 60,
            range = 20..100,
            onValueChange = {},
            format = { "$it%" },
          )
        }
      }
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun SettingsCardSingleRow() {
  Shot {
    LazyColumn {
      settingsCard(title = "Display") {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = "Start on boot",
            checked = true,
            onCheckedChange = {},
          )
        }
      }
    }
  }
}
