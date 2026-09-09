package tools.zach.app.pixelspeed.screenshot

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import tools.zach.app.pixelspeed.ui.theme.PixelSpeedTheme

/** A component in light and dark, at font scale 1 and 2, sized to its content. */
@Preview(name = "light", fontScale = 1f)
@Preview(name = "dark", fontScale = 1f, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "light-2x", fontScale = 2f)
@Preview(name = "dark-2x", fontScale = 2f, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class ComponentVariants

/** A whole screen at the Pixel 9 Pro's size, light and dark, at font scale 1 and 2. */
@Preview(name = "light", fontScale = 1f, device = PHONE)
@Preview(
  name = "dark",
  fontScale = 1f,
  device = PHONE,
  uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
@Preview(name = "light-2x", fontScale = 2f, device = PHONE)
@Preview(
  name = "dark-2x",
  fontScale = 2f,
  device = PHONE,
  uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
annotation class ScreenVariants

/** The Pixel 9 Pro: 1280 by 2856 px at 480 dpi. */
const val PHONE: String = "spec:width=427dp,height=952dp,dpi=480"

/** The app theme over its page surface, so a component renders on the ground it ships on. */
@Composable
fun Shot(content: @Composable () -> Unit) {
  PixelSpeedTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) { Box(Modifier) { content() } }
  }
}
