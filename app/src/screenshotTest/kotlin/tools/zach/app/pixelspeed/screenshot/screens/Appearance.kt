package tools.zach.app.pixelspeed.screenshot.screens

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.screenshot.ScreenVariants
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.ui.settings.AppearanceContent

@PreviewTest
@ScreenVariants
@Composable
fun Appearance() {
  Shot { AppearanceContent(settings = Settings(), onBack = {}, onUpdateProfile = {}) }
}

@PreviewTest
@ScreenVariants
@Composable
fun AppearanceAdvanced() {
  Shot {
    AppearanceContent(settings = Settings(advancedMode = true), onBack = {}, onUpdateProfile = {})
  }
}
