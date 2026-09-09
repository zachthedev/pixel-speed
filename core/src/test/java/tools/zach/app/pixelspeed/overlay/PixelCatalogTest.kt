package tools.zach.app.pixelspeed.overlay

import kotlin.math.floor
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.overlay.OverlayPlacement.isWidePanel

/**
 * Every Pixel that runs Android 12 or later, as the placement sees it: the display in dp, the
 * status bar, and the hole. Sizes and densities come from the SDK's device definitions
 * (`com/android/sdklib/devices/nexus.xml`); the Pixel 4a 5G and 5a are absent there and carry their
 * published resolution. Hole rects follow the model's camera class, since the exact rect lives in
 * each device's vendor overlay: a centered 30 dp hole on the Pixel 6 and later, a 30 dp hole in the
 * top start corner on the 4a through 5a, the Pixel 3 XL's notch, and no hole on the rest. Fold
 * covers carry the rects measured on their AVDs.
 */
class PixelCatalogTest {

  data class PixelDevice(val name: String, val geometry: DisplayGeometry)

  companion object {
    /** A display's bounds hold only whole dp, the way the app reads them. */
    private fun dp(px: Int, dpi: Int): Int = floor(px * 160.0 / dpi).toInt()

    /**
     * A round hole under a rect from the edge. Its glass line is the 9 Pro's (a circle whose top
     * sits at 53 px of 480 dpi, 18 dp); the other models are unmeasured and take the same value.
     */
    private fun centeredHole(widthDp: Int, barDp: Int): List<RectDp> =
      listOf(RectDp(widthDp / 2 - 15, 0, widthDp / 2 + 15, barDp - 4, glassTop = 18))

    private fun cornerHole(): List<RectDp> = listOf(RectDp(9, 0, 39, 30))

    private fun phone(
      name: String,
      widthPx: Int,
      heightPx: Int,
      dpi: Int,
      barDp: Int,
      hole: (widthDp: Int) -> List<RectDp>,
    ): PixelDevice {
      val widthDp = dp(widthPx, dpi)
      return PixelDevice(
        name,
        DisplayGeometry(
          widthDp = widthDp,
          heightDp = dp(heightPx, dpi),
          statusBarDp = barDp,
          navigationBarDp = 48,
          cutoutRects = hole(widthDp),
          landscape = false,
        ),
      )
    }

    private val none: (Int) -> List<RectDp> = { emptyList() }
    private val corner: (Int) -> List<RectDp> = { cornerHole() }
    private val centered: (Int) -> List<RectDp> = { centeredHole(it, 52) }

    val catalog: List<PixelDevice> =
      listOf(
        // Published resolutions for the two models the SDK's catalog lacks.
        phone("Pixel 4a 5G", 1080, 2340, 420, 28, corner),
        phone("Pixel 5a", 1080, 2400, 420, 28, corner),
        // SDK device definitions.
        phone("Pixel 3", 1080, 2160, 440, 24, none),
        phone("Pixel 3 XL", 1440, 2960, 560, 33) { w ->
          listOf(RectDp(w / 2 - 54, 0, w / 2 + 54, 33))
        },
        phone("Pixel 3a", 1080, 2220, 440, 24, none),
        phone("Pixel 3a XL", 1080, 2160, 400, 24, none),
        phone("Pixel 4", 1080, 2280, 440, 24, none),
        phone("Pixel 4 XL", 1440, 3040, 560, 24, none),
        phone("Pixel 4a", 1080, 2340, 440, 28, corner),
        phone("Pixel 5", 1080, 2340, 440, 28, corner),
        phone("Pixel 6", 1080, 2400, 420, 52, centered),
        phone("Pixel 6 Pro", 1440, 3120, 560, 52, centered),
        phone("Pixel 6a", 1080, 2400, 420, 52, centered),
        phone("Pixel 7", 1080, 2400, 420, 52, centered),
        phone("Pixel 7 Pro", 1440, 3120, 560, 52, centered),
        phone("Pixel 7a", 1080, 2400, 420, 52, centered),
        phone("Pixel 8", 1080, 2400, 420, 52, centered),
        phone("Pixel 8 Pro", 1344, 2992, 480, 52, centered),
        phone("Pixel 8a", 1080, 2400, 420, 52, centered),
        phone("Pixel 9", 1080, 2424, 420, 52, centered),
        phone("Pixel 9a", 1080, 2424, 420, 52, centered),
        // Measured on the phone: a 204 px bar, the cutout rect 586 to 695 px, and a glass circle
        // of radius 49 px centered 102 px down, all at 480 dpi (the AVD reports a 156 px bar).
        phone("Pixel 9 Pro", 1280, 2856, 480, 68) { w ->
          listOf(
            RectDp(
              w / 2 - 18,
              0,
              w / 2 + 19,
              68,
              glassTop = 18,
              glassLeft = w / 2 - 16,
              glassRight = w / 2 + 17,
            )
          )
        },
        phone("Pixel 9 Pro XL", 1344, 2992, 480, 52, centered),
        phone("Pixel 10", 1080, 2424, 420, 52, centered),
        phone("Pixel 10a", 1080, 2424, 420, 52, centered),
        phone("Pixel 10 Pro", 1280, 2856, 480, 52, centered),
        phone("Pixel 10 Pro XL", 1344, 2992, 480, 52, centered),
        // Fold covers measured on the AVDs; the inner panels are portrait when open, and the
        // 9 and 10 Pro Fold inner panels carry a hole in the top end corner whose glass starts
        // 40.5 px down at 390 dpi, 17 dp. The original Fold's cover hole is unmeasured.
        PixelDevice(
          "Pixel Fold cover",
          DisplayGeometry(372, 820, 60, 36, listOf(RectDp(171, 0, 201, 30)), false),
        ),
        // The 9 Pro Fold's cover, measured on the AVD: rect 484 to 596 by 20 to 152 px at 390 dpi,
        // cutout circle center 86 px with a 41.5 px radius, so the glass starts at 44.5 px: 18 dp.
        PixelDevice(
          "Pixel 9 Pro Fold cover",
          DisplayGeometry(443, 994, 62, 36, listOf(RectDp(199, 8, 245, 62, glassTop = 18)), false),
        ),
        PixelDevice(
          "Pixel 10 Pro Fold cover",
          DisplayGeometry(443, 994, 62, 36, listOf(RectDp(199, 8, 245, 62, glassTop = 18)), false),
        ),
        PixelDevice("Pixel Fold inner", DisplayGeometry(701, 841, 24, 40, emptyList(), false)),
        // The inner panels pad the bar's content by 32 dp for their rounded corners.
        PixelDevice(
          "Pixel 9 Pro Fold inner",
          DisplayGeometry(
            852,
            883,
            56,
            40,
            listOf(RectDp(796, 0, 852, 56, glassTop = 17)),
            false,
            cornerPaddingDp = 32,
          ),
        ),
        PixelDevice(
          "Pixel 10 Pro Fold inner",
          DisplayGeometry(
            852,
            883,
            56,
            40,
            listOf(RectDp(796, 0, 852, 56, glassTop = 17)),
            false,
            cornerPaddingDp = 32,
          ),
        ),
        PixelDevice("Pixel Tablet", DisplayGeometry(1280, 800, 24, 40, emptyList(), true)),
      )

    /** Looks measured on the Pixel 9 Pro at scale 1, scaled the way sp text scales them. */
    private fun looks(fontScale: Float): LookSizes =
      LookSizes(
        mapOf(
            OverlayStyle.COMPACT to SizeDp(128, 24),
            OverlayStyle.COMPACT12 to SizeDp(113, 24),
            OverlayStyle.STACK to SizeDp(100, 24),
            OverlayStyle.STACK_NO_UNIT to SizeDp(62, 24),
          )
          .mapValues { (_, size) ->
            SizeDp((size.width * fontScale).roundToInt(), (size.height * fontScale).roundToInt())
          }
      )

    private fun flankLooks(fontScale: Float): LookSizes =
      LookSizes(
        mapOf(
          OverlayStyle.COMPACT12 to
            SizeDp((67 * fontScale).roundToInt(), (24 * fontScale).roundToInt())
        )
      )

    private val statusBarAnchors =
      listOf(
        Anchor.STATUS_BAR_CUTOUT,
        Anchor.STATUS_BAR_START,
        Anchor.STATUS_BAR_BEFORE_CUTOUT,
        Anchor.STATUS_BAR_AFTER_CUTOUT,
        Anchor.STATUS_BAR_END,
      )

    /**
     * The device turned the other way: a rotated display with the sides swapped. A corner hole
     * leaves the top edge when the panel turns, so the rotated copy carries none.
     */
    private fun PixelDevice.rotated(): PixelDevice =
      PixelDevice(
        "$name rotated",
        geometry.copy(
          widthDp = geometry.heightDp,
          heightDp = geometry.widthDp,
          cutoutRects = emptyList(),
          landscape = !geometry.landscape,
          rotated = true,
        ),
      )

    private val allDevices: List<PixelDevice> = catalog + catalog.map { it.rotated() }

    /** The devices that keep their status bar anchors: everything unrotated, and wide panels. */
    @JvmStatic
    fun ribbonCases(): List<Array<Any>> =
      allDevices
        .filter { !(it.geometry.rotated && it.geometry.landscape) || it.geometry.isWidePanel }
        .flatMap { device ->
          listOf(1.0f, 1.3f, 2.0f).map { scale -> arrayOf<Any>(device.name, device, scale) }
        }

    @JvmStatic
    fun cases(): List<Array<Any>> = allDevices.flatMap { device ->
      listOf(1.0f, 1.3f, 2.0f).flatMap { scale ->
        statusBarAnchors.map { anchor -> arrayOf<Any>(device.name, device, scale, anchor) }
      }
    }

    private fun request(anchor: Anchor): PlacementRequest =
      PlacementRequest(
        anchor = anchor,
        landscapeAnchorMode = LandscapeAnchorMode.SAME,
        landscapeAnchor = anchor,
        style = OverlayStyle.COMPACT,
        statusBarFit = StatusBarFit.AUTO,
        offsetXdp = 0,
        offsetYdp = 0,
        customXdp = 0,
        customYdp = 0,
        avoidCutout = true,
        promotedChip = false,
      )
  }

