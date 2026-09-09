package tools.zach.app.pixelspeed.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.signals.sendLatest

/**
 * Tells whether the foreground app is fullscreen through a probe window that exists only while the
 * flow is collected. The probe is an alpha-zero, not-touchable, full-size window that fits the
 * system bars, so two independent readings come from it: the insets it receives report the status
 * bar hidden, and its laid-out height reaches the display height once no bar is reserved. Either
 * reading counts, after a short debounce so an inset animation does not blink the overlay.
 */
class FullscreenDetector(private val context: Context, private val windowManager: WindowManager) {

  fun fullscreen(): Flow<Boolean> = callbackFlow {
    val probe = View(context)
    var statusBarHidden = false
    var fillsDisplay = false
    fun publish() {
      sendLatest(statusBarHidden || fillsDisplay)
    }
    probe.setOnApplyWindowInsetsListener { view, insets ->
      statusBarHidden = !insets.isVisible(WindowInsets.Type.statusBars())
      publish()
      view.onApplyWindowInsets(insets)
    }
    probe.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
      fillsDisplay = bottom - top >= windowManager.currentWindowMetrics.bounds.height()
      publish()
    }
    val attached =
      try {
        windowManager.addView(probe, probeParams())
        true
      } catch (e: WindowManager.BadTokenException) {
        AppLog.e("Fullscreen probe refused: token", e)
        false
      } catch (e: SecurityException) {
        AppLog.e("Fullscreen probe refused: permission", e)
        false
      } catch (e: WindowManager.InvalidDisplayException) {
        AppLog.e("Fullscreen probe refused: display gone", e)
        false
      }
    if (!attached) {
      sendLatest(false)
      close()
    }
    awaitClose {
      if (attached) {
        try {
          windowManager.removeViewImmediate(probe)
        } catch (e: IllegalArgumentException) {
          AppLog.w("Fullscreen probe was already detached", e)
        }
      }
    }
  }
    .conflate()
    .distinctUntilChanged()
    .debounce(DEBOUNCE_MS)

  private fun probeParams(): WindowManager.LayoutParams =
    WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
      )
      .apply {
        alpha = 0f
        fitInsetsTypes = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        isFitInsetsIgnoringVisibility = false
        title = context.getString(R.string.overlay_probe_window_title)
      }

  private companion object {
    const val DEBOUNCE_MS: Long = 500L
  }
}
