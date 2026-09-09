package tools.zach.app.pixelspeed.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Axis-aligned rectangle in dp, with an inclusive left/top and exclusive right/bottom. For a cutout
 * rect, [glassTop] is where the missing glass begins inside it: the platform's bounding rect of a
 * round status bar hole reaches the top edge while the hole itself starts lower, and the strip
 * between the two is free to draw on. [glassLeft] and [glassRight] are the hole's own side edges
 * the same way, since the rect boxes the glass with a margin on each side, and null where no path
 * names them: a notch, a corner hole, or a display that reports no cutout path keeps the rect's own
 * edges and the wider gap that goes with an unknown margin.
 */
data class RectDp(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
  val glassTop: Int = top,
  val glassLeft: Int? = null,
  val glassRight: Int? = null,
) {
  /**
   * The same rectangle seen from the opposite side edge of a [widthDp] wide display, held inside
   * it: edges rounded separately can land a pixel past the display.
   */
  fun mirrored(widthDp: Int): RectDp =
    RectDp(
      (widthDp - right).coerceIn(0, widthDp),
      top,
      (widthDp - left).coerceIn(0, widthDp),
      bottom,
      glassTop,
      glassRight?.let { (widthDp - it).coerceIn(0, widthDp) },
      glassLeft?.let { (widthDp - it).coerceIn(0, widthDp) },
    )

  /**
   * The edge a pill keeps before this hole: [HOLE_GAP_DP] off the glass, else [GAP_DP] off the
   * rect.
   */
  val clearBefore: Int
    get() = glassLeft?.let { it - OverlayPlacement.HOLE_GAP_DP } ?: (left - OverlayPlacement.GAP_DP)

  /** The edge a pill keeps after this hole, the same way. */
  val clearAfter: Int
    get() =
      glassRight?.let { it + OverlayPlacement.HOLE_GAP_DP } ?: (right + OverlayPlacement.GAP_DP)
}

/**
 * The display's size and insets, in dp, for one placement decision. Horizontal values run in the
 * layout direction: x = 0 is the start edge, where the status bar keeps its clock, so on an RTL
 * display the cutout rects arrive mirrored and the window applies x from the end edge. The bounds
 * hold only the whole dp that fit inside the display while every rect rounds to nearest, so a rect
 * that touches the far edge can end one dp past [widthDp] or [heightDp].
 */
data class DisplayGeometry(
  val widthDp: Int,
  val heightDp: Int,
  val statusBarDp: Int,
  val navigationBarDp: Int,
  val cutoutRects: List<RectDp>,
  /** Wider than tall; the hide-in-landscape rule reads this. */
  val landscape: Boolean,
  /**
   * The user's font scale; the status bar sizes its clock and icons in sp, so both grow with it.
   */
  val fontScale: Float = 1f,
  /**
   * The extra padding the status bar keeps from both side edges on a panel with large rounded
   * corners, the framework's rounded_corner_content_padding; zero on most phones.
   */
  val cornerPaddingDp: Int = 0,
  /**
   * Turned a quarter turn from the display's natural orientation. A phone or an open foldable
   * turned to landscape has a short bar with its hole on a side, so the bar anchors fall back to
   * the top corners; a tablet keeps them in both orientations, since its bar stays clear.
   */
  val rotated: Boolean = false,
  /**
   * The layout runs right to left: the cutout rects arrive mirrored, and the status bar keeps its
   * clock farther from its edge than it does in the other direction.
   */
  val rtl: Boolean = false,
  /**
   * The status bar's real free span around its center, read from the bar's own icons when the side
   * flip is on; null leaves the placement to its clock and icon estimates.
   */
  val measuredBar: MeasuredBar? = null,
)

/**
 * The free span the status bar's icons leave around its center, in dp from the start edge in the
 * layout direction: [startFreeDp] is where the last icon before the center ends, [endFreeDp] where
 * the first icon after it begins.
 */
data class MeasuredBar(val startFreeDp: Int, val endFreeDp: Int)

