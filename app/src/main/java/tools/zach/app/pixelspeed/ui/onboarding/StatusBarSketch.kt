package tools.zach.app.pixelspeed.ui.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.overlay.OverlayPlacement
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.ui.overlay.LocalReadoutMotion
import tools.zach.app.pixelspeed.ui.overlay.OverlayContent
import tools.zach.app.pixelspeed.ui.overlay.rememberOverlayComposition
import tools.zach.app.pixelspeed.ui.rememberHour24

/**
 * A drawn copy of this phone's status bar, with the real overlay composition placed on it by the
 * real placement rule, so the page shows where the readout actually lands rather than describing
 * it. [measuredBar] stands in for the icons a real bar would report once the accessibility service
 * reads them, so a page can show what a busier bar does to the placement before the service is on.
 * Laid out at the display's own size, then scaled as one graphics layer to the width the caller
 * gives it, so the drawn bar and the real one share every measurement the placement rule reads.
 */
@Composable
fun StatusBarSketch(
  geometry: DisplayGeometry,
  profile: Profile,
  notificationIcons: Int,
  contentDescription: String,
  modifier: Modifier = Modifier,
  measuredBar: MeasuredBar? = null,
) {
  BoxWithConstraints(
    modifier
      .clip(RoundedCornerShape(SKETCH_CORNER_RADIUS))
      .background(STATUS_BAR_COLOR)
      .clearAndSetSemantics { this.contentDescription = contentDescription }
  ) {
    val scale = maxWidth / geometry.widthDp.dp
    ScaledLayer(scale) {
      StatusBarSketchContent(
        geometry = geometry.copy(measuredBar = measuredBar),
        profile = profile,
        notificationIcons = notificationIcons,
      )
    }
  }
}

/**
 * Measures [content] at its own size, then places it as one graphics layer scaled by [scale], so
 * the reported layout size is the scaled size while everything inside still measures in real dp.
 */
@Composable
private fun ScaledLayer(scale: Float, content: @Composable () -> Unit) {
  Layout(content = content) { measurables, _ ->
    val placeable = measurables.first().measure(Constraints())
    val width = (placeable.width * scale).roundToInt()
    val height = (placeable.height * scale).roundToInt()
    layout(width, height) {
      placeable.placeWithLayer(0, 0) {
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0f)
      }
    }
  }
}

@Composable
private fun StatusBarSketchContent(
  geometry: DisplayGeometry,
  profile: Profile,
  notificationIcons: Int,
) {
  // A fixed time in the phone's own clock format, so the drawing is the same on every render.
  val hour24: Boolean = rememberHour24()
  val locale: Locale = LocalLocale.current.platformLocale
  val clockText: String = remember(locale, hour24) { sketchClockText(locale, hour24) }
  val composition =
    rememberOverlayComposition(profile, SKETCH_SAMPLE, promotedChipAvailable = false)
  Box(Modifier.width(geometry.widthDp.dp).height(geometry.statusBarDp.dp)) {
    Row(
      modifier =
        Modifier.align(Alignment.CenterStart)
          .padding(start = OverlayPlacement.barStartInsetDp(geometry).dp),
      horizontalArrangement = Arrangement.spacedBy(SKETCH_ICON_GAP),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ClockText(clockText)
      repeat(notificationIcons) { index -> NotificationGlyph(circle = index % 2 == 1) }
    }
    centeredTopHole(geometry.cutoutRects, geometry.widthDp)?.let { hole ->
      // The platform's rect boxes the glass with a margin; the hole itself is round, no wider than
      // the glass where the path names it (else the rect), no taller than the glass, and starts
      // at the glass line. A faint ring keeps the black glass visible on the bar's near-black
      // ground.
      val glassLeft = hole.glassLeft ?: hole.left
      val glassRight = hole.glassRight ?: hole.right
      val diameter = minOf(glassRight - glassLeft, hole.bottom - hole.glassTop)
      val left = glassLeft + (glassRight - glassLeft - diameter) / 2
      Box(
        Modifier.offset { IntOffset(left.dp.roundToPx(), hole.glassTop.dp.roundToPx()) }
          .size(diameter.dp)
          .clip(CircleShape)
          .background(Color.Black)
          .border(SKETCH_HOLE_RING, Color.White.copy(alpha = SKETCH_HOLE_RING_ALPHA), CircleShape)
      )
    }
    Row(
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .padding(end = OverlayPlacement.barEndInsetDp(geometry).dp),
      horizontalArrangement = Arrangement.spacedBy(SKETCH_ICON_GAP),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      NotificationGlyph(circle = false)
      SignalBars()
      BatteryPill()
    }
    CompositionLocalProvider(LocalReadoutMotion provides false) {
      OverlayContent(
        values = composition.values,
        measureValues = composition.measureValues,
        request = composition.request,
        geometry = geometry,
        presentation = composition.presentation,
        onOrigin = {},
        onFits = {},
        placeAtOrigin = true,
      )
    }
  }
}

@Composable
private fun ClockText(text: String) {
  Text(text, color = Color.White, fontSize = SKETCH_CLOCK_SIZE, fontWeight = FontWeight.Medium)
}

