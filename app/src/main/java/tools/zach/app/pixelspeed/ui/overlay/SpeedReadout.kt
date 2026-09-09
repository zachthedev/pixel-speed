package tools.zach.app.pixelspeed.ui.overlay

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.DirectionMarker
import tools.zach.app.pixelspeed.settings.DirectionOrder
import tools.zach.app.pixelspeed.settings.FontWeightChoice
import tools.zach.app.pixelspeed.settings.SettingsBounds
import tools.zach.app.pixelspeed.ui.theme.GoogleSansFlex

/**
 * The strings the formatter produced for one tick, number and unit kept apart per direction. The
 * raw rates back the dominant-direction choice for the single-value looks; formatted strings alone
 * cannot be compared across differing units.
 */
data class ReadoutValues(
  val downloadNumber: String,
  val downloadUnit: String,
  val uploadNumber: String,
  val uploadUnit: String,
  val downloadBytesPerSecond: Long,
  val uploadBytesPerSecond: Long,
  val transport: Transport? = null,
  /** The units a screen reader names, present whether or not the look draws a label. */
  val downloadSpokenUnit: String = downloadUnit,
  val uploadSpokenUnit: String = uploadUnit,
  /**
   * The numbers and units the two-line stack and the flank halves draw. Under the Auto label they
   * carry no unit and their number stays in megabits, so it can differ from the climbing number the
   * other looks draw.
   */
  val downloadStackNumber: String = downloadNumber,
  val uploadStackNumber: String = uploadNumber,
  val downloadStackUnit: String = downloadUnit,
  val uploadStackUnit: String = uploadUnit,
  /**
   * True while a sampler waits for its first sample: the looks draw the loading dots where the
   * numbers go and no unit label, and the dots speak the wait.
   */
  val loading: Boolean = false,
)

/**
 * Colors of the pill. The defaults are the platform's own chip roles, fixed across light and dark.
 */
data class ReadoutColors(val fill: Color, val text: Color)

@Composable
fun nativeChipColors(): ReadoutColors =
  ReadoutColors(
    fill = colorResource(android.R.color.system_accent1_600),
    text = colorResource(android.R.color.system_neutral1_50),
  )

/** Geometry every look shares: the native status bar chip. */
object ReadoutDefaults {
  /**
   * Floor, not a fixed height: the pill grows past this to fit taller text at larger font scales.
   * Fitting the look back into the status bar's own height is a separate, placement-level concern.
   */
  val chipHeight: Dp = 24.dp
  val chipVerticalPadding: Dp = 2.dp
  val sidePadding: Dp = 12.dp
  val stackEndPadding: Dp = 8.dp

  /** The air between a stacked line's arrow, digits, and unit. */
  val stackGap: Dp = 1.5.dp

  /**
   * A flank half sits beside the camera hole where the clock and the icons leave the least room, so
   * it is the shortest and tightest pill: 18 dp tall on 11 sp digits with 6 dp of side padding.
   */
  val halfHeight: Dp = 18.dp
  val halfSidePadding: Dp = 6.dp
  val halfTextSize: TextUnit = 11.sp
  val halfGlyphSize: Dp = 11.dp
  val halfGlyphGap: Dp = 3.dp

  /** A leading arrow reads as air, so the stacked looks' start side sits this much tighter. */
  val stackStartInset: Dp = 2.dp

  /** The stacked looks' start padding with no style. */
  val stackStartPadding: Dp = stackEndPadding - stackStartInset
  /** The direction and network glyphs beside 14 sp text; other sizes keep the proportion. */
  val glyphSize: Dp = 16.dp
  /** The air between glyphs and digits stays absolute while both grow with the text. */
  val glyphGap: Dp = 4.dp
  val groupGap: Dp = 6.dp
  val textSize: TextUnit = 14.sp
  val compact12TextSize: TextUnit = 12.sp
  val stackTextSize: TextUnit = 10.sp
  val stackGlyphSize: Dp = 10.dp
  val ribbonHeight: Dp = 14.dp
  val ribbonVerticalPadding: Dp = 1.dp
  val ribbonTextSize: TextUnit = 9.sp
  val ribbonGlyphSize: Dp = 10.dp
  val ribbonSidePadding: Dp = 7.dp
}

