package tools.zach.app.pixelspeed.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.RectDp
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport

/** Display geometries the placement math is exercised on, in dp. */
object Geometries {
  /**
   * Pixel 9 Pro as the phone reports it: a 68 dp bar, the cutout rect 195 to 232 dp by 0 to 68, and
   * the glass circle 197 to 230 dp across from 18 dp down, so the hole is centered in the bar.
   */
  val phone: DisplayGeometry =
    DisplayGeometry(
      widthDp = 426,
      heightDp = 952,
      statusBarDp = 68,
      navigationBarDp = 48,
      cutoutRects =
        listOf(RectDp(195, 0, 232, 68, glassTop = 18, glassLeft = 197, glassRight = 230)),
      landscape = false,
    )
}

/**
 * [geometry] with its font scale matched to the preview's own, so the placement math and the
 * rendered text agree the way [tools.zach.app.pixelspeed.overlay.displayGeometry] does in the app.
 */
@Composable
fun withPreviewFontScale(geometry: DisplayGeometry): DisplayGeometry =
  geometry.copy(fontScale = LocalDensity.current.fontScale)

/** Rates a readout shows: about 12.3 Mbps down and 1.2 Mbps up over Wi-Fi. */
object Samples {
  val wifi: SpeedSample =
    SpeedSample(
      rxBytesPerSecond = 1_540_000,
      txBytesPerSecond = 150_000,
      atElapsedNanos = 0,
      transport = Transport.WIFI,
    )
}
