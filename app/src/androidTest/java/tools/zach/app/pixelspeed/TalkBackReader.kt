package tools.zach.app.pixelspeed

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Method
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks a screen with TalkBack's own navigation so a TalkBack running beside this instrumentation
 * speaks each item into logcat. Injected touches bypass the accessibility input filter and an
 * emulator without a window has no touch device bound to its display, so the swipes go to the
 * filter directly, the one path the platform offers for that. It runs on an emulator only: the
 * gestures are real input and the clicks act on live windows. Arguments, all optional: `launch`
 * (default true) starts MainActivity first; `settle` waits that many milliseconds after the launch;
 * `route` clicks nodes by their text in order, split on `|`; `wait` sleeps that long before the
 * first swipe so the host can open a dialog or a tile; `start` puts accessibility focus on the node
 * with that text before any gesture; `prev` sends that many previous-item gestures first; `swipes`
 * is how many next-item gestures to send (default 40). A route or start label that matches nothing
 * fails the run, since a transcript of the wrong screen would read as evidence.
 *
 * Every gesture is logged as `TalkBackReader: swipe <n>` so the host can pair it with TalkBack's
 * `Speaking fragment` lines that follow it.
 */
@RunWith(AndroidJUnit4::class)
class TalkBackReader {

  @Test
  fun read() {
    assumeTrue(
      "the reader drives live windows, so it runs on an emulator only",
      Build.FINGERPRINT.contains("generic") || Build.PRODUCT.startsWith("sdk_"),
    )
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val args = InstrumentationRegistry.getArguments()
    val automation: UiAutomation =
      instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    // The window list is empty unless the automation asks for interactive windows.
    automation.serviceInfo =
      automation.serviceInfo.apply {
        flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      }
    val context = instrumentation.targetContext
    packageName = context.packageName
    val metrics = context.resources.displayMetrics
    swipeY = metrics.heightPixels / 2f
    swipeStartX = metrics.widthPixels / 4f
    swipeEndX = metrics.widthPixels * 3 / 4f
    if (args.getString("launch", "true") == "true") {
      context.startActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
    SystemClock.sleep(number(args.getString("settle"), "settle", 5000, MAX_WAIT_MS).toLong())
    args.getString("route")?.split("|")?.forEach { label ->
      click(automation, label.trim())
      SystemClock.sleep(2500)
    }
    SystemClock.sleep(number(args.getString("wait"), "wait", 0, MAX_WAIT_MS).toLong())
    // TalkBack's own initial focus lands where it likes, so a named start makes a run repeatable.
    args.getString("start")?.let { label ->
      val node = roots(automation).firstNotNullOfOrNull { find(it, label.trim(), 0) }
      assertNotNull("start: no node reads $label", node)
      Log.i(TAG, "start: focus $label")
      node?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
      SystemClock.sleep(2000)
    }
    val prev = number(args.getString("prev"), "prev", 0, MAX_SWIPES)
    for (i in 1..prev) {
      Log.i(TAG, "swipe back $i")
      swipe(automation, toEnd = false)
      SystemClock.sleep(1800)
    }
    val swipes = number(args.getString("swipes"), "swipes", 40, MAX_SWIPES)
    for (i in 1..swipes) {
      Log.i(TAG, "swipe $i")
      swipe(automation, toEnd = true)
      SystemClock.sleep(1800)
    }
    Log.i(TAG, "done")
  }

  /** An argument as a count or a delay, refused with its name when it is not one in range. */
  private fun number(raw: String?, name: String, default: Int, max: Int): Int {
    if (raw == null) return default
    val value = raw.toIntOrNull()
    if (value == null || value < 0 || value > max) {
      fail("$name must be a whole number from 0 to $max, not $raw")
    }
    return value ?: default
  }

  private var packageName: String = ""
  private var swipeY: Float = 0f
  private var swipeStartX: Float = 0f
  private var swipeEndX: Float = 0f

  /** A quick horizontal swipe, which TalkBack reads as "move to the next (or previous) item". */
  private fun swipe(automation: UiAutomation, toEnd: Boolean) {
    val y = swipeY
    val startX = if (toEnd) swipeStartX else swipeEndX
    val endX = if (toEnd) swipeEndX else swipeStartX
    val steps = 8
    val downTime = SystemClock.uptimeMillis()
    inject(
      automation,
      MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, y, 0),
    )
    for (step in 1..steps) {
      SystemClock.sleep(12)
      val x = startX + (endX - startX) * step / steps
      inject(
        automation,
        MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0),
      )
    }
    SystemClock.sleep(12)
    inject(
      automation,
      MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, endX, y, 0),
    )
  }

  // The platform hides this method behind its test API, so the emulator runs with the hidden API
  // policy opened (settings put global hidden_api_policy 1) and the call goes through reflection.
  private val injectToFilter: Method by lazy {
    try {
      UiAutomation::class.java.getMethod("injectInputEventToInputFilter", InputEvent::class.java)
    } catch (e: NoSuchMethodException) {
      throw IllegalStateException(
        "injectInputEventToInputFilter is hidden; run adb shell settings put global hidden_api_policy 1",
        e,
      )
    }
  }

  private fun inject(automation: UiAutomation, event: MotionEvent) {
    event.source = InputDevice.SOURCE_TOUCHSCREEN
    injectToFilter.invoke(automation, event)
    event.recycle()
  }

  /**
   * The roots of this app's windows: the activity, an overlay, and any dialog the service hosts.
   * Other apps' windows stay out, so a route label can never click into one of them.
   */
  private fun roots(automation: UiAutomation): List<AccessibilityNodeInfo> {
    val roots = automation.windows.mapNotNull { it.root }.filter { it.packageName == packageName }
    return roots.ifEmpty {
      listOfNotNull(automation.rootInActiveWindow?.takeIf { it.packageName == packageName })
    }
  }

  /** Compose's node provider answers no text query, so the tree is walked for the label. */
  private fun find(node: AccessibilityNodeInfo, label: String, depth: Int): AccessibilityNodeInfo? {
    if (depth > MAX_DEPTH) return null
    if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
    for (i in 0 until node.childCount) {
      node.getChild(i)?.let { child ->
        find(child, label, depth + 1)?.let {
          return it
        }
      }
    }
    return null
  }

  private fun click(automation: UiAutomation, label: String) {
    var match: AccessibilityNodeInfo? = null
    // A row below the fold of a lazy list has no node until the list scrolls to it.
    for (attempt in 0..6) {
      match = roots(automation).firstNotNullOfOrNull { find(it, label, 0) }
      if (match != null) break
      val scrollable = roots(automation).firstNotNullOfOrNull { firstScrollable(it, 0) } ?: break
      scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
      SystemClock.sleep(1500)
    }
    assertNotNull("route: no node reads $label", match)
    var target: AccessibilityNodeInfo? = match
    while (target != null && !target.isClickable) target = target.parent
    assertNotNull("route: nothing clickable holds $label", target)
    Log.i(TAG, "route: click $label")
    target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  }

  private fun firstScrollable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
    if (depth > MAX_DEPTH) return null
    if (node.isScrollable) return node
    for (i in 0 until node.childCount) {
      node.getChild(i)?.let { child ->
        firstScrollable(child, depth + 1)?.let {
          return it
        }
      }
    }
    return null
  }

  private companion object {
    const val TAG: String = "TalkBackReader"
    const val MAX_SWIPES: Int = 200
    const val MAX_WAIT_MS: Int = 60_000
    const val MAX_DEPTH: Int = 100
  }
}