/** The user's chosen anchor, look and offsets for one overlay window. */
data class PlacementRequest(
  val anchor: Anchor,
  val landscapeAnchorMode: LandscapeAnchorMode,
  val landscapeAnchor: Anchor,
  val style: OverlayStyle,
  val statusBarFit: StatusBarFit,
  val offsetXdp: Int,
  val offsetYdp: Int,
  val customXdp: Int,
  val customYdp: Int,
  val avoidCutout: Boolean,
  /** A promoted notification's chip sits after the clock, where the download flank would go. */
  val promotedChip: Boolean,
  /**
   * Under AUTO, the slim ribbon stands in when neither side of a centered hole holds a look. Off,
   * the roomier side keeps the narrowest look and may overlap the nearest icon by a few dp.
   */
  val ribbonFallback: Boolean = false,
)

/** A measured size in whole dp. */
data class SizeDp(val width: Int, val height: Int)

/** Measured size of every look the renderer can draw, supplied by the renderer after measuring. */
data class LookSizes(val sizes: Map<OverlayStyle, SizeDp>)

/** The edge of its slot a look is anchored to, which the live content hugs inside the slot. */
enum class SlotAlignment {
  START,
  CENTER,
  END,
}

sealed interface Placement {
  /** Whether the look stays inside the free span and the bar; the Home card reports a false. */
  val fits: Boolean

  /** One window at (x, y) with the given size, measured on the widest strings the look can show. */
  data class Single(
    val x: Int,
    val y: Int,
    val size: SizeDp,
    val style: OverlayStyle,
    override val fits: Boolean,
    val alignment: SlotAlignment = SlotAlignment.CENTER,
  ) : Placement

  /**
   * Two halves flanking a centered cutout: download left of the hole, upload right of it. Only
   * returned when both halves clear the clock and the status icons; otherwise the cutout anchor
   * falls back to a [Single].
   */
  data class Flank(
    val download: RectDp,
    val upload: RectDp,
    val style: OverlayStyle,
    override val fits: Boolean,
  ) : Placement

  /** The slim ribbon, centered above the cutout or the bar. Not one of the nine looks. */
  data class Ribbon(val x: Int, val y: Int, val size: SizeDp, override val fits: Boolean) :
    Placement
}

/**
 * Decides where an overlay window sits and which look it draws, from a display's geometry and the
 * user's placement choices. Every value is in dp; the window manager layer converts to pixels.
 */
object OverlayPlacement {

  /** The status bar's own side padding before its clock and after its icons on a phone. */
  private const val MARGIN_DP = 16

  /** Measured width of the default clock at 14 sp and font scale 1. */
  private const val CLOCK_WIDTH_DP = 44

  /** Measured width of the status icons and privacy indicator zone at font scale 1. */
  private const val PRIVACY_INDICATOR_WIDTH_DP = 82

  /**
   * A side inset takes at most this fraction of the width: the platform's rects are not trusted.
   */
  private const val MAX_INSET_DIVISOR = 3

  /**
   * How much farther from its edge the status bar keeps its clock in a right-to-left layout,
   * measured on the Pixel 9 Pro: the privacy dot's padding stays at the physical right.
   */
  private const val RTL_CLOCK_EXTRA_DP = 24

  /**
   * Gap kept between the pill and the clock, the privacy indicator, or a cutout's rect, where those
   * edges are estimates.
   */
  internal const val GAP_DP = 8

  /** Gap kept between the pill and a measured clock or icon edge: the reading is exact. */
  internal const val MEASURED_GAP_DP = 2

  /** Gap kept between the pill and the glass of a centered hole whose path is known. */
  internal const val HOLE_GAP_DP = 4

  /** A display whose shorter side is at least this keeps its status bar anchors when rotated. */
  private const val WIDE_PANEL_DP: Int = 600

  /** True for a tablet or a fold's inner panel: the shorter side reaches [WIDE_PANEL_DP]. */
  val DisplayGeometry.isWidePanel: Boolean
    get() = minOf(widthDp, heightDp) >= WIDE_PANEL_DP

  /** Edge margin for the top and bottom anchors outside the status bar. */
  private const val EDGE_MARGIN_DP = 8

  /** A cutout centered within this fraction of the display width counts as a status bar hole. */
  private const val CENTER_TOLERANCE_FRACTION = 0.20

