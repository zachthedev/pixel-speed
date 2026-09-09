package tools.zach.app.pixelspeed.service

import tools.zach.app.pixelspeed.overlay.NodeBox

/** One accessibility window's identity and bounds, independent of the platform type. */
internal data class WindowCandidate(
  val title: String?,
  val isSystemWindow: Boolean,
  val packageName: String?,
  val leftPx: Int,
  val topPx: Int,
  val rightPx: Int,
  val bottomPx: Int,
)

/**
 * Picks the status bar among the windows the accessibility service sees, without touching
 * `AccessibilityWindowInfo` directly, so the rule runs on the JVM.
 */
internal object StatusBarWindowPicker {
  private const val STATUS_BAR_TITLE: String = "StatusBar"
  const val SYSTEMUI_PACKAGE: String = "com.android.systemui"

  /**
   * The index into [windows] of the status bar window, or null when none matches. Only a System UI
   * window sitting at the top of the display, on it, and no taller than [statusBarHeightPx] is
   * considered. Any app can title a window it owns and an unreadable root proves nothing, so the
   * package is the gate: among the System UI candidates, the one titled [STATUS_BAR_TITLE] wins
   * (Pixel builds report it), else the first.
   */
  fun pickIndex(windows: List<WindowCandidate>, statusBarHeightPx: Int): Int? {
    val barShaped =
      windows.withIndex().filter { (_, w) ->
        w.isSystemWindow &&
          w.packageName == SYSTEMUI_PACKAGE &&
          w.topPx <= 0 &&
          w.bottomPx > 0 &&
          w.rightPx > w.leftPx &&
          (w.bottomPx - w.topPx) <= statusBarHeightPx
      }
    val byTitle = barShaped.firstOrNull { (_, w) -> w.title == STATUS_BAR_TITLE }
    return (byTitle ?: barShaped.firstOrNull())?.index
  }
}

/** One accessibility node, abstracted so the leaf walk runs on the JVM with no platform type. */
internal interface AccessibilityNodeAdapter {
  val childCount: Int

  fun childAt(index: Int): AccessibilityNodeAdapter?

  /**
   * True when the node carries a content description or text, the label a leaf reports itself by.
   */
  val hasLabel: Boolean

  val boundsInScreen: NodeBox
}

/**
 * Every leaf under [root] that carries a label, as its screen bounds. [onVisited] runs once per
 * node after its subtree is read, in postorder, so the caller can release a node it obtained
 * through the platform's child lookup. The walk stops [MAX_WALK_DEPTH] levels down and after
 * [MAX_WALK_NODES] nodes: a status bar is a few dozen nodes deep by a handful, and a tree past
 * those bounds is not one.
 */
internal fun collectLeafBoxes(
  root: AccessibilityNodeAdapter,
  onVisited: (AccessibilityNodeAdapter) -> Unit = {},
): List<NodeBox> {
  val budget = intArrayOf(MAX_WALK_NODES)
  return collectLeafBoxes(root, onVisited, depth = 0, budget = budget)
}

private fun collectLeafBoxes(
  root: AccessibilityNodeAdapter,
  onVisited: (AccessibilityNodeAdapter) -> Unit,
  depth: Int,
  budget: IntArray,
): List<NodeBox> {
  if (depth > MAX_WALK_DEPTH || budget[0] <= 0) return emptyList()
  budget[0] -= 1
  return if (root.childCount == 0) {
    if (root.hasLabel) listOf(root.boundsInScreen) else emptyList()
  } else {
    (0 until root.childCount).flatMap { index ->
      val child = root.childAt(index) ?: return@flatMap emptyList()
      collectLeafBoxes(child, onVisited, depth + 1, budget).also { onVisited(child) }
    }
  }
}

internal const val MAX_WALK_DEPTH: Int = 32
internal const val MAX_WALK_NODES: Int = 512
