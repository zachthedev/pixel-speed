package tools.zach.app.pixelspeed.ui.settings

import android.hardware.input.InputManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.floor

/**
 * The platform's touch-obscuring limit: a window more opaque than this swallows the touches that
 * pass through it, so every place that shows an opacity compares against it. The limit is a global
 * setting a privileged caller can write anything into, so the value is held to zero through one.
 * The Compose Preview renderer has no window services and takes the platform default.
 */
@Composable
internal fun obscuringLimit(): Float {
  if (LocalInspectionMode.current) return DEFAULT_OBSCURING_LIMIT
  val context = LocalContext.current
  // A settings-provider read per recomposition is waste; the value changes only through a
  // privileged write, which a reopened screen picks up.
  return remember(context) {
    clampObscuringLimit(
      context.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch
        ?: DEFAULT_OBSCURING_LIMIT
    )
  }
}

/**
 * [reported] held to zero through one; a value that is not a number takes the platform default,
 * since the limit only decides a warning and a limit of zero would warn on every opacity.
 */
internal fun clampObscuringLimit(reported: Float): Float =
  if (reported.isNaN()) DEFAULT_OBSCURING_LIMIT else reported.coerceIn(0f, 1f)

/**
 * [limit] as the whole percent the opacity controls show, rounded down so the number named in a
 * warning is never above the real limit.
 */
internal fun obscuringLimitPercent(limit: Float): Int = floor(limit * PERCENT).toInt()

private const val DEFAULT_OBSCURING_LIMIT: Float = 0.8f
private const val PERCENT: Float = 100f
