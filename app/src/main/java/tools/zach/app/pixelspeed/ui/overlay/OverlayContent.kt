package tools.zach.app.pixelspeed.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.LookSizes
import tools.zach.app.pixelspeed.overlay.OverlayPlacement
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.overlay.Placement
import tools.zach.app.pixelspeed.overlay.PlacementRequest
import tools.zach.app.pixelspeed.overlay.SizeDp
import tools.zach.app.pixelspeed.overlay.SlotAlignment
import tools.zach.app.pixelspeed.settings.DirectionMarker
import tools.zach.app.pixelspeed.settings.DirectionOrder

/** Where the window has to move so the placed content lands where the placement says. */
data class WindowOrigin(val xDp: Int, val yDp: Int)

/** Everything [SpeedReadout] and [SlimRibbon] need beyond the formatted values and the look. */
data class ReadoutPresentation(
  val colors: ReadoutColors,
  val textSize: TextUnit,
  val readoutStyle: ReadoutStyle,
  val showDownload: Boolean = true,
  val showUpload: Boolean = true,
  val directionOrder: DirectionOrder = DirectionOrder.DOWNLOAD_FIRST,
  val directionMarker: DirectionMarker = DirectionMarker.ARROWS,
  val showNetworkGlyph: Boolean = false,
  val combined: Boolean = false,
)

@Composable
private fun PresentedReadout(
  values: ReadoutValues,
  style: OverlayStyle,
  presentation: ReadoutPresentation,
) {
  SpeedReadout(
    values = values,
    style = style,
    colors = presentation.colors,
    textSize = presentation.textSize,
    readoutStyle = presentation.readoutStyle,
    showDownload = presentation.showDownload,
    showUpload = presentation.showUpload,
    directionOrder = presentation.directionOrder,
    directionMarker = presentation.directionMarker,
    showNetworkGlyph = presentation.showNetworkGlyph,
    combined = presentation.combined,
  )
}

/**
 * Fades and scales the content in when [transitionKey] changes, on the graphics layer only, so the
 * window's layout never sees it. The key is the style the settings ask for, never the one auto-fit
 * picked from the measured digits, so a per-tick value update or an auto-fit downgrade snaps and
 * only a settings change animates. Skips the animation while [LocalReadoutMotion] is off.
 */
@Composable
private fun <T> AnimatedReadout(transitionKey: T, content: @Composable () -> Unit) {
  if (!LocalReadoutMotion.current) {
    content()
    return
  }
  val effectsSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
  val spatialSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
  val scale = remember { Animatable(1f) }
  val alpha = remember { Animatable(1f) }
  val seen = remember { mutableStateOf<T?>(null) }
  LaunchedEffect(transitionKey) {
    val first = seen.value == null
    seen.value = transitionKey
    if (first) return@LaunchedEffect
    scale.snapTo(SETTLE_START_SCALE)
    alpha.snapTo(0f)
    launch { scale.animateTo(1f, spatialSpec) }
    alpha.animateTo(1f, effectsSpec)
  }
  Box(
    Modifier.graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
      this.alpha = alpha.value
    }
  ) {
    content()
  }
}

private const val SETTLE_START_SCALE: Float = 0.9f

/**
 * Measures every look the placement might choose on [measureValues], the widest strings the format
 * can produce, asks the placement function where things go, then lays out either one pill, the two
 * flank halves, or the slim ribbon relative to the window's own origin and reports that origin so
 * the window can move there, and whether the look fits. The live [values] render inside the slot
 * the measurement reserved, so a tier change in the digits never moves the window. Sizes cross into
 * the placement function in whole dp, the same unit the window position uses.
 */
