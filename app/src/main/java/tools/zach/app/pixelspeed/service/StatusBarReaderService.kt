package tools.zach.app.pixelspeed.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.TAG
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.BarSpanReader
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.overlay.NodeBox
import tools.zach.app.pixelspeed.settings.SettingsStore

/**
 * Reads the status bar's own icons through the accessibility API and publishes their free span to
 * [StatusBarReadings], for [tools.zach.app.pixelspeed.overlay.DisplayGeometry.measuredBar]. The
 * config in `res/xml/status_bar_reader.xml` names no package. The window list a service holds
 * refreshes only through the events it receives, and a window-changed event carries no package
 * name, so no scope can keep those. Every event is only a trigger: a window change from anywhere or
 * a content change from System UI schedules a read, and every other event is dropped unread. This
 * class reads only the status bar window it finds, and only bounds and labels, never text content
 * beyond whether a node carries one.
 */
@AndroidEntryPoint
class StatusBarReaderService : AccessibilityService() {

  @Inject lateinit var readings: StatusBarReadings
  @Inject lateinit var settingsStore: SettingsStore

  private val handler: Handler = Handler(Looper.getMainLooper())
  private var readPending: Boolean = false
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /**
   * The active profile's side flip setting. The system grant keeps this service bound whatever the
   * setting says, so a setting turned off stops the reads here rather than in the consumer.
   */
  private var sideFlipWanted: Boolean = false

  /**
   * The bar is read once on connect, nothing in it need change for the first measurement, and again
   * whenever the overlay asks.
   */
  override fun onServiceConnected() {
    super.onServiceConnected()
    scope.coroutineContext.cancelChildren()
    scope.launch { readings.requests.collect { scheduleRead() } }
    scope.launch {
      settingsStore.settings
        .map { it.activeProfile.position.sideFlip }
        .distinctUntilChanged()
        .collect { wanted ->
          sideFlipWanted = wanted
          scheduleRead()
        }
    }
  }