/**
 * One direction's figures, kept apart from the other so hiding, reordering, and tinting stay
 * simple.
 */
private data class DirectionHalf(
  val isDownload: Boolean,
  val number: String,
  val unit: String,
  val loading: Boolean = false,
  val stackNumber: String = number,
  val stackUnit: String = unit,
)

private fun weightFor(choice: FontWeightChoice): FontWeight =
  when (choice) {
    FontWeightChoice.REGULAR -> FontWeight.Normal
    FontWeightChoice.MEDIUM -> FontWeight.Medium
    FontWeightChoice.BOLD -> FontWeight.Bold
  }

/** The status bar glyph for a transport and the name a screen reader gives it. */
private data class NetworkGlyph(val drawableRes: Int, val labelRes: Int)

private fun transportGlyph(transport: Transport?): NetworkGlyph? =
  when (transport) {
    Transport.WIFI -> NetworkGlyph(R.drawable.ic_wifi, R.string.overlay_transport_wifi)
    Transport.CELLULAR -> NetworkGlyph(R.drawable.ic_cellular, R.string.overlay_transport_cellular)
    Transport.ETHERNET -> NetworkGlyph(R.drawable.ic_ethernet, R.string.overlay_transport_ethernet)
    Transport.VPN -> NetworkGlyph(R.drawable.ic_vpn, R.string.overlay_transport_vpn)
    Transport.OTHER,
    Transport.NONE,
    null -> null
  }

/**
 * A pill never renders with no direction visible; a caller that hides both still gets the download.
 */
private fun orderedHalves(
  values: ReadoutValues,
  showDownload: Boolean,
  showUpload: Boolean,
  directionOrder: DirectionOrder,
): List<DirectionHalf> {
  val download =
    DirectionHalf(
      isDownload = true,
      number = values.downloadNumber,
      unit = values.downloadUnit,
      loading = values.loading,
      stackNumber = values.downloadStackNumber,
      stackUnit = values.downloadStackUnit,
    )
  val upload =
    DirectionHalf(
      isDownload = false,
      number = values.uploadNumber,
      unit = values.uploadUnit,
      loading = values.loading,
      stackNumber = values.uploadStackNumber,
      stackUnit = values.uploadStackUnit,
    )
  val halves =
    when (directionOrder) {
      DirectionOrder.DOWNLOAD_FIRST ->
        listOfNotNull(download.takeIf { showDownload }, upload.takeIf { showUpload })
      DirectionOrder.UPLOAD_FIRST ->
        listOfNotNull(upload.takeIf { showUpload }, download.takeIf { showDownload })
    }
  return halves.ifEmpty { listOf(download) }
}

private fun pillShape(cornerRadiusDp: Int): Shape =
  if (cornerRadiusDp >= SettingsBounds.CORNER_RADIUS_DP.last) RoundedCornerShape(percent = 50)
  else RoundedCornerShape(cornerRadiusDp.dp)

/** The glyph beside a single value: both arrows for a combined total, else the direction shown. */
private fun singleGlyph(combined: Boolean, downloadDominant: Boolean): Int =
  when {
    combined -> R.drawable.ic_swap_vert
    downloadDominant -> R.drawable.ic_arrow_downward
    else -> R.drawable.ic_arrow_upward
  }

/** True when download is the larger of the two rates. A tie favors download. */
fun isDownloadDominant(downloadBytesPerSecond: Long, uploadBytesPerSecond: Long): Boolean =
  downloadBytesPerSecond >= uploadBytesPerSecond

/**
 * The direction a single-value look shows: the only visible one when the layout hides the other,
 * else the dominant one.
 */
fun singleDirectionIsDownload(
  showDownload: Boolean,
  showUpload: Boolean,
  downloadBytesPerSecond: Long,
  uploadBytesPerSecond: Long,
): Boolean =
  when {
    !showUpload -> true
    !showDownload -> false
    else -> isDownloadDominant(downloadBytesPerSecond, uploadBytesPerSecond)
  }

/**
 * One readout in the requested look. Pure presentation; the caller supplies formatted strings.
 * [readoutStyle] carries the appearance settings; omitting it keeps the platform's own chip look.
 */
