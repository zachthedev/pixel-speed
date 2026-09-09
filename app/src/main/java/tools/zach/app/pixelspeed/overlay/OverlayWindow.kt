package tools.zach.app.pixelspeed.overlay

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog

/**
 * The one overlay window. It wraps its content, never takes focus or touches outside edit mode, and
 * lays out under the system bars so the placement function can put it anywhere, including the
 * status bar strip. Every method runs on the main thread.
 */
class OverlayWindow(applicationContext: Context) {

  private val displayManager: DisplayManager =
    applicationContext.getSystemService(DisplayManager::class.java)
  private val display: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

  /** A window context carries the display's own metrics, which a Service context does not. */
  private val context: Context =
    applicationContext.createWindowContext(
      display,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      null,
    )

  private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)

  private var host: OverlayComposeHost? = null
  // The two placement facts the params depend on; declared before them, since initializers run
  // in declaration order.
  private var inStatusBar: Boolean = false
  private var editing: Boolean = false
  private var visible: Boolean = true
  private val params: WindowManager.LayoutParams = baseParams()
  private var lastPlacementDp: Pair<Int, Int>? = null
  private var pendingPlacementDp: Pair<Int, Int>? = null

  val isAttached: Boolean
    get() = host != null

  val density: Float
    get() = context.resources.displayMetrics.density

  /** A fullscreen signal on this window's display; the probe exists only while collected. */
  fun fullscreen(): Flow<Boolean> = FullscreenDetector(context, windowManager).fullscreen()

  fun registerConfigurationListener(onChange: (Configuration) -> Unit): () -> Unit {
    val callbacks =
      object : ComponentCallbacks2 {
        private var layoutDirection: Int = context.resources.configuration.layoutDirection

        override fun onConfigurationChanged(newConfig: Configuration) {
          // A locale change can flip the layout direction, which moves the edge x runs from.
          if (newConfig.layoutDirection != layoutDirection) {
            layoutDirection = newConfig.layoutDirection
            host?.view?.let {
              applyFlags()
              updateLayout(it)
            }
          }
          onChange(newConfig)
        }

        override fun onTrimMemory(level: Int) = Unit

        @Deprecated("The platform reports memory pressure through onTrimMemory")
        override fun onLowMemory() = Unit
      }
    // A half turn between the two landscape rotations changes no configuration value, yet it
    // moves the hole to the other side, so the display's own change callback refreshes too.
    val displayListener =
      object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        // The callback also fires for refresh rate and color changes, which move nothing.
        private var rotation: Int = display.rotation

        override fun onDisplayChanged(displayId: Int) {
          if (displayId != display.displayId || display.rotation == rotation) return
          rotation = display.rotation
          onChange(context.resources.configuration)
        }
      }
    context.registerComponentCallbacks(callbacks)
    try {
      displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    } catch (e: RuntimeException) {
      // Neither listener may outlive a failed registration, or the window context leaks.
      context.unregisterComponentCallbacks(callbacks)
      throw e
    }
    return {
      context.unregisterComponentCallbacks(callbacks)
      displayManager.unregisterDisplayListener(displayListener)
    }
  }

  /** The display as the placement function sees it, in dp, read fresh on every call. */
  fun geometry(): DisplayGeometry = displayGeometry(context, windowManager)

  /**
   * Adds the window with the content. Returns false when the platform refuses, which means the
   * overlay permission is gone.
   */
  fun attach(inStatusBar: Boolean, opacity: Float, content: @Composable () -> Unit): Boolean {
    if (host != null) return true
    val newHost = OverlayComposeHost(context)
    newHost.setContent(content)
    this.inStatusBar = inStatusBar
    applyFlags()
    params.alpha = windowAlpha(opacity)
    return try {
      windowManager.addView(newHost.view, params)
      host = newHost
      newHost.show()
      true
    } catch (e: WindowManager.BadTokenException) {
      AppLog.e("Overlay window refused: token", e)
      newHost.destroy()
      newHost.clearViewModels()
      false
    } catch (e: SecurityException) {
      AppLog.e("Overlay window refused: permission", e)
      newHost.destroy()
      newHost.clearViewModels()
      false
    } catch (e: WindowManager.InvalidDisplayException) {
      AppLog.e("Overlay window refused: display gone", e)
      newHost.destroy()
      newHost.clearViewModels()
      false
    }
  }

  /**
   * Moves the window. Coordinates are dp from the display's top-left corner. A drag in progress
   * owns the position, so placement updates wait until edit mode ends.
   */
  fun moveTo(xDp: Int, yDp: Int) {
    val view = host?.view ?: return
    if (editing) {
      pendingPlacementDp = xDp to yDp
      return
    }
    if (lastPlacementDp == xDp to yDp) return
    lastPlacementDp = xDp to yDp
    params.x = xDp.toPx()
    params.y = yDp.toPx()
    updateLayout(view)
  }

  /**
   * Hides by alpha alone. The spec's alpha is zero while hidden, which exempts the window from the
   * obscured-touch rule, and the root stays visible so the window's surface does: the platform
   * posts its "displaying over other apps" notice whenever an app's first overlay surface shows and
   * cancels it when the last one hides, so a surface that hid on every pause would bring the notice
   * back each time after the user dismissed it. A keyguard hides every app overlay surface itself,
   * so on a locked device the notice returns after each unlock whatever this window does.
   */
  fun setVisible(visible: Boolean, opacity: Float) {
    val current = host ?: return
    this.visible = visible
    applyFlags()
    params.alpha = spec(visible, opacity).alpha
    // A kept surface would keep its nodes in the accessibility tree, and a screen reader would
    // find a readout that is not on screen.
    current.view.importantForAccessibility =
      if (visible) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
      else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    if (visible) current.show() else current.hide()
    updateLayout(current.view)
  }

  fun setInStatusBar(inStatusBar: Boolean) {
    val view = host?.view ?: return
    this.inStatusBar = inStatusBar
    applyFlags()
    updateLayout(view)
  }

  /**
   * Makes the window draggable. While editing, touches reach the window, a drag moves it, and a
   * release within [SNAP_DP] of a grid spot snaps there with a haptic tick; [onDrop] receives the
   * final origin in dp. Leaving edit mode restores the touch-through flags and drops the listener.
   */
  fun setEditable(editing: Boolean, onDrop: (xDp: Int, yDp: Int) -> Unit) {
    val view = host?.view ?: return
    if (this.editing == editing) return
    this.editing = editing
    applyFlags()
    if (editing) {
      // The status bar window sits above every overlay and takes the touches there, so a pill in
      // the bar drops below it first, where a finger can reach it.
      if (inStatusBar) {
        val bar = geometry()
        params.y = maxOf(params.y, (bar.statusBarDp + EDGE_DP).toPx())
      }
      view.setOnTouchListener(DragListener(view, onDrop))
    } else {
      view.setOnTouchListener(null)
      lastPlacementDp = null
      val pending = pendingPlacementDp
      pendingPlacementDp = null
      if (pending != null) {
        params.x = pending.first.toPx()
        params.y = pending.second.toPx()
        lastPlacementDp = pending
      }
    }
    updateLayout(view)
  }

  private inner class DragListener(
    private val view: View,
    private val onDrop: (xDp: Int, yDp: Int) -> Unit,
  ) : View.OnTouchListener {
    private var startX: Int = 0
    private var startY: Int = 0
    private var downRawX: Float = 0f
    private var downRawY: Float = 0f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          startX = params.x
          startY = params.y
          downRawX = event.rawX
          downRawY = event.rawY
        }
        MotionEvent.ACTION_MOVE -> {
          val bounds = geometry()
          // The window's x runs from the start edge, so a drag toward the end grows it.
          val toEnd = if (context.isRtl()) -1 else 1
          params.x =
            (startX + toEnd * (event.rawX - downRawX).roundToInt()).coerceIn(
              0,
              (bounds.widthDp.toPx() - view.width).coerceAtLeast(0),
            )
          params.y =
            (startY + (event.rawY - downRawY).roundToInt()).coerceIn(
              0,
              (bounds.heightDp.toPx() - view.height).coerceAtLeast(0),
            )
          updateLayout(view)
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          val snapped = snap(params.x.toDp(), params.y.toDp())
          if (snapped != null) {
            params.x = snapped.first.toPx()
            params.y = snapped.second.toPx()
            updateLayout(view)
            // A snap is a positional cue that can fire on every drag, so it gets the lightest tick.
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
          }
          v.performClick()
          onDrop(params.x.toDp(), params.y.toDp())
        }
      }
      return true
    }

    /** The nine grid spots: start, center, end by top, middle, bottom inside the safe area. */
    private fun snap(xDp: Int, yDp: Int): Pair<Int, Int>? {
      val geometry = geometry()
      val widthDp = view.width.toDp()
      val heightDp = view.height.toDp()
      val xs =
        listOf(EDGE_DP, (geometry.widthDp - widthDp) / 2, geometry.widthDp - widthDp - EDGE_DP)
      val ys =
        listOf(
          geometry.statusBarDp + EDGE_DP,
          (geometry.heightDp - heightDp) / 2,
          geometry.heightDp - geometry.navigationBarDp - heightDp - EDGE_DP,
        )
      val nearestX = xs.minByOrNull { abs(it - xDp) } ?: return null
      val nearestY = ys.minByOrNull { abs(it - yDp) } ?: return null
      val snapX = if (abs(nearestX - xDp) <= SNAP_DP) nearestX else xDp
      val snapY = if (abs(nearestY - yDp) <= SNAP_DP) nearestY else yDp
      return if (snapX == xDp && snapY == yDp) null else snapX to snapY
    }
  }

  fun detach() {
    val current = host ?: return
    current.view.setOnTouchListener(null)
    editing = false
    current.destroy()
    try {
      windowManager.removeViewImmediate(current.view)
    } catch (e: IllegalArgumentException) {
      AppLog.w("Overlay view was already detached", e)
    }
    current.clearViewModels()
    host = null
    lastPlacementDp = null
  }

  private fun updateLayout(view: View) {
    try {
      windowManager.updateViewLayout(view, params)
    } catch (e: IllegalArgumentException) {
      AppLog.w("Overlay layout update on a detached view: x=${params.x} y=${params.y}", e)
    }
  }

  /**
   * The flag set, cutout mode, and gravity for where the window sits, whether it is being edited,
   * and which edge x runs from.
   */
  private fun applyFlags() {
    val spec = spec(visible, opacity = 0f)
    params.flags = spec.flags
    params.layoutInDisplayCutoutMode = spec.layoutInDisplayCutoutMode
    params.gravity = spec.gravity
  }

  /** The spec owns how an opacity becomes a window alpha, so the rule lives in one place. */
  private fun windowAlpha(opacity: Float): Float = spec(visible = true, opacity).alpha

  private fun spec(visible: Boolean, opacity: Float): WindowParamsSpec =
    WindowParamsSpec.of(
      inStatusBar = inStatusBar,
      editing = editing,
      visible = visible,
      opacity = opacity,
      rtl = context.isRtl(),
    )

  private fun baseParams(): WindowManager.LayoutParams {
    val spec = spec(visible = false, opacity = 0f)
    return WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        spec.type,
        spec.flags,
        spec.format,
      )
      .apply {
        gravity = spec.gravity
        fitInsetsTypes = 0
        title = context.getString(R.string.overlay_window_title)
      }
  }

  private fun Int.toDp(): Int = pxToDp(this, density)

  private fun Int.toPx(): Int = dpToPx(this, density)

  private companion object {
    const val SNAP_DP: Int = 12
    const val EDGE_DP: Int = 8
  }
}