  /** The looks auto-fit tries in the status bar, widest first; the content measures each. */
  val AUTO_ORDER: List<OverlayStyle> =
    listOf(
      OverlayStyle.COMPACT,
      OverlayStyle.COMPACT12,
      OverlayStyle.STACK,
      OverlayStyle.STACK_NO_UNIT,
    )

  /** The ribbon's fixed distance from the display's top edge. */
  private const val RIBBON_TOP_MARGIN_DP = 2

  /** A cutout whose bounding rect starts within this of the top edge is the camera hole. */
  const val TOP_CUTOUT_INSET_DP: Int = 16

  /**
   * The status bar hole, if any: some panels inset the hole a few dp from the top edge, and the
   * platform lists cutout rects in no fixed order, so the qualifying rect nearest the horizontal
   * center wins.
   */
  private fun topCutout(geometry: DisplayGeometry): RectDp? =
    topCutouts(geometry).minByOrNull { abs((it.left + it.right) / 2.0 - geometry.widthDp / 2.0) }

  /** Every non-empty cutout rect that starts within [TOP_CUTOUT_INSET_DP] of the top edge. */
  private fun topCutouts(geometry: DisplayGeometry): List<RectDp> =
    geometry.cutoutRects.filter {
      it.left < it.right && it.top < it.bottom && it.top <= TOP_CUTOUT_INSET_DP
    }

  /**
   * Where the readout goes on [rawGeometry] for [request]. [looks] holds the measured size of every
   * single-pill look, [flankLooks] the size of one flank half per look, and [ribbonSize] the slim
   * ribbon's size. A status bar anchor beside a centered hole flanks it when both halves fit, else
   * tries one pill on the roomier side, then the other side, and, with neither holding a look under
   * [StatusBarFit.AUTO] and a ribbon of real height, falls back to the slim ribbon; a manual look
   * keeps the roomier side instead, so it never changes shape on its own. Every other anchor places
   * its own look, and the request's offsets and clamps apply last. A non-finite or non-positive
   * font scale is treated as 1.
   */
  fun place(
    request: PlacementRequest,
    rawGeometry: DisplayGeometry,
    looks: LookSizes,
    flankLooks: LookSizes,
    ribbonSize: SizeDp = SizeDp(0, 0),
  ): Placement {
    // A zero or non-finite scale would erase the clock and icon reservations or throw in rounding.
    val geometry =
      if (rawGeometry.fontScale.isFinite() && rawGeometry.fontScale > 0f) rawGeometry
      else rawGeometry.copy(fontScale = 1f)
    val anchor = resolveAnchor(request, geometry)
    if (anchor.inStatusBar && request.statusBarFit == StatusBarFit.SLIM) {
      return applyOffsetAndClamp(placeRibbon(geometry, ribbonSize), request, geometry)
    }
    val topCutout = topCutout(geometry)
    val activeCutout = topCutout.takeIf { request.avoidCutout }
    val centeredCutout = activeCutout?.takeIf { isCentered(it, geometry.widthDp) }

    val placement =
      if (!anchor.inStatusBar) {
        placeFree(anchor, request, geometry, looks)
      } else {
        val flank =
          if (anchor == Anchor.STATUS_BAR_CUTOUT && centeredCutout != null && !request.promotedChip)
            placeFlank(centeredCutout, geometry, flankLooks)
          else null
        when {
          flank?.fits == true -> flank
          anchor == Anchor.STATUS_BAR_CUTOUT && centeredCutout != null -> {
            // One pill beside an avoided hole: after it when a promoted chip holds the space
            // after the clock, else on whichever side has more room. The hole stays uncovered.
            val roomBefore = centeredCutout.clearBefore - (clockRight(geometry) + barGap(geometry))
            val roomAfter = endWithIndicator(geometry) - centeredCutout.clearAfter
            val nearSide =
              if (!request.promotedChip && roomBefore >= roomAfter) Anchor.STATUS_BAR_BEFORE_CUTOUT
              else Anchor.STATUS_BAR_AFTER_CUTOUT
            val farSide =
              if (nearSide == Anchor.STATUS_BAR_BEFORE_CUTOUT) Anchor.STATUS_BAR_AFTER_CUTOUT
              else Anchor.STATUS_BAR_BEFORE_CUTOUT
            val near =
              placeStatusBarSingle(nearSide, request, geometry, looks, activeCutout, centeredCutout)
            val far by lazy {
              placeStatusBarSingle(farSide, request, geometry, looks, activeCutout, centeredCutout)
            }
            // A promoted chip holds the space after the clock, so the far side never falls back
            // before the hole while one is up.
            val farAllowed = !(request.promotedChip && farSide == Anchor.STATUS_BAR_BEFORE_CUTOUT)
            when {
              near.fits -> near
              farAllowed && far.fits -> far
              // Neither side holds the look: AUTO gives way to the ribbon only when asked to and
              // when the ribbon has a real height to place, so a look never disappears into a
              // zero-size fallback; otherwise the roomier side keeps the narrowest look, and
              // MANUAL keeps it so a manual look never changes shape on its own.
              request.statusBarFit == StatusBarFit.AUTO &&
                request.ribbonFallback &&
                ribbonSize.height > 0 -> placeRibbon(geometry, ribbonSize)
              else -> near
            }
          }
          else ->
            placeStatusBarSingle(anchor, request, geometry, looks, activeCutout, centeredCutout)
        }
      }

    return applyOffsetAndClamp(placement, request, geometry)
  }

