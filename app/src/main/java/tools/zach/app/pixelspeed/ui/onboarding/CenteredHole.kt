package tools.zach.app.pixelspeed.ui.onboarding

import kotlin.math.abs
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.overlay.OverlayPlacement
import tools.zach.app.pixelspeed.overlay.RectDp

/** A cutout within this of the top edge counts as a status bar hole. */
private const val TOP_INSET_DP: Int = 16

/** A cutout centered within this fraction of the display width counts as the status bar's own. */
private const val CENTER_TOLERANCE_FRACTION: Double = 0.20

/**
 * The status bar's own camera hole among [cutoutRects] on a [widthDp] wide display: a rect whose
 * top sits within [TOP_INSET_DP] of the top edge and whose center falls within
 * [CENTER_TOLERANCE_FRACTION] of the display width from the display's own center. Null when none
 * qualifies, the rect nearest that center when more than one does.
 */
internal fun centeredTopHole(cutoutRects: List<RectDp>, widthDp: Int): RectDp? =
  cutoutRects
    .filter { it.left < it.right && it.top < it.bottom && it.top <= TOP_INSET_DP }
    .filter {
      abs((it.left + it.right) / 2.0 - widthDp / 2.0) <= widthDp * CENTER_TOLERANCE_FRACTION
    }
    .minByOrNull { abs((it.left + it.right) / 2.0 - widthDp / 2.0) }

/** Whether [cutoutRects] holds a centered top hole, the side flip step's whole reason to show. */
internal fun hasCenteredTopHole(cutoutRects: List<RectDp>, widthDp: Int): Boolean =
  centeredTopHole(cutoutRects, widthDp) != null

/**
 * A measured bar whose icons run up to [geometry]'s centered hole with no room left before it, the
 * shape a busier bar gives the placement rule once it has read that many icons. The span ends where
 * the drawn end icons begin, [endIconsDp] before the bar's end inset, so the readout keeps clear of
 * them the way it keeps clear of a real bar's. Null without a centered hole to push past.
 */
internal fun measuredBarPastHole(geometry: DisplayGeometry, endIconsDp: Int): MeasuredBar? =
  centeredTopHole(geometry.cutoutRects, geometry.widthDp)?.let { hole ->
    MeasuredBar(
      startFreeDp = hole.right,
      endFreeDp = geometry.widthDp - OverlayPlacement.barEndInsetDp(geometry) - endIconsDp,
    )
  }
