package tools.zach.app.pixelspeed.overlay

import kotlin.math.roundToInt

/** One leaf node's bounds in the status bar window, in physical screen pixels. */
data class NodeBox(val leftPx: Int, val topPx: Int, val rightPx: Int, val bottomPx: Int)

/**
 * Reads the status bar's real free span from its own icons, for [DisplayGeometry.measuredBar]. The
 * accessibility service supplies every leaf node carrying a label, an icon or the clock, as a
 * [NodeBox] in physical screen pixels; this function touches no platform type, so it runs on the
 * JVM.
 */
object BarSpanReader {

  /**
   * The bar's free span around [centerPx], or null when [boxes] is empty (a bar with no icons is
   * not a measurement). A box counts only when it sits fully inside the bar's [barHeightPx]. Among
   * the boxes that count, one whose right edge is at or before the center belongs to the zone
   * before the center, one whose left edge is at or after the center to the zone after it, and one
   * straddling the center belongs to neither. The nearer edge of each zone converts to dp at
   * [density] (physical pixels per dp); an empty zone falls back to the bar's own edge, 0 or
   * [barWidthPx] in dp. [rtl] puts the layout's start edge at the bar's physical right, so the two
   * zones swap which output they feed and each edge mirrors across the bar's width, the way
   * [RectDp.mirrored] mirrors a rect.
   */
  fun read(
    boxes: List<NodeBox>,
    barWidthPx: Int,
    barHeightPx: Int,
    centerPx: Int,
    density: Float,
    rtl: Boolean,
  ): MeasuredBar? {
    if (!density.isFinite() || density <= 0f) return null
    val inBar = boxes.filter { it.topPx >= 0 && it.bottomPx <= barHeightPx }
    // A bar with no icon inside it is not a measurement, not an empty bar.
    if (inBar.isEmpty()) return null
    val beforeCenter = inBar.filter { it.rightPx <= centerPx }
    val afterCenter = inBar.filter { it.leftPx >= centerPx }
    val widthDp = toDp(barWidthPx, density)
    val beforeEdgeDp = beforeCenter.maxOfOrNull { toDp(it.rightPx, density) }
    val afterEdgeDp = afterCenter.minOfOrNull { toDp(it.leftPx, density) }
    val startFreeDp = if (!rtl) beforeEdgeDp ?: 0 else afterEdgeDp?.let { widthDp - it } ?: 0
    val endFreeDp =
      if (!rtl) afterEdgeDp ?: widthDp else beforeEdgeDp?.let { widthDp - it } ?: widthDp
    return MeasuredBar(startFreeDp, endFreeDp)
  }

  private fun toDp(px: Int, density: Float): Int = (px / density).roundToInt()
}