  private fun resolveAnchor(request: PlacementRequest, geometry: DisplayGeometry): Anchor {
    if (!geometry.landscape) return request.anchor
    return when (request.landscapeAnchorMode) {
      LandscapeAnchorMode.SEPARATE -> request.landscapeAnchor
      // Only a rotated display has the short bar the corner fallback exists for, and a panel whose
      // shorter side is tablet-wide keeps a bar with room whichever way it turns.
      LandscapeAnchorMode.SAME ->
        if (!geometry.rotated || geometry.isWidePanel) request.anchor
        else
          when (request.anchor) {
            Anchor.STATUS_BAR_START,
            Anchor.STATUS_BAR_BEFORE_CUTOUT -> Anchor.TOP_START
            Anchor.STATUS_BAR_CUTOUT,
            Anchor.STATUS_BAR_AFTER_CUTOUT,
            Anchor.STATUS_BAR_END -> Anchor.TOP_END
            else -> request.anchor
          }
    }
  }

  internal fun isCentered(rect: RectDp, widthDp: Int): Boolean {
    val cutoutCenter = (rect.left + rect.right) / 2.0
    val displayCenter = widthDp / 2.0
    return abs(cutoutCenter - displayCenter) <= widthDp * CENTER_TOLERANCE_FRACTION
  }

  /** Picks the look and whether it fits the free span, per the AUTO or MANUAL status bar fit. */
  private fun effectiveLook(
    fit: StatusBarFit,
    requestedStyle: OverlayStyle,
    freeSpanWidth: Int,
    statusBarDp: Int,
    looks: LookSizes,
  ): Pair<OverlayStyle, Boolean> {
    // A look fits when it spans no wider than the free span and no taller than the bar itself;
    // a hidden bar (zero height) constrains only the width.
    fun fits(style: OverlayStyle): Boolean {
      val size = looks.sizes.getValue(style)
      return size.width <= freeSpanWidth && (statusBarDp <= 0 || size.height <= statusBarDp)
    }
    return when (fit) {
      StatusBarFit.MANUAL -> requestedStyle to fits(requestedStyle)
      StatusBarFit.AUTO -> {
        val fitting = AUTO_ORDER.firstOrNull(::fits)
        if (fitting != null) {
          fitting to true
        } else {
          // Nothing fits: a look too wide covers the status icons while one too tall only hangs
          // past the bar's edge, so the widest look inside the span wins, else the narrowest.
          val inSpan = AUTO_ORDER.firstOrNull { looks.sizes.getValue(it).width <= freeSpanWidth }
          (inSpan ?: AUTO_ORDER.last()) to false
        }
      }
      StatusBarFit.SLIM ->
        error("SLIM renders the ribbon in place(), never reaching a status bar single placement")
    }
  }