  /**
   * Only an event that can have moved the bar's icons costs a read, and only while the readout is
   * on screen to follow them; a hidden readout asks for its own read when it shows, so the lock
   * screen's window churn walks nothing. The overlay's own spoken readout changes every tick and
   * any busy app fires content changes many times a second. Neither reaches the bar, so neither
   * reaches the handler. A window change from anywhere passes, the overlay's own resize among them,
   * and costs one read per [THROTTLE_MILLIS] at most.
   */
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!sideFlipWanted || !readings.overlayVisible.value) return
    if (event == null || !event.triggersRead()) return
    scheduleRead()
  }

  private fun AccessibilityEvent.triggersRead(): Boolean =
    when (eventType) {
      AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
        packageName?.toString() == StatusBarWindowPicker.SYSTEMUI_PACKAGE
      else -> false
    }

  private fun scheduleRead() {
    if (readPending) return
    readPending = true
    handler.postDelayed(
      {
        readPending = false
        readBar()
      },
      THROTTLE_MILLIS,
    )
  }

  override fun onInterrupt() {
    readings.publish(null)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    handler.removeCallbacksAndMessages(null)
    readings.publish(null)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    scope.cancel()
    handler.removeCallbacksAndMessages(null)
    readings.publish(null)
    super.onDestroy()
  }

  /** Nothing thrown by a system window walk reaches the process; the reading is null instead. */
  private fun readBar() {
    if (!sideFlipWanted) {
      readings.publish(null)
      return
    }
    try {
      readBarOrThrow()
    } catch (e: RuntimeException) {
      AppLog.w("Status bar read failed", e)
      readings.publish(null)
    }
  }

  /**
   * A bar that cannot be read right now keeps the last reading. The shade and Quick Settings take
   * the bar window out of the accessibility list while they are up, and the icons behind them have
   * not moved; a null here would send the pill back to the estimate and then to the bar again once
   * the shade is gone. The bar's next event reads it afresh.
   */
  private fun readBarOrThrow() {
    val windowInfos = windows ?: return holdReading { "no windows" }
    val statusBarHeightPx = statusBarHeightPx()
    val candidates = windowInfos.map { toCandidate(it, statusBarHeightPx) }
    val index =
      StatusBarWindowPicker.pickIndex(candidates, statusBarHeightPx)
        ?: return holdReading { "no bar among " + candidates.joinToString { it.shape() } }
    val bar = candidates[index]
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      // Only the bar's own identity is logged; the other windows are counted, never named.
      AppLog.d("Status bar window: ${bar.shape()} among ${candidates.size}")
    }
    val root = windowInfos[index].root ?: return holdReading { "no root" }
    val boxes =
      collectLeafBoxes(RealAccessibilityNode(root)) { node ->
        (node as? RealAccessibilityNode)?.recycleIfNeeded()
      }
    recycleIfNeeded(root)
    val measured: MeasuredBar? =
      BarSpanReader.read(
        boxes = boxes,
        barWidthPx = bar.rightPx - bar.leftPx,
        barHeightPx = bar.bottomPx - bar.topPx,
        centerPx = topCutoutCenterPx(bar),
        density = resources.displayMetrics.density,
        rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
      )
    // A bar with no box inside it is mid-transition, as across an unlock, when the window is back
    // before its icons have settled into it; the last reading stands until they have.
    if (measured == null) return holdReading { "no span in ${boxes.size} boxes" }
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      AppLog.d("Status bar read: ${boxes.size} boxes in ${bar.shape()}, span $measured")
    }
    readings.publish(measured)
  }

  /** A window's type, package, and bounds for the log; never its title. */
  private fun WindowCandidate.shape(): String =
    (if (isSystemWindow) "system" else "app") +
      "/" +
      packageName +
      "/" +
      "$leftPx,$topPx-$rightPx,$bottomPx"

  /** The reading stands; a caller waiting on a measurement keeps waiting. */
  private fun holdReading(reason: () -> String) {
    if (Log.isLoggable(TAG, Log.DEBUG)) AppLog.d("Status bar read held: ${reason()}")
  }

  /**
   * The window's type, title, and bounds cost no IPC; its root is fetched only for a system window
   * shaped like a status bar, so no other window's content is ever touched.
   */
  private fun toCandidate(
    window: AccessibilityWindowInfo,
    statusBarHeightPx: Int,
  ): WindowCandidate {
    val rect = Rect()
    window.getBoundsInScreen(rect)
    val isSystemWindow = window.type == AccessibilityWindowInfo.TYPE_SYSTEM
    val barShaped = rect.top <= 0 && (rect.bottom - rect.top) in 1..statusBarHeightPx
    val packageName =
      if (isSystemWindow && barShaped) {
        val root = window.root
        val name = root?.packageName?.toString()
        recycleIfNeeded(root)
        name
      } else {
        null
      }
    return WindowCandidate(
      title = window.title?.toString(),
      isSystemWindow = isSystemWindow,
      packageName = packageName,
      leftPx = rect.left,
      topPx = rect.top,
      rightPx = rect.right,
      bottomPx = rect.bottom,
    )
  }

  /**
   * Where the camera hole's center sits, from the display's own centered top cutout, or the bar's
   * own center when there is none or it sits off-center.
   */
  private fun topCutoutCenterPx(bar: WindowCandidate): Int {
    val barCenterPx = (bar.leftPx + bar.rightPx) / 2
    val displayWidthPx = resources.displayMetrics.widthPixels
    // A plain Service, unlike an Activity, is not associated with a display, so the display comes
    // from the display manager rather than the Context.getDisplay() that only a visual context has.
    val display = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    val boundingRects = display?.cutout?.boundingRects.orEmpty()
    val centered =
      boundingRects
        .filter { rect ->
          abs((rect.left + rect.right) / 2 - displayWidthPx / 2) <= displayWidthPx / 5
        }
        .minByOrNull { rect -> abs((rect.left + rect.right) / 2 - displayWidthPx / 2) }
    return centered?.let { (it.left + it.right) / 2 } ?: barCenterPx
  }

  /**
   * The tallest window the bounds fallback accepts as the status bar. A window-less service reads
   * no inset, so a fraction of the display stands in: every status bar is far shorter, and every
   * other system window at the top edge (the shade, the keyguard) is far taller.
   */
  private fun statusBarHeightPx(): Int =
    resources.displayMetrics.heightPixels / STATUS_BAR_MAX_DISPLAY_FRACTION

  private fun recycleIfNeeded(node: AccessibilityNodeInfo?) {
    if (node != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      @Suppress("DEPRECATION") node.recycle()
    }
  }

  private inner class RealAccessibilityNode(val node: AccessibilityNodeInfo) :
    AccessibilityNodeAdapter {
    override val childCount: Int
      get() = node.childCount

    override fun childAt(index: Int): AccessibilityNodeAdapter? =
      node.getChild(index)?.let(::RealAccessibilityNode)

    override val hasLabel: Boolean
      get() = !node.contentDescription.isNullOrBlank() || !node.text.isNullOrBlank()

    override val boundsInScreen: NodeBox
      get() {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return NodeBox(rect.left, rect.top, rect.right, rect.bottom)
      }

    fun recycleIfNeeded() = recycleIfNeeded(node)
  }

  private companion object {
    /** Coalesces a burst of window and content events into one read. */
    const val THROTTLE_MILLIS: Long = 150L
    /** The status bar is never taller than a tenth of the display. */
    const val STATUS_BAR_MAX_DISPLAY_FRACTION: Int = 10
  }
}