@Composable
fun SpeedReadout(
  values: ReadoutValues,
  style: OverlayStyle,
  modifier: Modifier = Modifier,
  colors: ReadoutColors = nativeChipColors(),
  textSize: TextUnit = ReadoutDefaults.textSize,
  sidePadding: Dp = ReadoutDefaults.sidePadding,
  readoutStyle: ReadoutStyle? = null,
  showDownload: Boolean = true,
  showUpload: Boolean = true,
  directionOrder: DirectionOrder = DirectionOrder.DOWNLOAD_FIRST,
  directionMarker: DirectionMarker = DirectionMarker.ARROWS,
  combined: Boolean = false,
  showNetworkGlyph: Boolean = false,
) {
  val fill = readoutStyle?.fill?.let { Color(it) } ?: colors.fill
  val text = readoutStyle?.text?.let { Color(it) } ?: colors.text
  val effectiveSidePadding = readoutStyle?.sidePaddingDp?.dp ?: sidePadding
  val effectiveMinWidth = readoutStyle?.minWidthDp?.dp ?: 0.dp
  val shape = readoutStyle?.let { pillShape(it.cornerRadiusDp) } ?: RoundedCornerShape(percent = 50)
  val fontFamily = if (readoutStyle?.monospace == true) FontFamily.Monospace else GoogleSansFlex
  val fontWeight = readoutStyle?.let { weightFor(it.weight) } ?: FontWeight.Medium
  val downloadTint = readoutStyle?.downloadTint?.let { Color(it) }
  val uploadTint = readoutStyle?.uploadTint?.let { Color(it) }
  val networkGlyph = if (showNetworkGlyph) transportGlyph(values.transport) else null
  // Only a leading arrow reads as air; a letter, a network glyph, or bare digits keep the
  // configured padding on both sides.
  val stackEndPadding = readoutStyle?.sidePaddingDp?.dp ?: ReadoutDefaults.stackEndPadding
  val stackLeadsWithArrow = directionMarker == DirectionMarker.ARROWS && networkGlyph == null
  val stackStartPadding =
    if (stackLeadsWithArrow) (stackEndPadding - ReadoutDefaults.stackStartInset).coerceAtLeast(0.dp)
    else stackEndPadding
  val shown = orderedHalves(values, showDownload, showUpload, directionOrder)

  fun tintFor(half: DirectionHalf): Color? = if (half.isDownload) downloadTint else uploadTint

  when (style) {
    OverlayStyle.CHIP,
    OverlayStyle.GLYPHS ->
      Pill(fill, shape, modifier, effectiveSidePadding, effectiveMinWidth) {
        NetworkGlyphPrefix(networkGlyph, text, glyphFor(textSize))
        shown.forEachIndexed { index, half ->
          if (index > 0) Gap(ReadoutDefaults.groupGap)
          HalfGroup(
            half,
            directionMarker,
            textSize,
            glyphFor(textSize),
            text,
            tintFor(half),
            fontFamily,
            fontWeight,
            spoken = index == 0,
          )
        }
        if (!values.loading) {
          Gap(ReadoutDefaults.glyphGap)
          Figures(shown.last().unit, textSize, text, fontFamily, fontWeight)
        }
      }
    OverlayStyle.COMPACT ->
      Pill(fill, shape, modifier, effectiveSidePadding, effectiveMinWidth) {
        NetworkGlyphPrefix(networkGlyph, text, glyphFor(textSize))
        shown.forEachIndexed { index, half ->
          if (index > 0) Gap(ReadoutDefaults.groupGap)
          HalfGroup(
            half,
            directionMarker,
            textSize,
            glyphFor(textSize),
            text,
            tintFor(half),
            fontFamily,
            fontWeight,
            spoken = index == 0,
          )
        }
      }
    OverlayStyle.COMPACT12 ->
      Pill(fill, shape, modifier, effectiveSidePadding, effectiveMinWidth) {
        val size = ReadoutDefaults.compact12TextSize
        val glyph = compact12Glyph()
        NetworkGlyphPrefix(networkGlyph, text, glyph)
        shown.forEachIndexed { index, half ->
          if (index > 0) Gap(ReadoutDefaults.groupGap)
          HalfGroup(
            half,
            directionMarker,
            size,
            glyph,
            text,
            tintFor(half),
            fontFamily,
            fontWeight,
            spoken = index == 0,
          )
        }
      }
    OverlayStyle.STACK ->
      Pill(
        fill,
        shape,
        modifier,
        startPadding = stackStartPadding,
        minWidth = effectiveMinWidth,
        endPadding = stackEndPadding,
      ) {
        NetworkGlyphPrefix(networkGlyph, text, stackGlyph())
        Column(horizontalAlignment = Alignment.End) {
          shown.forEachIndexed { index, half ->
            StackLine(
              half,
              half.stackUnit,
              directionMarker,
              tintFor(half) ?: text,
              fontFamily,
              fontWeight,
              spoken = index == 0,
            )
          }
        }
      }
    OverlayStyle.STACK_NO_UNIT ->
      Pill(
        fill,
        shape,
        modifier,
        startPadding = stackStartPadding,
        minWidth = effectiveMinWidth,
        endPadding = stackEndPadding,
      ) {
        NetworkGlyphPrefix(networkGlyph, text, stackGlyph())
        Column(horizontalAlignment = Alignment.End) {
          shown.forEachIndexed { index, half ->
            StackLine(
              half,
              "",
              directionMarker,
              tintFor(half) ?: text,
              fontFamily,
              fontWeight,
              spoken = index == 0,
            )
          }
        }
      }
    OverlayStyle.SINGLE -> {
      val downloadDominant =
        singleDirectionIsDownload(
          showDownload,
          showUpload,
          values.downloadBytesPerSecond,
          values.uploadBytesPerSecond,
        )
      val number = if (downloadDominant) values.downloadNumber else values.uploadNumber
      val unit = if (downloadDominant) values.downloadUnit else values.uploadUnit
      Pill(fill, shape, modifier, effectiveSidePadding, effectiveMinWidth) {
        NetworkGlyphPrefix(networkGlyph, text, glyphFor(textSize))
        Glyph(singleGlyph(combined, downloadDominant), text, glyphFor(textSize))
        Gap(ReadoutDefaults.glyphGap)
        Reading(number, values.loading, textSize, text, fontFamily, fontWeight)
        if (!values.loading) {
          Gap(ReadoutDefaults.glyphGap)
          Figures(unit, textSize, text, fontFamily, fontWeight)
        }
      }
    }
    OverlayStyle.SINGLE_NO_UNIT -> {
      val downloadDominant =
        singleDirectionIsDownload(
          showDownload,
          showUpload,
          values.downloadBytesPerSecond,
          values.uploadBytesPerSecond,
        )
      val number = if (downloadDominant) values.downloadNumber else values.uploadNumber
      Pill(fill, shape, modifier, effectiveSidePadding, effectiveMinWidth) {
        NetworkGlyphPrefix(networkGlyph, text, glyphFor(textSize))
        Glyph(singleGlyph(combined, downloadDominant), text, glyphFor(textSize))
        Gap(ReadoutDefaults.glyphGap)
        Reading(number, values.loading, textSize, text, fontFamily, fontWeight)
      }
    }
    OverlayStyle.HALO ->
      Row(
        modifier =
          modifier.heightIn(min = ReadoutDefaults.chipHeight).widthIn(min = effectiveMinWidth),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        NetworkGlyphPrefix(networkGlyph, text, glyphFor(textSize))
        shown.forEachIndexed { index, half ->
          if (index > 0) Gap(ReadoutDefaults.groupGap)
          HalfGroup(
            half,
            directionMarker,
            textSize,
            glyphFor(textSize),
            text,
            tintFor(half),
            fontFamily,
            fontWeight,
            halo = true,
            spoken = index == 0,
          )
        }
        if (!values.loading) {
          Gap(ReadoutDefaults.glyphGap)
          Figures(shown.last().unit, textSize, text, fontFamily, fontWeight, halo = true)
        }
      }
  }
}