@Composable
private fun NotificationGlyph(circle: Boolean) {
  Box(
    Modifier.size(SKETCH_GLYPH_SIZE)
      .clip(if (circle) CircleShape else RoundedCornerShape(SKETCH_GLYPH_CORNER))
      .background(Color.White)
  )
}

@Composable
private fun SignalBars() {
  Canvas(Modifier.size(SKETCH_SIGNAL_WIDTH, SKETCH_GLYPH_SIZE)) {
    val barCount = 4
    val barWidth = size.width / (barCount * 2 - 1)
    repeat(barCount) { index ->
      val barHeight = size.height * (index + 1) / barCount
      drawRect(
        color = Color.White,
        topLeft = Offset(index * barWidth * 2, size.height - barHeight),
        size = Size(barWidth, barHeight),
      )
    }
  }
}

@Composable
private fun BatteryPill() {
  Box(
    Modifier.size(SKETCH_BATTERY_WIDTH, SKETCH_GLYPH_SIZE)
      .clip(RoundedCornerShape(percent = 50))
      .background(SKETCH_BATTERY_COLOR)
  )
}

/** The width of the drawn end icons with their gaps, the room a sketch keeps at the bar's end. */
internal fun sketchEndIconsDp(): Int =
  (SKETCH_GLYPH_SIZE + SKETCH_SIGNAL_WIDTH + SKETCH_BATTERY_WIDTH + SKETCH_ICON_GAP * 2)
    .value
    .roundToInt()

/**
 * Ten past ten in the phone's clock format with no day period marker. The status bar's own clock
 * omits the marker, and the placement's clock estimate assumes that width, so the drawn clock omits
 * it the same way. A locale pattern the formatter refuses, or one that is only a marker, gives way
 * to a plain hour and minute, which keeps the same width. [bestPattern] is the locale's pattern
 * source for a skeleton; the JVM tests pass their own.
 */
internal fun sketchClockText(
  locale: Locale,
  hour24: Boolean,
  bestPattern: (Locale, String) -> String = DateFormat::getBestDateTimePattern,
): String {
  val plain = if (hour24) "H:mm" else "h:mm"
  val pattern = withoutDayPeriod(bestPattern(locale, if (hour24) "Hm" else "hm")).ifBlank { plain }
  val time = sketchClockTime()
  return try {
    SimpleDateFormat(pattern, locale).format(time)
  } catch (e: IllegalArgumentException) {
    SimpleDateFormat(plain, locale).format(time)
  }
}

/**
 * [pattern] with every day period letter (`a`, `b`, `B`) outside quotes removed, together with the
 * spacing around it, so `h:mm a` becomes `h:mm` and `ah:mm` becomes `h:mm`. Spacing is any Unicode
 * space separator (CLDR joins the marker with a narrow no-break space) or format control (a bidi
 * mark). Quoted text stays as it is.
 */
internal fun withoutDayPeriod(pattern: String): String {
  val out = StringBuilder()
  var quoted = false
  for (char in pattern) {
    if (char == '\'') quoted = !quoted
    if (!quoted && char in DAY_PERIOD_LETTERS) {
      while (out.isNotEmpty() && isSpacing(out.last())) out.setLength(out.length - 1)
      continue
    }
    if (!quoted && isSpacing(char) && (out.isEmpty() || isSpacing(out.last()))) continue
    out.append(char)
  }
  while (out.isNotEmpty() && isSpacing(out.last())) out.setLength(out.length - 1)
  return out.toString()
}

private fun isSpacing(char: Char): Boolean {
  val type: Int = Character.getType(char)
  return type == Character.SPACE_SEPARATOR.toInt() || type == Character.FORMAT.toInt()
}

private const val DAY_PERIOD_LETTERS: String = "abB"

/** Ten past ten, the hour every drawn clock shows. */
private fun sketchClockTime(): Date =
  Calendar.getInstance()
    .apply {
      set(Calendar.HOUR_OF_DAY, SKETCH_CLOCK_HOUR)
      set(Calendar.MINUTE, SKETCH_CLOCK_MINUTE)
      set(Calendar.SECOND, 0)
    }
    .time

private const val SKETCH_CLOCK_HOUR: Int = 10
private const val SKETCH_CLOCK_MINUTE: Int = 10

/** A representative rate pair for the drawn readout, not a live reading. */
private val SKETCH_SAMPLE: SpeedSample =
  SpeedSample(
    rxBytesPerSecond = 6_475_000,
    txBytesPerSecond = 3_687_000,
    atElapsedNanos = 0,
    transport = Transport.WIFI,
  )

private val SKETCH_CORNER_RADIUS: Dp = 20.dp
private val SKETCH_ICON_GAP: Dp = 8.dp
private val SKETCH_CLOCK_SIZE: TextUnit = 14.sp
private val SKETCH_GLYPH_SIZE: Dp = 14.dp
private val SKETCH_GLYPH_CORNER: Dp = 4.dp
private val SKETCH_SIGNAL_WIDTH: Dp = 16.dp
private val SKETCH_BATTERY_WIDTH: Dp = 22.dp
private val SKETCH_HOLE_RING: Dp = 1.dp
private const val SKETCH_HOLE_RING_ALPHA: Float = 0.4f
private val STATUS_BAR_COLOR: Color = Color(0xFF14151C)
private val SKETCH_BATTERY_COLOR: Color = Color(0xFF4CAF50)