  /**
   * Centers the ribbon on a centered top hole, ignoring [PlacementRequest.avoidCutout], else on the
   * bar, then moves it into the free span of the bar nearest that spot when a hole's glass reaches
   * into its strip. A ribbon no span holds stays put with [Placement.fits] false; Home's fit hint
   * is silent under the Slim fit, so that flag is read by tests and the catalog, not shown. The
   * request's nudge applies afterwards and can move the ribbon back over a hole.
   */
  private fun placeRibbon(geometry: DisplayGeometry, size: SizeDp): Placement.Ribbon {
    val top = RIBBON_TOP_MARGIN_DP
    val bottom = top + size.height
    val centered = topCutout(geometry)?.takeIf { isCentered(it, geometry.widthDp) }
    val preferred =
      if (centered != null) {
        (centered.left + centered.right - size.width) / 2
      } else {
        (geometry.widthDp - size.width) / 2
      }
    // A ribbon taller than the bar hangs into the app below it.
    val inBar = geometry.statusBarDp <= 0 || bottom <= geometry.statusBarDp
    // Only a hole whose glass reaches into the strip blocks it; a round hole under the strip
    // does not. Overlapping blockers merge, so the spans between them are the bar's real gaps.
    val blockers =
      topCutouts(geometry)
        .filter { it.glassTop < bottom && it.bottom > top }
        .sortedBy { it.left }
        .fold(mutableListOf<RectDp>()) { runs, hole ->
          val last = runs.lastOrNull()
          if (last != null && hole.left <= last.right) {
            runs[runs.lastIndex] = last.copy(right = maxOf(last.right, hole.right))
          } else {
            runs += hole
          }
          runs
        }
    fun clear(x: Int): Boolean = blockers.none { x < it.right && x + size.width > it.left }
    if (clear(preferred)) {
      return Placement.Ribbon(preferred, top, size, fits = size.width <= geometry.widthDp && inBar)
    }
    // The free spans of the bar's content area, a gap in from each blocker; the span that holds
    // the ribbon nearest its preferred spot wins, and none holding it leaves the ribbon put.
    val edges =
      listOf(barStartInsetDp(geometry)) +
        blockers.flatMap { listOf(it.left - GAP_DP, it.right + GAP_DP) } +
        listOf(geometry.widthDp - barEndInsetDp(geometry))
    val candidates =
      edges.chunked(2).mapNotNull { (start, end) ->
        if (end - start < size.width) null else preferred.coerceIn(start, end - size.width)
      }
    val x = candidates.filter(::clear).minByOrNull { abs(it - preferred) }
    return if (x != null) Placement.Ribbon(x, top, size, fits = inBar)
    else Placement.Ribbon(preferred, top, size, fits = false)
  }

  /**
   * Centers on the given cutout's vertical span, or on the bar's own center without one. A look
   * taller than the bar hangs from the top edge rather than above it.
   */
  private fun statusBarY(topCutout: RectDp?, statusBarDp: Int, height: Int): Int {
    val centered =
      if (topCutout != null) {
        (topCutout.top + topCutout.bottom - height) / 2
      } else {
        (statusBarDp - height) / 2
      }
    return centered.coerceAtLeast(0)
  }

  /**
   * A hole in a top corner: a top cutout the status bar keeps its content clear of by starting
   * after it (start corner) or ending before it (end corner).
   */
  private fun cornerCutout(geometry: DisplayGeometry, start: Boolean): RectDp? {
    // Every top hole off the centered band sits on one side of the bar's own content, so the
    // content starts after it; the platform lists rects in no fixed order, so the rect
    // reaching farthest into the bar wins.
    val sideHoles =
      geometry.cutoutRects
        .filter { it.left < it.right && it.top < it.bottom && it.top <= TOP_CUTOUT_INSET_DP }
        .filter { !isCentered(it, geometry.widthDp) }
        .filter { ((it.left + it.right) / 2.0 < geometry.widthDp / 2.0) == start }
    return if (start) sideHoles.maxByOrNull { it.right } else sideHoles.minByOrNull { it.left }
  }

  /** Where the status bar's own content starts: after the corner padding or a corner hole. */
  fun barStartInsetDp(geometry: DisplayGeometry): Int =
    maxOf(
        MARGIN_DP + geometry.cornerPaddingDp + (if (geometry.rtl) RTL_CLOCK_EXTRA_DP else 0),
        cornerCutout(geometry, start = true)?.let { it.right + GAP_DP } ?: 0,
      )
      .coerceIn(0, geometry.widthDp / MAX_INSET_DIVISOR)