/**
 * One pill 14 dp tall in the strip above the cutout: down glyph and number, up glyph and number,
 * unit.
 */
@Composable
fun SlimRibbon(
  values: ReadoutValues,
  modifier: Modifier = Modifier,
  colors: ReadoutColors = nativeChipColors(),
  readoutStyle: ReadoutStyle? = null,
) {
  val fill = readoutStyle?.fill?.let { Color(it) } ?: colors.fill
  val text = readoutStyle?.text?.let { Color(it) } ?: colors.text
  Row(
    modifier =
      modifier
        .heightIn(min = ReadoutDefaults.ribbonHeight)
        .background(fill, RoundedCornerShape(percent = 50))
        .padding(
          horizontal = ReadoutDefaults.ribbonSidePadding,
          vertical = ReadoutDefaults.ribbonVerticalPadding,
        ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Glyph(R.drawable.ic_arrow_downward, text, ribbonGlyph())
    Gap(2.dp)
    Reading(values.downloadNumber, values.loading, ReadoutDefaults.ribbonTextSize, text)
    Gap(ReadoutDefaults.groupGap)
    Glyph(R.drawable.ic_arrow_upward, text, ribbonGlyph())
    Gap(2.dp)
    Reading(
      values.uploadNumber,
      values.loading,
      ReadoutDefaults.ribbonTextSize,
      text,
      spoken = false,
    )
    if (!values.loading) {
      Gap(2.dp)
      Figures(values.uploadUnit, ReadoutDefaults.ribbonTextSize, text)
    }
  }
}

/**
 * One half of a cutout flank: the direction's marker and number at [ReadoutDefaults.halfTextSize],
 * then the unit the stack and the halves draw, if any. A half must fit between the clock and the
 * hole and between the hole and the icons at once, so it keeps its own size, the smallest of every
 * look: the half text, the half padding, no minimum width, and no network glyph. The colors, the
 * direction tints, the corner shape, the typeface, the weight, and the marker come from the
 * presentation the way they do for the whole-pill looks. The half is one spoken node naming its
 * direction and reading, whatever marker is drawn, since two bare halves differ only by position.
 */
@Composable
fun FlankHalf(
  values: ReadoutValues,
  download: Boolean,
  presentation: ReadoutPresentation,
  modifier: Modifier = Modifier,
) {
  val style = presentation.readoutStyle
  val fill = style.fill?.let { Color(it) } ?: presentation.colors.fill
  val text = style.text?.let { Color(it) } ?: presentation.colors.text
  val tint = (if (download) style.downloadTint else style.uploadTint)?.let { Color(it) }
  val fontFamily = if (style.monospace) FontFamily.Monospace else GoogleSansFlex
  val half =
    if (download) {
      DirectionHalf(
        isDownload = true,
        values.downloadStackNumber,
        values.downloadStackUnit,
        values.loading,
      )
    } else {
      DirectionHalf(
        isDownload = false,
        values.uploadStackNumber,
        values.uploadStackUnit,
        values.loading,
      )
    }
  val direction =
    stringResource(
      when {
        presentation.combined -> R.string.content_description_combined
        download -> R.string.content_description_download
        else -> R.string.content_description_upload
      }
    )
  val unit = if (download) values.downloadSpokenUnit else values.uploadSpokenUnit
  val spoken =
    when {
      half.loading -> stringResource(R.string.overlay_half_loading, direction)
      half.number == SpeedFormatter.UNSUPPORTED_DASH ->
        stringResource(R.string.overlay_half_no_reading, direction)
      else -> stringResource(R.string.overlay_half_reading, direction, half.number, unit)
    }
  val described = modifier.clearAndSetSemantics { contentDescription = spoken }
  val fontWeight = weightFor(style.weight)
  Pill(
    fill,
    pillShape(style.cornerRadiusDp),
    described,
    ReadoutDefaults.halfSidePadding,
    0.dp,
    minHeight = ReadoutDefaults.halfHeight,
  ) {
    HalfGroup(
      half,
      presentation.directionMarker,
      ReadoutDefaults.halfTextSize,
      halfGlyph(),
      text,
      tint,
      fontFamily,
      fontWeight,
      glyphGap = ReadoutDefaults.halfGlyphGap,
    )
    if (half.unit.isNotEmpty() && !half.loading) {
      Gap(ReadoutDefaults.halfGlyphGap)
      Figures(half.unit, ReadoutDefaults.halfTextSize, tint ?: text, fontFamily, fontWeight)
    }
  }
}

@Composable
private fun Pill(
  fill: Color,
  shape: Shape,
  modifier: Modifier,
  startPadding: Dp,
  minWidth: Dp,
  endPadding: Dp = startPadding,
  minHeight: Dp = ReadoutDefaults.chipHeight,
  content: @Composable () -> Unit,
) {
  Row(
    modifier =
      modifier
        .heightIn(min = minHeight)
        .widthIn(min = minWidth)
        .background(fill, shape)
        .padding(
          start = startPadding,
          end = endPadding,
          top = ReadoutDefaults.chipVerticalPadding,
          bottom = ReadoutDefaults.chipVerticalPadding,
        ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content()
  }
}

@Composable
private fun NetworkGlyphPrefix(glyph: NetworkGlyph?, tint: Color, size: Dp) {
  if (glyph != null) {
    Icon(
      painter = painterResource(glyph.drawableRes),
      contentDescription = stringResource(glyph.labelRes),
      tint = tint,
      modifier = Modifier.size(size),
    )
    Gap(ReadoutDefaults.glyphGap)
  }
}

/**
 * The glyph size that keeps [base] beside [baseText] in proportion to text of [textSize], so a
 * larger text setting or font scale grows the arrows with the digits.
 */
@Composable
private fun glyphFor(
  textSize: TextUnit,
  base: Dp = ReadoutDefaults.glyphSize,
  baseText: TextUnit = ReadoutDefaults.textSize,
): Dp = with(LocalDensity.current) { textSize.toDp() } * (base.value / baseText.value)

/** The 12 sp look keeps the 14 sp glyph, so it scales from that pairing. */
@Composable
private fun compact12Glyph(): Dp =
  glyphFor(
    ReadoutDefaults.compact12TextSize,
    ReadoutDefaults.glyphSize,
    ReadoutDefaults.compact12TextSize,
  )

@Composable
private fun stackGlyph(): Dp =
  glyphFor(
    ReadoutDefaults.stackTextSize,
    ReadoutDefaults.stackGlyphSize,
    ReadoutDefaults.stackTextSize,
  )

@Composable
private fun halfGlyph(): Dp =
  glyphFor(
    ReadoutDefaults.halfTextSize,
    ReadoutDefaults.halfGlyphSize,
    ReadoutDefaults.halfTextSize,
  )

@Composable
private fun ribbonGlyph(): Dp =
  glyphFor(
    ReadoutDefaults.ribbonTextSize,
    ReadoutDefaults.ribbonGlyphSize,
    ReadoutDefaults.ribbonTextSize,
  )

@Composable
private fun HalfGroup(
  half: DirectionHalf,
  marker: DirectionMarker,
  textSize: TextUnit,
  glyphSize: Dp,
  color: Color,
  tint: Color?,
  fontFamily: FontFamily,
  fontWeight: FontWeight,
  halo: Boolean = false,
  spoken: Boolean = true,
  glyphGap: Dp = ReadoutDefaults.glyphGap,
) {
  val figureColor = tint ?: color
  if (marker != DirectionMarker.NONE) {
    Marker(half, marker, figureColor, glyphSize, textSize, fontFamily, fontWeight)
    Gap(glyphGap)
  }
  Reading(half.number, half.loading, textSize, figureColor, fontFamily, fontWeight, halo, spoken)
}

@Composable
private fun Marker(
  half: DirectionHalf,
  marker: DirectionMarker,
  color: Color,
  glyphSize: Dp,
  textSize: TextUnit,
  fontFamily: FontFamily,
  fontWeight: FontWeight,
) {
  val directionName =
    stringResource(
      if (half.isDownload) R.string.content_description_download
      else R.string.content_description_upload
    )
  when (marker) {
    DirectionMarker.ARROWS ->
      Glyph(
        if (half.isDownload) R.drawable.ic_arrow_downward else R.drawable.ic_arrow_upward,
        color,
        glyphSize,
      )
    DirectionMarker.LETTERS ->
      Text(
        text = if (half.isDownload) "D" else "U",
        // A bare letter reads as a letter; the direction name is what a screen reader needs.
        modifier = Modifier.semantics { contentDescription = directionName },
        color = color,
        style =
          TextStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = textSize,
            lineHeight = textSize,
          ),
      )
    DirectionMarker.NONE -> Unit
  }
}

@Composable
private fun StackLine(
  half: DirectionHalf,
  unit: String,
  marker: DirectionMarker,
  color: Color,
  fontFamily: FontFamily,
  fontWeight: FontWeight,
  spoken: Boolean = true,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (marker != DirectionMarker.NONE) {
      Marker(
        half,
        marker,
        color,
        stackGlyph(),
        ReadoutDefaults.stackTextSize,
        fontFamily,
        fontWeight,
      )
      Gap(ReadoutDefaults.stackGap)
    }
    Reading(
      half.stackNumber,
      half.loading,
      ReadoutDefaults.stackTextSize,
      color,
      fontFamily,
      fontWeight,
      spoken = spoken,
    )
    if (unit.isNotEmpty() && !half.loading) {
      Gap(ReadoutDefaults.stackGap)
      Figures(unit, ReadoutDefaults.stackTextSize, color, fontFamily, fontWeight)
    }
  }
}