  /**
   * Pinned literals for three devices, so a change to the clock, icon, or margin constants has to
   * break something the sweep below computes with the same functions.
   */
  @Test
  fun `pinned spans on three devices`() {
    fun place(device: PixelDevice, scale: Float, anchor: Anchor = Anchor.STATUS_BAR_CUTOUT) =
      OverlayPlacement.place(
        request(anchor),
        device.geometry.copy(fontScale = scale),
        looks(scale),
        flankLooks(scale),
      )
    val pixel9 = catalog.first { it.name == "Pixel 9" }
    val foldCover = catalog.first { it.name == "Pixel Fold cover" }
    // Pixel 9: 411 dp wide at 420 dpi, hole 190..220 with no path known; the flanks sit 8 dp
    // off each edge of the rect.
    val flank = place(pixel9, 1.0f) as Placement.Flank
    assertTrue(flank.download.right == 182, "Pixel 9 download half ends at ${flank.download.right}")
    assertTrue(flank.upload.left == 228, "Pixel 9 upload half starts at ${flank.upload.left}")
    // Pixel 9 at scale 2: the clock ends at 104 dp and the icons begin at 223 dp, so only the
    // 124 dp stack goes before the hole, ending 8 dp before it and overrunning the clock.
    val scaled = place(pixel9, 2.0f) as Placement.Single
    assertTrue(scaled.style == OverlayStyle.STACK_NO_UNIT, "Pixel 9 at 2.0 picks ${scaled.style}")
    assertTrue(scaled.x == 182 - 124, "Pixel 9 at 2.0 starts at ${scaled.x}")
    assertTrue(!scaled.fits, "Pixel 9 at 2.0 cannot fit 124 dp into 70 dp")
    // The original Fold's 372 dp cover: 95 dp before the hole holds the 62 dp stack at 101.
    val cover = place(foldCover, 1.0f) as Placement.Single
    assertTrue(cover.style == OverlayStyle.STACK_NO_UNIT, "Fold cover picks ${cover.style}")
    assertTrue(cover.x == 101 && cover.fits, "Fold cover stack at ${cover.x}, fits ${cover.fits}")
  }