  /** How far from the end edge the status bar's own content ends. */
  fun barEndInsetDp(geometry: DisplayGeometry): Int =
    maxOf(
        MARGIN_DP + geometry.cornerPaddingDp,
        cornerCutout(geometry, start = false)?.let { geometry.widthDp - it.left + GAP_DP } ?: 0,
      )
      .coerceIn(0, geometry.widthDp / MAX_INSET_DIVISOR)

  /**
   * The gap a pill keeps from the clock's end: [MEASURED_GAP_DP] off a reading that lies inside the
   * bar, else [GAP_DP], since a reading the clamp overrides is an estimate again. An overflowing
   * span (a pill that cannot fit and ends at the hole) starts flush with the clock instead.
   */
  private fun barGap(geometry: DisplayGeometry): Int =
    if (readingInsideBar(geometry.measuredBar?.startFreeDp, geometry)) MEASURED_GAP_DP else GAP_DP

  private fun readingInsideBar(reading: Int?, geometry: DisplayGeometry): Boolean =
    reading != null &&
      reading in barStartInsetDp(geometry)..(geometry.widthDp - barEndInsetDp(geometry))

  /**
   * Where the clock ends: the bar's own measured free span when [DisplayGeometry.measuredBar] is
   * read, else the clock's estimated width at the display's font scale. Clamped into the bar's own
   * content area so a bad reading never reports a span past the clock's or the icons' edge.
   */
  internal fun clockRight(geometry: DisplayGeometry): Int {
    val estimate = barStartInsetDp(geometry) + (CLOCK_WIDTH_DP * geometry.fontScale).roundToInt()
    val measured = geometry.measuredBar?.startFreeDp ?: estimate
    return measured.coerceIn(barStartInsetDp(geometry), geometry.widthDp - barEndInsetDp(geometry))
  }

  /**
   * Where the status icons and privacy indicator begin: the bar's own measured free span when
   * [DisplayGeometry.measuredBar] is read, else the indicator's estimated width at the display's
   * font scale. Clamped into the bar's own content area so a bad reading never reports a span past
   * the clock's or the icons' edge.
   */
  internal fun endWithIndicator(geometry: DisplayGeometry): Int {
    val estimate =
      geometry.widthDp -
        barEndInsetDp(geometry) -
        (PRIVACY_INDICATOR_WIDTH_DP * geometry.fontScale).roundToInt() -
        GAP_DP
    val start = barStartInsetDp(geometry)
    val end = geometry.widthDp - barEndInsetDp(geometry)
    // A reading past the bar's end names no icon to clear, so the span runs to the end itself.
    val measured =
      geometry.measuredBar?.let {
        when {
          it.endFreeDp >= end -> end
          it.endFreeDp < start -> start
          else -> it.endFreeDp - MEASURED_GAP_DP
        }
      }
    return (measured ?: estimate).coerceIn(start, end)
  }

