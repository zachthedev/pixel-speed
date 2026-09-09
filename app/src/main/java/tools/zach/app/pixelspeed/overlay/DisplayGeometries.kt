package tools.zach.app.pixelspeed.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.Display
import android.view.DisplayCutout
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.roundToInt
import tools.zach.app.pixelspeed.log.AppLog

/** A context whose window manager reports the display [context] draws on, not one window of it. */
fun displayContext(context: Context): Context = displayContext(context, context.display)

/** A context whose window manager reports [display] whole; [context] may have no display itself. */
fun displayContext(context: Context, display: Display): Context =
  context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)

/**
 * The display as the placement function sees it, in dp, read fresh from [windowManager]'s current
 * metrics. [context] must be a display context such as [displayContext] returns: only a window
 * context reports the whole display and its rotation, while an activity's window manager reports
 * the activity's own window, which in split screen is neither full height nor under the cutout. The
 * cutout rects are mirrored on an RTL display so the geometry runs in the layout direction.
 */
fun displayGeometry(context: Context, windowManager: WindowManager): DisplayGeometry {
  val density = context.resources.displayMetrics.density
  fun Int.toDp(): Int = pxToDp(this, density)
  val metrics = windowManager.currentWindowMetrics
  val bounds = metrics.bounds
  val insets = metrics.windowInsets
  val statusBar = insets.getInsets(WindowInsets.Type.statusBars())
  val navigationBar = insets.getInsets(WindowInsets.Type.navigationBars())
  // The display holds only its whole dp: a size rounded up puts a clamped window one px past
  // the edge, so the bounds floor while every rect inside them keeps the shared rounding.
  val widthDp = pxToDpFloor(bounds.width(), density)
  val rtl = context.isRtl()
  val cutout = insets.displayCutout
  val glassBounds: RectF? = cutout?.let(::glassBounds)
  val cutoutRects =
    cutout?.boundingRects.orEmpty().map { rect ->
      // One path outlines every cutout, so its bounds name a rect's glass only when they sit
      // inside that rect; any other rect keeps its own top as the glass line.
      val pathInside =
        glassBounds != null && glassBounds.left >= rect.left && glassBounds.right <= rect.right
      val glass: RectF? = if (pathInside) glassBounds else null
      val glassTopPx = glass?.let { maxOf(rect.top, it.top.roundToInt()) } ?: rect.top
      val glassLeftPx = glass?.let { maxOf(rect.left, it.left.roundToInt()) }
      val glassRightPx = glass?.let { minOf(rect.right, it.right.roundToInt()) }
      val physical =
        RectDp(
          left = rect.left.toDp(),
          top = rect.top.toDp(),
          right = rect.right.toDp(),
          bottom = rect.bottom.toDp(),
          glassTop = glassTopPx.toDp(),
          glassLeft = glassLeftPx?.toDp(),
          glassRight = glassRightPx?.toDp(),
        )
      if (rtl) physical.mirrored(widthDp) else physical
    }
  return DisplayGeometry(
    widthDp = widthDp,
    heightDp = pxToDpFloor(bounds.height(), density),
    statusBarDp = statusBar.top.toDp(),
    navigationBarDp = navigationBar.bottom.toDp(),
    cutoutRects = cutoutRects,
    landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    fontScale = context.resources.configuration.fontScale,
    cornerPaddingDp = cornerPaddingDp(roundedCornerContentPadding(context.resources).toDp()),
    rotated =
      when (context.display.rotation) {
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> true
        else -> false
      },
    rtl = rtl,
  )
}

/**
 * The framework's rounded_corner_content_padding for the display [resources] is configured for, in
 * pixels: the system status bar pads its clock and icons by it on panels with large rounded
 * corners. The name is internal to the framework, so a build without it yields zero, and a ROM may
 * define it as any type, so a read that throws yields zero too.
 */
private fun roundedCornerContentPadding(resources: Resources): Int {
  val id = roundedCornerContentPaddingId
  if (id == 0) return 0
  return try {
    resources.getDimensionPixelSize(id)
  } catch (e: RuntimeException) {
    AppLog.w("rounded_corner_content_padding resolved to $id but is not a dimension", e)
    0
  }
}

// The framework keeps this dimen internal, so a name lookup is the only way to find it; the id
// is fixed for the process, so it resolves once rather than on every geometry read.
private val roundedCornerContentPaddingId: Int by lazy { findRoundedCornerContentPaddingId() }

@SuppressLint("DiscouragedApi")
private fun findRoundedCornerContentPaddingId(): Int =
  Resources.getSystem().getIdentifier("rounded_corner_content_padding", "dimen", "android")

/** Whether this context lays out right to left, which is where the status bar keeps its clock. */
fun Context.isRtl(): Boolean = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

/** The platform's value, held to the plausible range: a ROM can define the dimen as anything. */
private fun cornerPaddingDp(paddingDp: Int): Int {
  val clamped = clampCornerPadding(paddingDp)
  // Geometry is read on every drag move, so the warning goes out once per process.
  if (clamped != paddingDp && warnedCornerPadding.compareAndSet(false, true)) {
    AppLog.w("rounded_corner_content_padding of $paddingDp dp is out of range; clamped")
  }
  return clamped
}

/**
 * The bounds of the cutout path, the outline of the missing glass, cached per cutout since the
 * geometry is read on every drag move and the platform hands out one cutout per configuration.
 */
private fun glassBounds(cutout: DisplayCutout): RectF? {
  cachedGlass?.let { (owner, bounds) -> if (owner == cutout) return bounds }
  val path: Path = cutout.cutoutPath ?: return null
  val bounds = RectF()
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    path.computeBounds(bounds)
  } else {
    @Suppress("DEPRECATION") path.computeBounds(bounds, true)
  }
  cachedGlass = cutout to bounds
  return bounds
}

@Volatile private var cachedGlass: Pair<DisplayCutout, RectF>? = null

/** A negative or oversized corner padding becomes the nearest plausible value. */
internal fun clampCornerPadding(paddingDp: Int): Int = paddingDp.coerceIn(0, MAX_CORNER_PADDING_DP)

private val warnedCornerPadding = AtomicBoolean(false)

/** The widest corner padding a shipping panel declares, with room above it. */
internal const val MAX_CORNER_PADDING_DP: Int = 48

/**
 * Pixels to whole dp at [density], rounded to nearest; the one rounding rule the window and the
 * geometry share.
 */
internal fun pxToDp(px: Int, density: Float): Int = (px / density).roundToInt()

/** Pixels to the whole dp that fit inside them at [density]; a display's bounds use this. */
internal fun pxToDpFloor(px: Int, density: Float): Int = floor(px / density).toInt()

/** Whole dp to pixels at [density], rounded to nearest. */
internal fun dpToPx(dp: Int, density: Float): Int = (dp * density).roundToInt()