  /**
   * The whole contract, per device: every span sits on the display; a placement that reports a fit
   * clears the clock and the status icons; and with avoidance on nothing covers a centered hole,
   * which is the only hole the placement promises to avoid.
   */
  @ParameterizedTest(name = "{0} at font scale {2}, {3}")
  @MethodSource("cases")
  fun `every pixel keeps the readout on the display off the clock and icons and off the hole`(
    name: String,
    device: PixelDevice,
    fontScale: Float,
    anchor: Anchor,
  ) {
    val geometry = device.geometry.copy(fontScale = fontScale)
    val placement =
      OverlayPlacement.place(request(anchor), geometry, looks(fontScale), flankLooks(fontScale))
    val label = "$name at $fontScale ($anchor)"
    val boxes: List<RectDp> =
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
        is Placement.Ribbon ->
          listOf(
            RectDp(
              placement.x,
              placement.y,
              placement.x + placement.size.width,
              placement.y + placement.size.height,
            )
          )
      }
    val inBar = !(geometry.rotated && geometry.landscape) || geometry.isWidePanel
    val spanStart = OverlayPlacement.clockRight(geometry) + OverlayPlacement.GAP_DP
    val spanEnd = OverlayPlacement.endWithIndicator(geometry)
    val hole =
      geometry.cutoutRects.firstOrNull {
        it.top <= OverlayPlacement.TOP_CUTOUT_INSET_DP &&
          OverlayPlacement.isCentered(it, geometry.widthDp)
      }
    for (box in boxes) {
      assertTrue(
        box.left >= 0 && box.right <= geometry.widthDp,
        "$label: ${box.left}..${box.right} leaves the display",
      )
      assertTrue(
        box.top >= 0 && box.bottom <= geometry.heightDp,
        "$label: ${box.top}..${box.bottom} leaves the display",
      )
      if (inBar && placement.fits) {
        assertTrue(box.left >= spanStart, "$label: fits yet starts at ${box.left} under the clock")
        assertTrue(box.right <= spanEnd, "$label: fits yet ends at ${box.right} over the icons")
      }
      if (inBar && hole != null) {
        assertTrue(
          box.right <= hole.left || box.left >= hole.right,
          "$label: ${box.left}..${box.right} covers the hole ${hole.left}..${hole.right}",
        )
      }
    }
  }

  @ParameterizedTest(name = "{0} at font scale {2} keeps the slim ribbon off every hole")
  @MethodSource("ribbonCases")
  fun `every pixel keeps the slim ribbon on the display and off every hole`(
    name: String,
    device: PixelDevice,
    fontScale: Float,
  ) {
    val geometry = device.geometry.copy(fontScale = fontScale)
    // The ribbon's text is 9 sp, so its measured size follows the font scale like the looks.
    val ribbonSize = SizeDp((116 * fontScale).roundToInt(), (14 * fontScale).roundToInt())
    val placement =
      OverlayPlacement.place(
        request(Anchor.STATUS_BAR_CUTOUT).copy(statusBarFit = StatusBarFit.SLIM),
        geometry,
        looks(fontScale),
        flankLooks(fontScale),
        ribbonSize,
      )
    val ribbon = placement as Placement.Ribbon
    val box = RectDp(ribbon.x, ribbon.y, ribbon.x + ribbonSize.width, ribbon.y + ribbonSize.height)
    val label =
      "$name at $fontScale: the ribbon ${box.left}..${box.right} by ${box.top}..${box.bottom}"
    assertTrue(box.left >= 0 && box.right <= geometry.widthDp, "$label leaves the display")
    // The holes whose glass reaches into the ribbon's strip, from the catalog's own glass lines.
    val blockers = geometry.cutoutRects.filter { it.glassTop < box.bottom && it.bottom > box.top }
    val covered = blockers.filter { box.right > it.left && box.left < it.right }
    if (ribbon.fits) {
      assertTrue(covered.isEmpty(), "$label covers $covered and claims to fit")
      return
    }
    // A reported non-fit must be unavoidable: the ribbon hangs below the bar, or no spot inside
    // the bar's content area keeps a gap from every blocker.
    val tallerThanBar = geometry.statusBarDp > 0 && box.bottom > geometry.statusBarDp
    val start = OverlayPlacement.barStartInsetDp(geometry)
    val end = geometry.widthDp - OverlayPlacement.barEndInsetDp(geometry) - ribbonSize.width
    val spot =
      (start..end).firstOrNull { x ->
        blockers.none { hole ->
          x < hole.right + OverlayPlacement.GAP_DP &&
            x + ribbonSize.width > hole.left - OverlayPlacement.GAP_DP
        }
      }
    assertTrue(tallerThanBar || spot == null, "$label reports no fit while x=$spot holds it")
  }
}