@Composable
private fun Glyph(glyph: Int, tint: Color, size: Dp = ReadoutDefaults.glyphSize) {
  val description =
    when (glyph) {
      R.drawable.ic_arrow_downward -> stringResource(R.string.content_description_download)
      R.drawable.ic_arrow_upward -> stringResource(R.string.content_description_upload)
      else -> stringResource(R.string.content_description_combined)
    }
  Icon(
    painter = painterResource(glyph),
    contentDescription = description,
    tint = tint,
    modifier = Modifier.size(size),
  )
}

/**
 * The number, or the loading dots before the first sample; the first digits crossfade in over the
 * motion scheme's effects duration, and snap in with motion off. A pill with two readings speaks
 * the wait once, from the reading marked [spoken].
 */
@Composable
private fun Reading(
  number: String,
  loading: Boolean,
  textSize: TextUnit,
  color: Color,
  fontFamily: FontFamily = GoogleSansFlex,
  fontWeight: FontWeight = FontWeight.Medium,
  halo: Boolean = false,
  spoken: Boolean = true,
) {
  val spec: FiniteAnimationSpec<Float> =
    if (LocalReadoutMotion.current) MaterialTheme.motionScheme.defaultEffectsSpec() else snap()
  val density = LocalDensity.current
  val dotSize: Dp = with(density) { (textSize * DOT_SCALE).toDp() }
  // The dots take the line box a digit measures in the figures' own style, ascent and descent
  // included. The crossfade box keeps its height, so nothing moves vertically when the digits
  // land. The slot around it holds the width.
  val measurer = rememberTextMeasurer()
  val style = figureStyle(textSize, fontFamily, fontWeight, halo = false)
  val lineHeight: Dp =
    remember(measurer, style, density) {
      with(density) { measurer.measure("8", style).size.height.toDp() }
    }
  Crossfade(targetState = loading, animationSpec = spec, label = "reading") { waiting ->
    if (waiting) LoadingDots(color, dotSize, lineHeight, spoken = spoken)
    else Figures(number, textSize, color, fontFamily, fontWeight, halo)
  }
}

