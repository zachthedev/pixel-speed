package tools.zach.app.pixelspeed.screenshot.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.ui.settings.SettingsCardTitle

@PreviewTest
@ComponentVariants
@Composable
fun SettingsCardTitleAlone() {
  Shot { SettingsCardTitle("Display") }
}
