package tools.zach.app.pixelspeed.screenshot.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.overlay.StatusBarFit
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Geometries
import tools.zach.app.pixelspeed.screenshot.Samples
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.screenshot.withPreviewFontScale
import tools.zach.app.pixelspeed.settings.AppearanceSettings
import tools.zach.app.pixelspeed.settings.NotificationSettings
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.ReadoutSurface
import tools.zach.app.pixelspeed.ui.overlay.LocalReadoutMotion
import tools.zach.app.pixelspeed.ui.overlay.OverlayContent
import tools.zach.app.pixelspeed.ui.overlay.WindowOrigin
import tools.zach.app.pixelspeed.ui.overlay.rememberOverlayComposition

@Composable
private fun Composition(
  profile: Profile,
  promotedChipAvailable: Boolean,
  sample: SpeedSample? = Samples.wifi,
  loading: Boolean = false,
) {
  val composition =
    rememberOverlayComposition(
      profile,
      sample,
      promotedChipAvailable = promotedChipAvailable,
      loading = loading,
    )
  var origin: WindowOrigin by remember { mutableStateOf(WindowOrigin(0, 0)) }
  Shot {
    val geometry = withPreviewFontScale(Geometries.phone)
    // One frame is rendered, so every motion is off and the loading dots hold their first frame.
    CompositionLocalProvider(LocalReadoutMotion provides false) {
      Box(Modifier.width(Geometries.phone.widthDp.dp).height(Geometries.phone.statusBarDp.dp)) {
        OverlayContent(
          values = composition.values,
          measureValues = composition.measureValues,
          request = composition.request,
          geometry = geometry,
          presentation = composition.presentation,
          onOrigin = { origin = it },
          onFits = {},
          modifier =
            Modifier.offset { IntOffset(origin.xDp.dp.roundToPx(), origin.yDp.dp.roundToPx()) },
        )
      }
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun CompositionFlank() {
  Composition(Profile(), promotedChipAvailable = false)
}

@PreviewTest
@ComponentVariants
@Composable
fun CompositionPromoted() {
  val profile = Profile(notification = NotificationSettings(surface = ReadoutSurface.BOTH))
  Composition(profile, promotedChipAvailable = true)
}

@PreviewTest
@ComponentVariants
@Composable
fun CompositionSlim() {
  val profile = Profile(appearance = AppearanceSettings(statusBarFit = StatusBarFit.SLIM))
  Composition(profile, promotedChipAvailable = false)
}

/** The wait before the first sample: loading dots where the digits go, no unit label. */
@PreviewTest
@ComponentVariants
@Composable
fun CompositionFlankLoading() {
  Composition(Profile(), promotedChipAvailable = false, sample = null, loading = true)
}

@PreviewTest
@ComponentVariants
@Composable
fun CompositionSlimLoading() {
  val profile = Profile(appearance = AppearanceSettings(statusBarFit = StatusBarFit.SLIM))
  Composition(profile, promotedChipAvailable = false, sample = null, loading = true)
}