@Composable
fun OverlayContent(
  values: ReadoutValues,
  measureValues: ReadoutValues,
  request: PlacementRequest,
  geometry: DisplayGeometry,
  presentation: ReadoutPresentation,
  onOrigin: (WindowOrigin) -> Unit,
  onFits: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  placeAtOrigin: Boolean = false,
) {
  var origin: WindowOrigin? by remember { mutableStateOf(null) }
  var fits: Boolean? by remember { mutableStateOf(null) }
  // A plain holder, not snapshot state: it is written inside measure, where a state write would
  // invalidate the layout that is being measured.
  val lastLogged = remember { arrayOfNulls<String>(1) }
  SubcomposeLayout(modifier) { constraints ->
    val loose = Constraints()
    // Each size is the larger of the widest form and the live form, so the slot never moves while
    // the live digits stay inside the bound and still holds them when a fixed prefix runs past it.
    fun measure(slot: String, content: @Composable () -> Unit): Placeable =
      subcompose(slot, content).first().measure(loose)
    // A live form measures with motion off: the loading dots hold their first frame, so the slot
    // reserves their width and nothing in a measurement slot invalidates the layout every frame.
    fun measureStill(slot: String, content: @Composable () -> Unit): Placeable =
      measure(slot) {
        CompositionLocalProvider(LocalReadoutMotion provides false, content = content)
      }
    fun sizeOf(worst: Placeable, live: Placeable): SizeDp =
      SizeDp(
        max(worst.width, live.width).toDp().roundUp(),
        max(worst.height, live.height).toDp().roundUp(),
      )
    fun measureLook(style: OverlayStyle): SizeDp =
      sizeOf(
        measure("measure-$style") { PresentedReadout(measureValues, style, presentation) },
        measureStill("measure-live-$style") { PresentedReadout(values, style, presentation) },
      )
    val styles = (OverlayPlacement.AUTO_ORDER + request.style).distinct()
    val looks = LookSizes(styles.associateWith(::measureLook))
    val flankDown =
      sizeOf(
        measure("measure-flank-down") {
          FlankHalf(measureValues, download = true, presentation = presentation)
        },
        measureStill("measure-live-flank-down") {
          FlankHalf(values, download = true, presentation = presentation)
        },
      )
    val flankUp =
      sizeOf(
        measure("measure-flank-up") {
          FlankHalf(measureValues, download = false, presentation = presentation)
        },
        measureStill("measure-live-flank-up") {
          FlankHalf(values, download = false, presentation = presentation)
        },
      )
    val flankSize =
      SizeDp(max(flankDown.width, flankUp.width), max(flankDown.height, flankUp.height))
    val flankLooks = LookSizes(mapOf(OverlayStyle.COMPACT12 to flankSize))
    val ribbonSize =
      sizeOf(
        measure("measure-ribbon") {
          SlimRibbon(
            measureValues,
            colors = presentation.colors,
            readoutStyle = presentation.readoutStyle,
          )
        },
        measureStill("measure-live-ribbon") {
          SlimRibbon(values, colors = presentation.colors, readoutStyle = presentation.readoutStyle)
        },
      )

    val placement = OverlayPlacement.place(request, geometry, looks, flankLooks, ribbonSize)
    fits = placement.fits
    // A drag in edit mode moves the window by whole dp, so the log keeps the placement's kind,
    // look, and fit, and not every coordinate a drag passes through.
    val signature: String = placementSignature(placement)
    if (signature != lastLogged[0]) {
      lastLogged[0] = signature
      AppLog.i("Placement: $placement")
    }
    // The window host sits at the origin itself, so the content is placed at zero. A host that
    // draws the whole display instead asks for the content at its origin in the same pass, since
    // the origin report reaches it only after the frame.
    fun placed(
      width: Int,
      height: Int,
      place: Placeable.PlacementScope.(dx: Int, dy: Int) -> Unit,
    ): MeasureResult {
      val at: WindowOrigin? = origin.takeIf { placeAtOrigin }
      val dx = at?.xDp?.dp?.roundToPx() ?: 0
      val dy = at?.yDp?.dp?.roundToPx() ?: 0
      return layout(width + dx, height + dy) { place(dx, dy) }
    }
    when (placement) {
      is Placement.Single -> {
        origin = WindowOrigin(placement.x, placement.y)
        val content =
          subcompose("content") {
              AnimatedReadout(request.style) {
                PresentedReadout(values, placement.style, presentation)
              }
            }
            .first()
            .measure(loose)
        // The live look hugs the edge its anchor names inside the slot its widest form reserved.
        val slotWidth = max(placement.size.width.dp.roundToPx(), content.width)
        val slotHeight = max(placement.size.height.dp.roundToPx(), content.height)
        val x =
          when (placement.alignment) {
            SlotAlignment.START -> 0
            SlotAlignment.CENTER ->
              centeredInSlot(slotWidth, content.width, roomPx(placement.x, geometry.widthDp))
            SlotAlignment.END -> slotWidth - content.width
          }
        // Placement x runs in the layout direction, so the placing does too.
        placed(slotWidth, slotHeight) { dx, dy ->
          content.placeRelative(dx + x, dy + (slotHeight - content.height) / 2)
        }
      }
      is Placement.Flank -> {
        // A hidden direction leaves its half out; the other keeps its slot beside the hole. At
        // least one direction always shows, so the list is never empty.
        val slots =
          listOfNotNull(
            if (presentation.showDownload) placement.download to true else null,
            if (presentation.showUpload) placement.upload to false else null,
          )
        val left = slots.minOf { it.first.left }
        val top = slots.minOf { it.first.top }
        origin = WindowOrigin(left, top)
        // The visible-direction pair is this half's state: a direction hiding or showing
        // crossfades, the number ticking every sample does not.
        val visibility = presentation.showDownload to presentation.showUpload
        val halves = slots.map { (rect, isDownload) ->
          val half =
            subcompose(if (isDownload) "flank-down" else "flank-up") {
                AnimatedReadout(visibility) {
                  FlankHalf(values, download = isDownload, presentation = presentation)
                }
              }
              .first()
              .measure(loose)
          // Each half hugs the hole: download ends at its slot's end edge, upload starts at
          // its slot's start edge, whatever the live digits measure.
          val x =
            if (isDownload) (rect.right - left).dp.roundToPx() - half.width
            else (rect.left - left).dp.roundToPx()
          Triple(half, x, (rect.top - top).dp.roundToPx())
        }
        val width = (slots.maxOf { it.first.right } - left).dp.roundToPx()
        val height = (slots.maxOf { it.first.bottom } - top).dp.roundToPx()
        placed(width, height) { dx, dy ->
          halves.forEach { (half, x, y) -> half.placeRelative(dx + x, dy + y) }
        }
      }
      is Placement.Ribbon -> {
        origin = WindowOrigin(placement.x, placement.y)
        val content =
          subcompose("ribbon") {
              SlimRibbon(
                values,
                colors = presentation.colors,
                readoutStyle = presentation.readoutStyle,
              )
            }
            .first()
            .measure(loose)
        // The live ribbon keeps the center its widest form reserved on the hole.
        val slotWidth = max(placement.size.width.dp.roundToPx(), content.width)
        val slotHeight = max(placement.size.height.dp.roundToPx(), content.height)
        val x = centeredInSlot(slotWidth, content.width, roomPx(placement.x, geometry.widthDp))
        placed(slotWidth, slotHeight) { dx, dy ->
          content.placeRelative(dx + x, dy + (slotHeight - content.height) / 2)
        }
      }
    }
  }
  val reported: WindowOrigin? = origin
  LaunchedEffect(reported) { if (reported != null) onOrigin(reported) }
  val reportedFit: Boolean? = fits
  LaunchedEffect(reportedFit) { if (reportedFit != null) onFits(reportedFit) }
}

private fun Dp.roundUp(): Int = kotlin.math.ceil(value).roundToInt()

/** The px from a window at [originDp] to the end of a display [displayWidthDp] wide. */
private fun Density.roomPx(originDp: Int, displayWidthDp: Int): Int =
  displayWidthDp.dp.roundToPx() - originDp.dp.roundToPx()

/**
 * The offset that centers live content of [contentWidth] px in a slot of [slotWidth] px when the
 * window has [roomPx] px of display from its start edge to the display's end. A slot wider than
 * that room was clamped to the display's start edge, so the offset stops where the content would
 * leave the display, and content that cannot fit at all sits at the start.
 */
internal fun centeredInSlot(slotWidth: Int, contentWidth: Int, roomPx: Int): Int =
  ((slotWidth - contentWidth) / 2).coerceAtMost((roomPx - contentWidth).coerceAtLeast(0))

private fun placementSignature(placement: Placement): String =
  when (placement) {
    is Placement.Single -> "single ${placement.style} ${placement.alignment} ${placement.fits}"
    is Placement.Flank -> "flank ${placement.style} ${placement.fits}"
    is Placement.Ribbon -> "ribbon ${placement.fits}"
  }