/** The figures' style: tabular digits at the size, with an optional halo. */
@Composable
private fun figureStyle(
  textSize: TextUnit,
  fontFamily: FontFamily,
  fontWeight: FontWeight,
  halo: Boolean,
): TextStyle {
  val haloBlurRadiusPx = with(LocalDensity.current) { 1.dp.toPx() }
  return TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = textSize,
    lineHeight = textSize,
    fontFeatureSettings = "tnum",
    shadow =
      if (halo) Shadow(color = Color.Black.copy(alpha = 0.9f), blurRadius = haloBlurRadiusPx)
      else null,
  )
}

/** A loading dot's size against the text it stands in for. */
private const val DOT_SCALE: Float = 0.55f

/** Tabular figures so digits never shift the pill between ticks. */
@Composable
private fun Figures(
  text: String,
  textSize: TextUnit,
  color: Color,
  fontFamily: FontFamily = GoogleSansFlex,
  fontWeight: FontWeight = FontWeight.Medium,
  halo: Boolean = false,
) {
  Text(
    text = text,
    color = color,
    maxLines = 1,
    softWrap = false,
    textAlign = TextAlign.End,
    style = figureStyle(textSize, fontFamily, fontWeight, halo),
  )
}

@Composable
private fun Gap(width: Dp) {
  androidx.compose.foundation.layout.Spacer(Modifier.width(width))
}