  private fun placeStatusBarSingle(
    anchor: Anchor,
    request: PlacementRequest,
    geometry: DisplayGeometry,
    looks: LookSizes,
    activeCutout: RectDp?,
    centeredCutout: RectDp?,
  ): Placement.Single {
    val clockRight = clockRight(geometry)
    val startWithClock = clockRight + barGap(geometry)
    val endWithIndicator = endWithIndicator(geometry)

    // Triple of (free span start, free span end, true when the pill aligns to the span's start).
    // A pill centered on the display reaches equally far both ways, so its span is twice the
    // tighter side's room.
    val (freeSpanStart, freeSpanEnd, alignStart) =
      when (anchor) {
        Anchor.STATUS_BAR_CUTOUT -> {
          val center = geometry.widthDp / 2
          val room = minOf(center - startWithClock, endWithIndicator - center)
          Triple(center - room, center + room, true)
        }
        Anchor.STATUS_BAR_START -> {
          val end = centeredCutout?.clearBefore ?: endWithIndicator
          Triple(startWithClock, end, true)
        }
        Anchor.STATUS_BAR_BEFORE_CUTOUT -> {
          if (centeredCutout != null) {
            Triple(startWithClock, centeredCutout.clearBefore, false)
          } else {
            Triple(clockRight, endWithIndicator, false)
          }
        }
        Anchor.STATUS_BAR_AFTER_CUTOUT -> {
          val start = centeredCutout?.clearAfter ?: startWithClock
          Triple(start, endWithIndicator, true)
        }
        Anchor.STATUS_BAR_END -> {
          val start = centeredCutout?.clearAfter ?: clockRight
          Triple(start, endWithIndicator, false)
        }
        else -> error("$anchor is not a status bar anchor")
      }

    val freeSpanWidth = freeSpanEnd - freeSpanStart
    val centered =
      effectiveLook(request.statusBarFit, request.style, freeSpanWidth, geometry.statusBarDp, looks)
    // A centered pill that breaks the tighter side may still fit between the clock and the icons
    // once it slides off center, so that span gets a second try before anything is declared unfit.
    val (style, lookFits) =
      if (anchor == Anchor.STATUS_BAR_CUTOUT && !centered.second) {
        effectiveLook(
          request.statusBarFit,
          request.style,
          endWithIndicator - startWithClock,
          geometry.statusBarDp,
          looks,
        )
      } else {
        centered
      }
    val size = looks.sizes.getValue(style)

    // A pill wider than its span overruns toward the clock or the icons, never toward an avoided
    // hole: a span that ends at the hole keeps its end, one that starts there keeps its start.
    val overflows = size.width > freeSpanWidth
    val holeEndsSpan =
      centeredCutout != null &&
        (anchor == Anchor.STATUS_BAR_START || anchor == Anchor.STATUS_BAR_BEFORE_CUTOUT)
    val x =
      when {
        anchor == Anchor.STATUS_BAR_CUTOUT ->
          ((geometry.widthDp - size.width) / 2).clampToSpan(
            startWithClock,
            endWithIndicator - size.width,
          )
        overflows -> if (holeEndsSpan) freeSpanEnd - size.width else freeSpanStart
        alignStart -> freeSpanStart
        else -> freeSpanEnd - size.width
      }
    val y = statusBarY(activeCutout, geometry.statusBarDp, size.height)
    val alignment =
      when {
        anchor == Anchor.STATUS_BAR_CUTOUT -> SlotAlignment.CENTER
        overflows -> if (holeEndsSpan) SlotAlignment.END else SlotAlignment.START
        alignStart -> SlotAlignment.START
        else -> SlotAlignment.END
      }
    return Placement.Single(x, y, size, style, lookFits, alignment)
  }

  private fun placeFlank(
    cutout: RectDp,
    geometry: DisplayGeometry,
    flankLooks: LookSizes,
  ): Placement.Flank {
    val size = flankLooks.sizes.getValue(OverlayStyle.COMPACT12)
    val top = (cutout.top + cutout.bottom - size.height) / 2
    val bottom = top + size.height
    val downloadRight = cutout.clearBefore
    val download = RectDp(downloadRight - size.width, top, downloadRight, bottom)
    val uploadLeft = cutout.clearAfter
    val upload = RectDp(uploadLeft, top, uploadLeft + size.width, bottom)
    val fits =
      download.left >= clockRight(geometry) + barGap(geometry) &&
        upload.right <= endWithIndicator(geometry)
    return Placement.Flank(download, upload, OverlayStyle.COMPACT12, fits)
  }

