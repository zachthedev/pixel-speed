package tools.zach.app.pixelspeed.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class OverlayPlacementTest {

  companion object {
    private val pixel11Pro =
      DisplayGeometry(
        widthDp = 427,
        heightDp = 952,
        statusBarDp = 68,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(196, 0, 230, 33)),
        landscape = false,
      )
    // 586 to 695 px on a 1280 px display at density 3 is 195 to 232 dp on a 427 dp display, the
    // rect running 0 to 52 dp; the hole's circle (center 102 px, radius 49) starts at 53 px, 18 dp.
    private val ribbonCutoutDisplay =
      DisplayGeometry(
        widthDp = 427,
        heightDp = 952,
        statusBarDp = 68,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(195, 0, 232, 52, glassTop = 18)),
        landscape = false,
      )
    // The same hole beside an end corner hole whose glass starts at the edge.
    private val centeredAndCornerHoles =
      ribbonCutoutDisplay.copy(
        cutoutRects = listOf(RectDp(195, 0, 232, 52, glassTop = 18), RectDp(390, 0, 427, 33))
      )
    private val ribbonSize = SizeDp(116, 14)
    private val pixel412 =
      DisplayGeometry(
        widthDp = 412,
        heightDp = 915,
        statusBarDp = 62,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(197, 0, 227, 30)),
        landscape = false,
      )
    private val cornerHole =
      DisplayGeometry(
        widthDp = 393,
        heightDp = 851,
        statusBarDp = 28,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(9, 0, 39, 30)),
        landscape = false,
      )
    private val noCutout =
      DisplayGeometry(
        widthDp = 411,
        heightDp = 731,
        statusBarDp = 24,
        navigationBarDp = 48,
        cutoutRects = emptyList(),
        landscape = false,
      )
    private val foldCover =
      DisplayGeometry(
        widthDp = 372,
        heightDp = 820,
        statusBarDp = 60,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(171, 0, 201, 30)),
        landscape = false,
      )
    // The 9 Pro Fold's cover rect, 8 dp below the edge, with no cutout path known: the glass line
    // falls back to the rect's top, inside the ribbon's strip.
    private val foldCoverInsetHole =
      DisplayGeometry(
        widthDp = 443,
        heightDp = 994,
        statusBarDp = 62,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(199, 8, 245, 62)),
        landscape = false,
      )
    private val foldInner =
      DisplayGeometry(
        widthDp = 840,
        heightDp = 900,
        statusBarDp = 24,
        navigationBarDp = 40,
        cutoutRects = emptyList(),
        landscape = false,
      )
    // The 9 Pro Fold's inner panel, measured on the AVD: 32 dp of corner padding and a 56 dp
    // hole in the top end corner.
    private val foldInnerCornerHole =
      DisplayGeometry(
        widthDp = 852,
        heightDp = 883,
        statusBarDp = 56,
        navigationBarDp = 40,
        cutoutRects = listOf(RectDp(796, 0, 852, 56)),
        landscape = false,
        cornerPaddingDp = 32,
      )
    // Two holes on the start side, listed inner first, plus a sliver on the start edge.
    private val twoStartHoles =
      cornerHole.copy(
        cutoutRects = listOf(RectDp(44, 0, 80, 30), RectDp(9, 0, 39, 30), RectDp(0, 0, 4, 60))
      )
    private val bothCorners =
      cornerHole.copy(cutoutRects = listOf(RectDp(354, 0, 384, 30), RectDp(9, 0, 39, 30)))
    private val paddedNoHole = noCutout.copy(cornerPaddingDp = 32)
    // A hole on the start side but well off the corner still pushes the clock past it.
    private val holeOffTheCorner = cornerHole.copy(cutoutRects = listOf(RectDp(80, 0, 110, 30)))
    private val tabletPortrait =
      DisplayGeometry(
        widthDp = 800,
        heightDp = 1280,
        statusBarDp = 24,
        navigationBarDp = 40,
        cutoutRects = emptyList(),
        landscape = false,
        rotated = true,
      )
    private val tablet =
      DisplayGeometry(
        widthDp = 1280,
        heightDp = 800,
        statusBarDp = 24,
        navigationBarDp = 40,
        cutoutRects = emptyList(),
        landscape = true,
      )
    /** A fold's inner panel turned to landscape: rotated, but tablet-wide on its shorter side. */
    private val foldInnerLandscape =
      DisplayGeometry(
        widthDp = 883,
        heightDp = 852,
        statusBarDp = 56,
        navigationBarDp = 40,
        cutoutRects = listOf(RectDp(827, 0, 883, 56)),
        landscape = true,
        rotated = true,
        cornerPaddingDp = 32,
      )
    private val pixel11ProLandscape =
      DisplayGeometry(
        widthDp = 952,
        heightDp = 427,
        statusBarDp = 24,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(0, 196, 33, 230)),
        landscape = true,
        rotated = true,
      )
    // A hole wide enough that neither the 14 dp room before it nor the negative room after it
    // holds even the narrowest look (62 dp), so both single-pill sides miss.
    private val bothSidesNarrow =
      DisplayGeometry(
        widthDp = 200,
        heightDp = 400,
        statusBarDp = 60,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(90, 0, 110, 30)),
        landscape = false,
      )

    private val looks =
      LookSizes(
        mapOf(
          OverlayStyle.CHIP to SizeDp(166, 24),
          OverlayStyle.COMPACT to SizeDp(128, 24),
          OverlayStyle.COMPACT12 to SizeDp(113, 24),
          OverlayStyle.STACK to SizeDp(100, 24),
          OverlayStyle.STACK_NO_UNIT to SizeDp(62, 24),
          OverlayStyle.SINGLE to SizeDp(120, 24),
          OverlayStyle.SINGLE_NO_UNIT to SizeDp(100, 24),
          OverlayStyle.HALO to SizeDp(100, 24),
          OverlayStyle.GLYPHS to SizeDp(100, 24),
        )
      )
    private val flankLooks = LookSizes(mapOf(OverlayStyle.COMPACT12 to SizeDp(67, 24)))

    private fun request(
      anchor: Anchor = Anchor.STATUS_BAR_CUTOUT,
      landscapeAnchorMode: LandscapeAnchorMode = LandscapeAnchorMode.SAME,
      landscapeAnchor: Anchor = Anchor.STATUS_BAR_CUTOUT,
      style: OverlayStyle = OverlayStyle.COMPACT,
      statusBarFit: StatusBarFit = StatusBarFit.AUTO,
      offsetXdp: Int = 0,
      offsetYdp: Int = 0,
      customXdp: Int = 0,
      customYdp: Int = 0,
      avoidCutout: Boolean = true,
      promotedChip: Boolean = false,
      ribbonFallback: Boolean = false,
    ): PlacementRequest =
      PlacementRequest(
        anchor = anchor,
        landscapeAnchorMode = landscapeAnchorMode,
        landscapeAnchor = landscapeAnchor,
        style = style,
        statusBarFit = statusBarFit,
        offsetXdp = offsetXdp,
        offsetYdp = offsetYdp,
        customXdp = customXdp,
        customYdp = customYdp,
        avoidCutout = avoidCutout,
        promotedChip = promotedChip,
        ribbonFallback = ribbonFallback,
      )

    @JvmStatic
    fun statusBarAnchorsByFontScale(): List<Array<Any>> =
      listOf(
          Anchor.STATUS_BAR_START,
          Anchor.STATUS_BAR_BEFORE_CUTOUT,
          Anchor.STATUS_BAR_AFTER_CUTOUT,
          Anchor.STATUS_BAR_END,
        )
        .flatMap { anchor ->
          listOf(1.0f, 1.3f, 1.5f, 2.0f).flatMap { scale ->
            listOf(StatusBarFit.AUTO, StatusBarFit.MANUAL).map { arrayOf<Any>(anchor, scale, it) }
          }
        }

    @JvmStatic fun statusBarAnchors(): List<Anchor> = Anchor.entries.filter { it.inStatusBar }

    // Geometry, where the bar's content starts, and how far from the end edge it stops.
    @JvmStatic
    fun barInsetCases(): List<Array<Any>> =
      listOf(
        arrayOf("phone", pixel11Pro, 16, 16),
        arrayOf("start corner hole", cornerHole, 47, 16),
        arrayOf("fold inner", foldInnerCornerHole, 48, 64),
        arrayOf("no cutout", noCutout, 16, 16),
        arrayOf("right to left", pixel11Pro.copy(rtl = true), 40, 16),
        arrayOf("two start holes and an edge sliver", twoStartHoles, 88, 16),
        arrayOf("both corners", bothCorners, 47, 47),
        arrayOf("corner padding without a hole", paddedNoHole, 48, 48),
        arrayOf("hole off the corner", holeOffTheCorner, 118, 16),
      )

    @JvmStatic
    fun holeOrders(): List<Array<Any>> =
      listOf(
        arrayOf("centered first", centeredAndCornerHoles.cutoutRects),
        arrayOf("corner first", centeredAndCornerHoles.cutoutRects.reversed()),
      )

    @JvmStatic
    fun cornerHoleDisplays(): List<Array<Any>> =
      listOf(
        arrayOf("start corner hole", cornerHole),
        arrayOf("fold inner panel's end corner hole", foldInnerCornerHole),
      )

    // widthDp, the expected AUTO style, and why: a STATUS_BAR_START span of that width with no
    // hole and no corner padding, where the free span is widthDp - 174.
    @JvmStatic
    fun autoOrderCases(): List<Array<Any>> =
      listOf(
        arrayOf(
          279,
          OverlayStyle.STACK,
          "a 105 dp span holds the 100 dp stack, not the 113 dp compact12",
        ),
        arrayOf(
          254,
          OverlayStyle.STACK_NO_UNIT,
          "an 80 dp span holds only the 62 dp no-unit stack",
        ),
      )

    @JvmStatic
    fun singleCenteredAutoCompactCases(): List<Array<Any>> =
      listOf(
        arrayOf("cornerHole", cornerHole),
        arrayOf("noCutout", noCutout),
        arrayOf("foldInner", foldInner),
      )
  }

  @ParameterizedTest(
    name = "STATUS_BAR_CUTOUT with no centered hole on {0} centers a Single COMPACT that fits"
  )
  @MethodSource("singleCenteredAutoCompactCases")
  fun `status bar cutout without centered hole centers single`(
    name: String,
    geometry: DisplayGeometry,
  ) {
    val placement = OverlayPlacement.place(request(), geometry, looks, flankLooks)
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.COMPACT, single.style, "$name: AUTO look")
    assertTrue(single.fits, "$name: COMPACT fits the widthDp - 32 span")
    assertEquals((geometry.widthDp - single.size.width) / 2, single.x, "$name: centered x")
  }

  @ParameterizedTest(name = "{0}: the bar's content starts at {2} and ends {3} from the edge")
  @MethodSource("barInsetCases")
  fun `bar insets follow corner padding and corner holes`(
    name: String,
    geometry: DisplayGeometry,
    startInset: Int,
    endInset: Int,
  ) {
    assertEquals(startInset, OverlayPlacement.barStartInsetDp(geometry), "$name: start inset")
    assertEquals(endInset, OverlayPlacement.barEndInsetDp(geometry), "$name: end inset")
    val start =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_START), geometry, looks, flankLooks)
        as Placement.Single
    assertEquals(
      startInset + 44 + OverlayPlacement.GAP_DP,
      start.x,
      "$name: STATUS_BAR_START clears the clock",
    )
    val end =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_END), geometry, looks, flankLooks)
        as Placement.Single
    assertEquals(
      geometry.widthDp - endInset - 82 - OverlayPlacement.GAP_DP,
      end.x + end.size.width,
      "$name: STATUS_BAR_END clears the icons",
    )
  }

  @Test
  fun `status bar cutout with centered hole flanks the cutout`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val flank = placement as Placement.Flank
    assertEquals(188, flank.download.right, "download half ends 8 dp left of the hole")
    assertEquals(238, flank.upload.left, "upload half starts 8 dp right of the hole")
    val expectedTop = (33 - 24) / 2
    assertEquals(expectedTop, flank.download.top, "download half centered on the hole vertically")
    assertEquals(expectedTop, flank.upload.top, "upload half centered on the hole vertically")
    assertTrue(flank.fits, "both margins clear 16 dp")
  }

  @Test
  fun `status bar cutout with a promoted chip uses the single pill not the flanks`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, promotedChip = true),
        pixel11Pro,
        looks,
        flankLooks,
      )
    // The chip holds the space after the clock, so the pill takes the 83 dp after the hole, where
    // only the 62 dp stack fits.
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, single.style, "the look that fits after the hole")
    assertEquals(238, single.x, "starts 8 dp after the hole")
    assertTrue(single.fits, "fits after the hole")
  }

  @Test
  fun `status bar cutout with a promoted chip and no avoidance centers on the hole`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, promotedChip = true, avoidCutout = false),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals((pixel11Pro.widthDp - single.size.width) / 2, single.x, "centered on the display")
  }

  @Test
  fun `status bar start with a manual look wider than the room before the hole overruns the clock not the hole`() {
    // 166 dp of chip against the 120 dp between the clock and the hole: the chip keeps its end
    // 8 dp before the hole and runs back over the clock instead.
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_START,
          style = OverlayStyle.CHIP,
          statusBarFit = StatusBarFit.MANUAL,
        ),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(188, single.x + single.size.width, "ends 8 dp before the hole")
    assertFalse(single.fits, "wider than the 120 dp before the hole")
  }

  @Test
  fun `status bar cutout on a fold cover whose hole is inset from the top edge still flanks`() {
    // The Pixel 9 Pro Fold's cover reports its hole 8 dp below the top edge (484 to 596 px wide,
    // 20 to 152 px tall at 2.4375 density) inside a 62 dp bar.
    val foldCoverInsetHole =
      DisplayGeometry(
        widthDp = 443,
        heightDp = 994,
        statusBarDp = 62,
        navigationBarDp = 36,
        cutoutRects = listOf(RectDp(199, 8, 245, 62)),
        landscape = false,
      )
    val placement = OverlayPlacement.place(request(), foldCoverInsetHole, looks, flankLooks)
    val flank = placement as Placement.Flank
    assertEquals(191, flank.download.right, "download half ends 8 dp left of the hole")
    assertEquals(253, flank.upload.left, "upload half starts 8 dp right of the hole")
    assertEquals((8 + 62 - 24) / 2, flank.download.top, "centered on the hole's vertical span")
  }

  @Test
  fun `status bar auto with nothing fitting prefers the look inside the span over the shortest`() {
    // 260 dp with no hole leaves 86 dp after the clock and before the icons: only the 62 dp stack
    // fits the width, and its 40 dp height overruns the 24 dp bar; the wide looks cover the icons.
    val narrow =
      DisplayGeometry(
        widthDp = 260,
        heightDp = 600,
        statusBarDp = 24,
        navigationBarDp = 48,
        cutoutRects = emptyList(),
        landscape = false,
      )
    val tall =
      LookSizes(
        mapOf(
          OverlayStyle.COMPACT to SizeDp(128, 24),
          OverlayStyle.COMPACT12 to SizeDp(113, 24),
          OverlayStyle.STACK to SizeDp(100, 24),
          OverlayStyle.STACK_NO_UNIT to SizeDp(62, 40),
        )
      )
    val placement =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_START), narrow, tall, flankLooks)
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, single.style, "the look that stays inside the span")
    assertFalse(single.fits, "it still overruns the bar's height")
  }

  @Test
  fun `status bar cutout flanks that reach the clock fall back to the single pill`() {
    // 140 dp halves put the download half's left edge at 48 dp, under the 44 dp clock. The room
    // before the hole (68 to 188) beats the room after it (238 to 321), so the pill sits before it.
    val wide = LookSizes(mapOf(OverlayStyle.COMPACT12 to SizeDp(140, 24)))
    val placement = OverlayPlacement.place(request(), pixel11Pro, looks, wide)
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.COMPACT12, single.style, "widest look inside the 120 dp before")
    assertEquals(188 - 113, single.x, "ends 8 dp before the hole")
    assertTrue(single.fits, "fits before the hole")
  }

  @Test
  fun `status bar cutout flanks that reach the status icons fall back to the single pill`() {
    // 67 dp halves fit at scale 1; a 100 dp upload half ends at 338 dp, past the icons at 321.
    val wide = LookSizes(mapOf(OverlayStyle.COMPACT12 to SizeDp(100, 24)))
    val placement = OverlayPlacement.place(request(), pixel11Pro, looks, wide)
    val single = placement as Placement.Single
    assertTrue(
      single.x + single.size.width <= 188,
      "clears the hole: ends at ${single.x + single.size.width}",
    )
  }

  @Test
  fun `status bar cutout at font scale two puts the single pill before the hole`() {
    // At scale 2 the clock ends at 104 dp and the icons begin at 239 dp: 76 dp of room before the
    // hole against 1 dp after it, so the 62 dp stack sits before the hole and the hole stays clear.
    val scaled = pixel11Pro.copy(fontScale = 2f)
    val placement = OverlayPlacement.place(request(), scaled, looks, flankLooks) as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, placement.style, "the only look inside 76 dp")
    assertTrue(placement.fits, "fits before the hole")
    assertEquals(188 - 62, placement.x, "ends 8 dp before the hole")
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("autoOrderCases")
  fun `auto tries the two-line stack before the no-unit stack`(
    widthDp: Int,
    expected: OverlayStyle,
    name: String,
  ) {
    val geometry = noCutout.copy(widthDp = widthDp)
    val placement =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_START), geometry, looks, flankLooks)
    val single = placement as Placement.Single
    assertEquals(expected, single.style, name)
    assertTrue(single.fits, name)
  }

  @Test
  fun `neither side holds a look so auto keeps the roomier side with the narrowest look`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.AUTO),
        bothSidesNarrow,
        looks,
        flankLooks,
        SizeDp(50, 14),
      )
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, single.style, "the narrowest look")
    assertFalse(single.fits, "the 62 dp stack overruns a narrow side")
    assertEquals(SlotAlignment.END, single.alignment, "the roomier side, before the hole")
  }

  @Test
  fun `neither side holds a look so auto with the ribbon fallback takes the ribbon`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_CUTOUT,
          statusBarFit = StatusBarFit.AUTO,
          ribbonFallback = true,
        ),
        bothSidesNarrow,
        looks,
        flankLooks,
        SizeDp(50, 14),
      )
    val ribbon = placement as Placement.Ribbon
    assertTrue(ribbon.fits, "the ribbon finds a span neither pill side holds")
    assertEquals(32, ribbon.x, "the free span before the hole, nearest the preferred center")
  }

  @Test
  fun `neither side holds a look so manual keeps the roomier side unfit`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_CUTOUT,
          statusBarFit = StatusBarFit.MANUAL,
          style = OverlayStyle.COMPACT,
        ),
        bothSidesNarrow,
        looks,
        flankLooks,
        SizeDp(50, 14),
      )
    val single = placement as Placement.Single
    assertFalse(single.fits, "COMPACT does not fit either narrow side")
    assertEquals(
      SlotAlignment.END,
      single.alignment,
      "the overrun ends at the hole, which only the before side does",
    )
  }

  @Test
  fun `a hole with no path known keeps the rect's 8 dp gap`() {
    val flank = OverlayPlacement.place(request(), pixel11Pro, looks, flankLooks) as Placement.Flank
    assertEquals(196 - 8, flank.download.right, "download half ends 8 dp before the rect")
    assertEquals(230 + 8, flank.upload.left, "upload half starts 8 dp after the rect")
  }

  @Test
  fun `a measured bar keeps two dp off the clock and the first icon`() {
    // The Pixel 9 Pro with Bluetooth on: icons end 114 dp in and begin 304 dp in. A 67 dp flank
    // half after the hole ends at 305 under 8 dp gaps and cannot fit; 2 dp off the exact edges it
    // ends at 301, three dp clear of the icon.
    val phone =
      ribbonCutoutDisplay.copy(
        cutoutRects =
          listOf(RectDp(195, 0, 232, 52, glassTop = 18, glassLeft = 197, glassRight = 230)),
        measuredBar = MeasuredBar(startFreeDp = 114, endFreeDp = 304),
      )
    val flank = OverlayPlacement.place(request(), phone, looks, flankLooks) as Placement.Flank
    assertTrue(flank.fits, "both halves clear the measured edges")
    assertEquals(230 + 4 + 67, flank.upload.right, "the upload half ends 3 dp before the icon")
    val single =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_START), phone, looks, flankLooks)
        as Placement.Single
    assertEquals(114 + 2, single.x, "a start-anchored pill sits 2 dp after the measured clock")
  }

  @Test
  fun `a reading the clamp overrides keeps the estimate's gap`() {
    // A start reading of zero (no box before the hole center) clamps to the bar's inset; the pill
    // then keeps 8 dp off that estimate, not 2.
    val clamped = pixel11Pro.copy(measuredBar = MeasuredBar(startFreeDp = 0, endFreeDp = 258))
    val single =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_START), clamped, looks, flankLooks)
        as Placement.Single
    assertEquals(OverlayPlacement.barStartInsetDp(clamped) + 8, single.x, "8 dp off the inset")
  }

  @Test
  fun `the glass gap counts from the hole's own edges, not the platform rect`() {
    // The rect boxes the hole with 2 dp of margin each side: 196..230 around glass at 198..228.
    val measuredGlass =
      pixel11Pro.copy(
        cutoutRects = listOf(RectDp(196, 0, 230, 33, glassLeft = 198, glassRight = 228))
      )
    val flank =
      OverlayPlacement.place(request(), measuredGlass, looks, flankLooks) as Placement.Flank
    assertEquals(198 - 4, flank.download.right, "download half ends 4 dp before the glass")
    assertEquals(228 + 4, flank.upload.left, "upload half starts 4 dp after the glass")
  }

  @Test
  fun `a stack one dp too wide under an 8 dp gap fits after the hole under the glass gap`() {
    // The Pixel 9 Pro with Bluetooth on: icons end 138 dp in and begin 304 dp in, the hole's glass
    // runs 197 to 230 inside a rect of 195 to 232, and the no-unit stack is 57 dp. Two 8 dp gaps
    // off the rect left 56 dp after the hole; 4 dp off the glass leaves 62.
    val phone =
      ribbonCutoutDisplay.copy(
        cutoutRects =
          listOf(RectDp(195, 0, 232, 52, glassTop = 18, glassLeft = 197, glassRight = 230)),
        measuredBar = MeasuredBar(startFreeDp = 138, endFreeDp = 304),
      )
    val phoneLooks = LookSizes(looks.sizes + (OverlayStyle.STACK_NO_UNIT to SizeDp(57, 24)))
    val placement =
      OverlayPlacement.place(request(), phone, phoneLooks, flankLooks, SizeDp(116, 14))
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, single.style)
    assertTrue(single.fits, "62 dp after the hole holds the 57 dp stack")
    assertEquals(230 + 4, single.x, "starts 4 dp after the glass")
  }

  @Test
  fun `neither side holds a look and a zero ribbon size keeps the roomier side`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_CUTOUT,
          statusBarFit = StatusBarFit.AUTO,
          ribbonFallback = true,
        ),
        bothSidesNarrow,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertFalse(single.fits, "a zero-size ribbon never counts as a fallback")
    assertEquals(SlotAlignment.END, single.alignment, "keeps the before side instead of the ribbon")
  }

  @Test
  fun `a promoted chip keeps the fallback off the space before the hole`() {
    // The measured bar leaves 20 dp after the hole and the whole bar before it.
    val busyAfter =
      pixel11Pro.copy(measuredBar = MeasuredBar(startFreeDp = 0, endFreeDp = 230 + 8 + 20))
    val placement =
      OverlayPlacement.place(
        request(promotedChip = true, ribbonFallback = true),
        busyAfter,
        looks,
        flankLooks,
        SizeDp(50, 14),
      )
    assertTrue(
      placement is Placement.Ribbon,
      "the before side is the chip's, so the ribbon takes over",
    )
  }

  @Test
  fun `a measured bar narrower than the estimate flips the pill to the roomier real side`() {
    val measured = pixel11Pro.copy(measuredBar = MeasuredBar(startFreeDp = 250, endFreeDp = 329))
    val placement = OverlayPlacement.place(request(), measured, looks, flankLooks)
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, single.style, "the 89 dp span after the hole")
    assertEquals(238, single.x, "starts 8 dp after the hole")
    assertTrue(single.fits, "the measured clock reading kills the before side, not the after one")
  }

  @Test
  fun `measured bar values outside the bar clamp into it`() {
    val wild = pixel11Pro.copy(measuredBar = MeasuredBar(startFreeDp = -50, endFreeDp = 5000))
    assertEquals(
      OverlayPlacement.barStartInsetDp(wild),
      OverlayPlacement.clockRight(wild),
      "a negative reading clamps to the bar's own start",
    )
    assertEquals(
      wild.widthDp - OverlayPlacement.barEndInsetDp(wild),
      OverlayPlacement.endWithIndicator(wild),
      "a reading past the edge clamps to the bar's own end",
    )
  }

  @ParameterizedTest(name = "{0} at font scale {1} with {2} fit never covers the hole")
  @MethodSource("statusBarAnchorsByFontScale")
  fun `status bar anchors never cover the hole with avoidance on`(
    anchor: Anchor,
    fontScale: Float,
    fit: StatusBarFit,
  ) {
    val scaled = pixel11Pro.copy(fontScale = fontScale)
    val placement =
      OverlayPlacement.place(
        request(anchor = anchor, statusBarFit = fit, style = OverlayStyle.CHIP),
        scaled,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    val right = single.x + single.size.width
    assertTrue(
      right <= 196 || single.x >= 230,
      "$anchor at $fontScale ($fit) spans ${single.x}..$right across the 196..230 hole",
    )
  }

  @Test
  fun `status bar start at font scale two starts after the doubled clock`() {
    val scaled = pixel11Pro.copy(fontScale = 2f)
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_START),
        scaled,
        looks,
        flankLooks,
      ) as Placement.Single
    assertEquals(16 + 88 + 8, placement.x, "16 dp margin, the 44 dp clock doubled, 8 dp gap")
  }

  @Test
  fun `status bar auto with every look taller than the bar picks the shortest and reports no fit`() {
    val tall =
      LookSizes(
        mapOf(
          OverlayStyle.COMPACT to SizeDp(128, 32),
          OverlayStyle.COMPACT12 to SizeDp(90, 34),
          OverlayStyle.STACK to SizeDp(100, 36),
          OverlayStyle.STACK_NO_UNIT to SizeDp(62, 40),
        )
      )
    val placement =
      OverlayPlacement.place(request(anchor = Anchor.STATUS_BAR_START), noCutout, tall, flankLooks)
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.COMPACT, single.style, "the shortest look, not the narrowest")
    assertFalse(single.fits, "nothing fits a 24 dp bar")
    assertEquals(0, single.y, "hangs from the top edge, never above it")
  }

  @Test
  fun `status bar cutout on fold cover falls back to the single pill when a half reaches the icons`() {
    // foldCover's cutout (171, 0, 201, 30) is centered, but a 67 dp upload half ends at 276 dp on
    // a 372 dp display whose status icons begin at 266 dp. The 95 dp before the hole beats the
    // 57 dp after it, and only the 62 dp stack fits there.
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT),
        foldCover,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.STACK_NO_UNIT, single.style, "the look that fits before the hole")
    assertEquals(163 - 62, single.x, "ends 8 dp before the hole")
    assertTrue(single.fits, "fits before the hole")
  }

  @Test
  fun `status bar cutout on fold cover flanks when both halves clear the clock and icons`() {
    // A 55 dp upload half ends at 264 dp, inside the 266 dp icon edge.
    val narrow = LookSizes(mapOf(OverlayStyle.COMPACT12 to SizeDp(55, 24)))
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT),
        foldCover,
        looks,
        narrow,
      )
    val flank = placement as Placement.Flank
    assertEquals(163, flank.download.right, "download half ends 8 dp left of the hole")
    assertEquals(209, flank.upload.left, "upload half starts 8 dp right of the hole")
    assertTrue(flank.fits, "both margins clear 16 dp")
  }

  @Test
  fun `status bar start auto on Pixel 11 Pro picks compact12`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_START),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.COMPACT, request().style)
    assertEquals(
      OverlayStyle.COMPACT12,
      single.style,
      "COMPACT's 128 dp does not fit a 120 dp span",
    )
    assertTrue(single.fits)
    assertEquals(68, single.x)
  }

  @Test
  fun `status bar start auto on fold cover picks stack no unit`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_START),
        foldCover,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(
      OverlayStyle.STACK_NO_UNIT,
      single.style,
      "neither COMPACT (128) nor COMPACT12 (113) fits a 95 dp span",
    )
    assertTrue(single.fits)
  }

  @Test
  fun `status bar start manual chip does not fit but keeps position`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_START,
          style = OverlayStyle.CHIP,
          statusBarFit = StatusBarFit.MANUAL,
        ),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(OverlayStyle.CHIP, single.style)
    assertFalse(single.fits, "CHIP's 166 dp does not fit the 120 dp free span")
    assertEquals(188, single.x + single.size.width, "the overrun goes over the clock, not the hole")
  }

  @Test
  fun `status bar end right edge clears clock and privacy indicator`() {
    // With the hole ignored the span runs from the clock to the icons, so the 166 dp chip ends at
    // the icon edge.
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_END,
          style = OverlayStyle.CHIP,
          statusBarFit = StatusBarFit.MANUAL,
          avoidCutout = false,
        ),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(321, single.x + single.size.width, "right edge at widthDp - 16 - 82 - 8")
    assertTrue(single.fits, "166 dp inside the 253 dp span")
  }

  @Test
  fun `status bar end with avoidance keeps a wide look off the hole and reports no fit`() {
    // Only 83 dp separate the hole and the icons; the 166 dp chip starts after the hole and runs
    // over the icons rather than sliding back across the hole.
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_END,
          style = OverlayStyle.CHIP,
          statusBarFit = StatusBarFit.MANUAL,
        ),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(238, single.x, "starts 8 dp after the hole")
    assertFalse(single.fits, "wider than the 83 dp after the hole")
  }

  @Test
  fun `status bar after cutout auto on Pixel 11 Pro picks stack no unit`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_AFTER_CUTOUT),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(
      OverlayStyle.STACK_NO_UNIT,
      single.style,
      "neither COMPACT (128) nor COMPACT12 (113) fits an 83 dp span",
    )
    assertEquals(238, single.x)
  }

  @Test
  fun `status bar before cutout without cutout behaves like end`() {
    val withCutout =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_END,
          style = OverlayStyle.CHIP,
          statusBarFit = StatusBarFit.MANUAL,
        ),
        noCutout,
        looks,
        flankLooks,
      ) as Placement.Single
    val beforeCutout =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_BEFORE_CUTOUT,
          style = OverlayStyle.CHIP,
          statusBarFit = StatusBarFit.MANUAL,
        ),
        noCutout,
        looks,
        flankLooks,
      ) as Placement.Single
    assertEquals(
      withCutout.x,
      beforeCutout.x,
      "STATUS_BAR_BEFORE_CUTOUT mirrors STATUS_BAR_END without a hole",
    )
  }

  @Test
  fun `tablet held in portrait keeps the status bar anchors`() {
    // Rotated from natural landscape into portrait: the bar is still wide, so no fallback.
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_START, landscapeAnchorMode = LandscapeAnchorMode.SAME),
        tabletPortrait,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(16 + 44 + OverlayPlacement.GAP_DP, single.x, "after the clock, in the bar")
    assertEquals(0, single.y, "inside the bar, not below it")
  }

  @Test
  fun `a mirrored rect swaps its side edges`() {
    assertEquals(RectDp(0, 0, 56, 56), RectDp(796, 0, 852, 56).mirrored(852))
    assertEquals(RectDp(196, 0, 230, 33), RectDp(197, 0, 231, 33).mirrored(427))
    assertEquals(
      RectDp(0, 0, 384, 30),
      RectDp(9, 0, 400, 30).mirrored(393),
      "an edge rounded past the display is held inside it",
    )
  }

  @Test
  fun `natural landscape honors the separate landscape anchor`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_START,
          landscapeAnchorMode = LandscapeAnchorMode.SEPARATE,
          landscapeAnchor = Anchor.BOTTOM_END,
        ),
        tablet,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(tablet.widthDp - 8 - single.size.width, single.x, "at the end edge")
    assertEquals(
      tablet.heightDp - tablet.navigationBarDp - 8 - single.size.height,
      single.y,
      "above the navigation bar",
    )
  }

  @Test
  fun `natural landscape keeps the status bar anchors`() {
    // A tablet is landscape without being rotated, so SAME keeps the bar anchor in its bar.
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, landscapeAnchorMode = LandscapeAnchorMode.SAME),
        tablet,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals((tablet.widthDp - single.size.width) / 2, single.x, "centered in the bar")
    assertEquals(0, single.y, "inside the 24 dp bar, not below it")
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("statusBarAnchors")
  fun `a rotated panel with a tablet-wide shorter side keeps every status bar anchor`(
    anchor: Anchor
  ) {
    val placement =
      OverlayPlacement.place(
        request(anchor = anchor, landscapeAnchorMode = LandscapeAnchorMode.SAME),
        foldInnerLandscape,
        looks,
        flankLooks,
      )
    val boxes =
      when (placement) {
        is Placement.Single ->
          listOf(
            RectDp(
              placement.x,
              placement.y,
              placement.x + placement.size.width,
              placement.y + placement.size.height,
            )
          )
        is Placement.Flank -> listOf(placement.download, placement.upload)
        is Placement.Ribbon -> error("no ribbon was asked for")
      }
    assertTrue(placement.fits, "$anchor fits the 883 dp bar")
    val spanStart = OverlayPlacement.clockRight(foldInnerLandscape) + OverlayPlacement.GAP_DP
    val spanEnd = OverlayPlacement.endWithIndicator(foldInnerLandscape)
    for (box in boxes) {
      assertTrue(box.left >= spanStart, "$anchor starts at ${box.left} under the clock")
      assertTrue(box.right <= spanEnd, "$anchor ends at ${box.right} over the icons")
      assertTrue(
        box.top >= 0 && box.bottom <= foldInnerLandscape.statusBarDp,
        "$anchor sits at ${box.top}..${box.bottom}, outside the 56 dp bar",
      )
    }
  }

  @Test
  fun `landscape same falls back to top start for status bar start`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_START, landscapeAnchorMode = LandscapeAnchorMode.SAME),
        pixel11ProLandscape,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(8, single.x)
    assertEquals(32, single.y, "statusBarDp (24) + 8")
  }

  @Test
  fun `landscape separate uses the landscape anchor`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_START,
          landscapeAnchorMode = LandscapeAnchorMode.SEPARATE,
          landscapeAnchor = Anchor.BOTTOM_END,
          style = OverlayStyle.CHIP,
        ),
        pixel11ProLandscape,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    val chip = looks.sizes.getValue(OverlayStyle.CHIP)
    assertEquals(pixel11ProLandscape.widthDp - 8 - chip.width, single.x)
    assertEquals(
      pixel11ProLandscape.heightDp - pixel11ProLandscape.navigationBarDp - 8 - chip.height,
      single.y,
    )
  }

  @Test
  fun `top end on tablet places chip at the top right edge`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.TOP_END, style = OverlayStyle.CHIP),
        tablet,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(1280 - 8 - 166, single.x)
    assertEquals(32, single.y)
  }

  @Test
  fun `bottom start on Pixel 11 Pro places chip above the navigation bar`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.BOTTOM_START, style = OverlayStyle.CHIP),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(8, single.x)
    assertEquals(952 - 36 - 8 - 24, single.y)
  }

  @Test
  fun `custom clamps into the display bounds`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.CUSTOM, customXdp = -50, customYdp = 2000),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(0, single.x)
    assertEquals(952 - 24, single.y)
  }

  @Test
  fun `offset shifts both flank halves`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, offsetXdp = 10),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val flank = placement as Placement.Flank
    assertEquals(188 + 10, flank.download.right)
    assertEquals(238 + 10, flank.upload.left)
  }

  @Test
  fun `large offset clamps flank halves at the right edge`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, offsetXdp = 400),
        pixel11Pro,
        looks,
        flankLooks,
      )
    // The halves move as one pair, so the upload half stops at the edge and the download half
    // keeps its 50 dp gap before it.
    val flank = placement as Placement.Flank
    assertEquals(pixel11Pro.widthDp, flank.upload.right, "clamped to the display's right edge")
    assertEquals(pixel11Pro.widthDp - 117, flank.download.right, "50 dp before the upload half")
  }

  @Test
  fun `look wider than the display clamps to zero instead of throwing`() {
    val oversized = LookSizes(mapOf(OverlayStyle.CHIP to SizeDp(428, 24)))
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.TOP_START, style = OverlayStyle.CHIP),
        pixel11Pro,
        oversized,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(0, single.x, "a 428 dp look on a 427 dp display clamps to the left edge")
    assertFalse(single.fits)
  }

  @Test
  fun `a grossly oversized look clamps to zero instead of throwing`() {
    val oversized = LookSizes(mapOf(OverlayStyle.CHIP to SizeDp(100_004, 24)))
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.TOP_START, style = OverlayStyle.CHIP),
        pixel11Pro,
        oversized,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals(0, single.x)
    assertFalse(single.fits)
  }

  @Test
  fun `oversized flank halves give way to a single pill on the display`() {
    val wideFlank = LookSizes(mapOf(OverlayStyle.COMPACT12 to SizeDp(300, 24)))
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT),
        foldCover,
        looks,
        wideFlank,
      )
    // Halves wider than the display never fit, so the single pill takes over and clamps instead.
    val single = placement as Placement.Single
    assertTrue(single.x >= 0, "the single pill stays on the display: x ${single.x}")
    assertTrue(single.x + single.size.width <= 372, "the single pill ends on the display")
  }

  @Test
  fun `avoid cutout false treats the hole as absent`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, avoidCutout = false),
        pixel11Pro,
        looks,
        flankLooks,
      )
    val single = placement as Placement.Single
    assertEquals((pixel11Pro.widthDp - single.size.width) / 2, single.x, "centered, not flanked")
  }

  @Test
  fun `slim ribbon centers on a centered cutout`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        ribbonCutoutDisplay,
        looks,
        flankLooks,
        ribbonSize,
      )
    val ribbon = placement as Placement.Ribbon
    assertEquals(155, ribbon.x, "(195 + 232 - 116) / 2")
    assertEquals(2, ribbon.y)
    assertTrue(ribbon.fits)
  }

  @ParameterizedTest(name = "slim ribbon beside a corner hole centers on the bar: {0}")
  @MethodSource("cornerHoleDisplays")
  fun `slim ribbon beside a corner hole centers on the bar and clears the hole`(
    name: String,
    geometry: DisplayGeometry,
  ) {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        geometry,
        looks,
        flankLooks,
        ribbonSize,
      )
    val ribbon = placement as Placement.Ribbon
    assertEquals((geometry.widthDp - ribbonSize.width) / 2, ribbon.x, name)
    val hole = geometry.cutoutRects.single()
    assertTrue(
      ribbon.x + ribbonSize.width <= hole.left || ribbon.x >= hole.right,
      "$name: the ribbon ${ribbon.x}..${ribbon.x + ribbonSize.width} covers the hole",
    )
  }

  @Test
  fun `slim fit on a rotated phone falls back to the corner pill`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        pixel11ProLandscape,
        looks,
        flankLooks,
        ribbonSize,
      )
    assertTrue(placement is Placement.Single, "$placement")
  }

  @Test
  fun `slim ribbon beside a hole whose glass starts inside the strip moves beside the hole`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        foldCoverInsetHole,
        looks,
        flankLooks,
        ribbonSize,
      )
    val ribbon = placement as Placement.Ribbon
    // 199 dp before the hole beats 198 after it, so the ribbon ends a gap before the hole.
    assertEquals(199 - OverlayPlacement.GAP_DP - ribbonSize.width, ribbon.x)
    assertEquals(2, ribbon.y)
  }

  @ParameterizedTest(name = "two holes listed {0}")
  @MethodSource("holeOrders")
  fun `slim ribbon taller than the strip takes the free span nearest the hole, whatever the order`(
    order: String,
    rects: List<RectDp>,
  ) {
    // At font scale 1.3 the ribbon is 151 by 18 dp, so its strip reaches the round hole's glass.
    val tall = SizeDp(151, 18)
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        centeredAndCornerHoles.copy(cutoutRects = rects),
        looks,
        flankLooks,
        tall,
      )
    val ribbon = placement as Placement.Ribbon
    // The span before the hole (0 to 187) holds it, the one between the holes (240 to 382) does
    // not, so the ribbon ends a gap before the centered hole.
    assertEquals(195 - OverlayPlacement.GAP_DP - tall.width, ribbon.x, order)
    assertTrue(ribbon.fits, order)
    for (hole in rects) {
      assertTrue(
        ribbon.x + tall.width <= hole.left || ribbon.x >= hole.right,
        "$order: the ribbon ${ribbon.x}..${ribbon.x + tall.width} covers $hole",
      )
    }
  }

  @Test
  fun `slim ribbon no span holds stays centered and reports the overlap`() {
    val wide = SizeDp(232, 28)
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        ribbonCutoutDisplay,
        looks,
        flankLooks,
        wide,
      )
    val ribbon = placement as Placement.Ribbon
    assertEquals((195 + 232 - wide.width) / 2, ribbon.x)
    assertFalse(ribbon.fits)
  }

  @Test
  fun `slim ribbon without a cutout centers on the bar`() {
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        noCutout,
        looks,
        flankLooks,
        ribbonSize,
      )
    val ribbon = placement as Placement.Ribbon
    assertEquals((noCutout.widthDp - ribbonSize.width) / 2, ribbon.x)
    assertEquals(2, ribbon.y)
  }

  @Test
  fun `slim ribbon ignores avoid cutout`() {
    val placement =
      OverlayPlacement.place(
        request(
          anchor = Anchor.STATUS_BAR_CUTOUT,
          statusBarFit = StatusBarFit.SLIM,
          avoidCutout = false,
        ),
        ribbonCutoutDisplay,
        looks,
        flankLooks,
        ribbonSize,
      )
    val ribbon = placement as Placement.Ribbon
    assertEquals(155, ribbon.x, "centers on the cutout even though avoidCutout is false")
  }

  @Test
  fun `slim ribbon wider than the display clamps to zero`() {
    val oversizedRibbon = SizeDp(500, 14)
    val placement =
      OverlayPlacement.place(
        request(anchor = Anchor.STATUS_BAR_CUTOUT, statusBarFit = StatusBarFit.SLIM),
        ribbonCutoutDisplay,
        looks,
        flankLooks,
        oversizedRibbon,
      )
    val ribbon = placement as Placement.Ribbon
    assertEquals(0, ribbon.x)
    assertFalse(ribbon.fits)
  }
}