  private fun placeFree(
    anchor: Anchor,
    request: PlacementRequest,
    geometry: DisplayGeometry,
    looks: LookSizes,
  ): Placement.Single {
    val size = looks.sizes.getValue(request.style)
    return when (anchor) {
      Anchor.TOP_START ->
        Placement.Single(
          EDGE_MARGIN_DP,
          geometry.statusBarDp + EDGE_MARGIN_DP,
          size,
          request.style,
          fits = true,
          alignment = SlotAlignment.START,
        )
      Anchor.TOP_END ->
        Placement.Single(
          geometry.widthDp - EDGE_MARGIN_DP - size.width,
          geometry.statusBarDp + EDGE_MARGIN_DP,
          size,
          request.style,
          fits = true,
          alignment = SlotAlignment.END,
        )
      Anchor.BOTTOM_START ->
        Placement.Single(
          EDGE_MARGIN_DP,
          geometry.heightDp - geometry.navigationBarDp - EDGE_MARGIN_DP - size.height,
          size,
          request.style,
          fits = true,
          alignment = SlotAlignment.START,
        )
      Anchor.BOTTOM_END ->
        Placement.Single(
          geometry.widthDp - EDGE_MARGIN_DP - size.width,
          geometry.heightDp - geometry.navigationBarDp - EDGE_MARGIN_DP - size.height,
          size,
          request.style,
          fits = true,
          alignment = SlotAlignment.END,
        )
      Anchor.CUSTOM -> {
        // A drop records the look's left edge, so the content keeps that edge.
        val x = request.customXdp.clampToSpan(0, geometry.widthDp - size.width)
        val y = request.customYdp.clampToSpan(0, geometry.heightDp - size.height)
        Placement.Single(x, y, size, request.style, fits = true, SlotAlignment.START)
      }
      else -> error("$anchor is not placed outside the status bar")
    }
  }

  private fun applyOffsetAndClamp(
    placement: Placement,
    request: PlacementRequest,
    geometry: DisplayGeometry,
  ): Placement {
    return when (placement) {
      is Placement.Single -> {
        val x =
          (placement.x + request.offsetXdp).clampToSpan(0, geometry.widthDp - placement.size.width)
        val y =
          (placement.y + request.offsetYdp).clampToSpan(
            0,
            geometry.heightDp - placement.size.height,
          )
        val fits =
          placement.fits &&
            placement.size.width <= geometry.widthDp &&
            placement.size.height <= geometry.heightDp
        placement.copy(x = x, y = y, fits = fits)
      }
      is Placement.Flank -> {
        // The halves move as one rigid pair, so an edge stops both and their gap survives.
        val pair =
          RectDp(
            placement.download.left,
            placement.download.top,
            placement.upload.right,
            placement.upload.bottom,
          )
        val shifted = shiftAndClamp(pair, request.offsetXdp, request.offsetYdp, geometry)
        val dx = shifted.left - pair.left
        val dy = shifted.top - pair.top
        val download = placement.download.shift(dx, dy)
        val upload = placement.upload.shift(dx, dy)
        val fits =
          placement.fits &&
            (download.right - download.left) <= geometry.widthDp &&
            (upload.right - upload.left) <= geometry.widthDp
        placement.copy(download = download, upload = upload, fits = fits)
      }
      is Placement.Ribbon -> {
        val x =
          (placement.x + request.offsetXdp).clampToSpan(0, geometry.widthDp - placement.size.width)
        val y =
          (placement.y + request.offsetYdp).clampToSpan(
            0,
            geometry.heightDp - placement.size.height,
          )
        val fits =
          placement.fits &&
            placement.size.width <= geometry.widthDp &&
            placement.size.height <= geometry.heightDp
        placement.copy(x = x, y = y, fits = fits)
      }
    }
  }

  private fun RectDp.shift(dx: Int, dy: Int): RectDp =
    copy(
      left = left + dx,
      top = top + dy,
      right = right + dx,
      bottom = bottom + dy,
      glassTop = glassTop + dy,
      glassLeft = glassLeft?.plus(dx),
      glassRight = glassRight?.plus(dx),
    )

  private fun shiftAndClamp(
    rect: RectDp,
    offsetXdp: Int,
    offsetYdp: Int,
    geometry: DisplayGeometry,
  ): RectDp {
    val width = rect.right - rect.left
    val height = rect.bottom - rect.top
    val left = (rect.left + offsetXdp).clampToSpan(0, geometry.widthDp - width)
    val top = (rect.top + offsetYdp).clampToSpan(0, geometry.heightDp - height)
    return RectDp(left, top, left + width, top + height)
  }

  /**
   * Clamps into `[min, max]`, tolerating a `max` below `min` (a look wider than the display) by
   * collapsing to `min` instead of throwing, the way [Int.coerceIn] would.
   */
  private fun Int.clampToSpan(min: Int, max: Int): Int {
    if (max < min) return min
    return coerceIn(min, max)
  }
}
