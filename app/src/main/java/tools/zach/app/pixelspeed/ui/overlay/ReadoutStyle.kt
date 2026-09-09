package tools.zach.app.pixelspeed.ui.overlay

import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.settings.AppearanceSettings
import tools.zach.app.pixelspeed.settings.ColorMode
import tools.zach.app.pixelspeed.settings.FontFamilyChoice
import tools.zach.app.pixelspeed.settings.FontWeightChoice
import tools.zach.app.pixelspeed.settings.PositionSettings

/** The ARGB colors and geometry the appearance settings resolve to for one tick. */
data class ReadoutStyle(
  val fill: Int?,
  val text: Int?,
  val cornerRadiusDp: Int,
  val sidePaddingDp: Int,
  val minWidthDp: Int,
  val monospace: Boolean,
  val weight: FontWeightChoice,
  val downloadTint: Int?,
  val uploadTint: Int?,
  val highlighted: Boolean,
)

private const val WHITE: Int = 0xFFFFFFFF.toInt()
private const val BLACK: Int = 0xFF000000.toInt()
private const val MONOCHROME_LIGHT_FILL: Int = WHITE
private const val MONOCHROME_LIGHT_TEXT: Int = BLACK
private const val MONOCHROME_DARK_FILL: Int = BLACK
private const val MONOCHROME_DARK_TEXT: Int = WHITE
private const val DOWNLOAD_TINT: Int = 0xFF4CAF50.toInt()
private const val UPLOAD_TINT: Int = 0xFF2196F3.toInt()
// Darker variants for bright fills; each tint is chosen by measured contrast against the fill,
// then pushed toward the light or dark shade of its own hue until it reads. The light poles are
// luminous enough to clear AA on every tone-40 accent fill and far enough apart to stay two
// colors there (CIE76 distance above 30).
private const val DOWNLOAD_TINT_ON_LIGHT: Int = 0xFF2E7D32.toInt()
private const val UPLOAD_TINT_ON_LIGHT: Int = 0xFF1565C0.toInt()
private const val HIGHLIGHT_TEXT_ON_LIGHT: Int = 0xFF9A5B00.toInt()
private const val DOWNLOAD_LIGHT_POLE: Int = 0xFFD6F5D0.toInt()
private const val DOWNLOAD_DARK_POLE: Int = 0xFF1B5E20.toInt()
private const val UPLOAD_LIGHT_POLE: Int = 0xFFD6E4FF.toInt()
private const val UPLOAD_DARK_POLE: Int = 0xFF0D47A1.toInt()
private const val HIGHLIGHT_LIGHT_POLE: Int = 0xFFFFECB3.toInt()
private const val HIGHLIGHT_DARK_POLE: Int = 0xFF6D4300.toInt()

/**
 * Threshold-highlight text color. A literal, not a `MaterialTheme.colorScheme` role: the overlay
 * composes outside the app's themed surface tree, so no color role is reachable here.
 */
private const val HIGHLIGHT_TEXT: Int = 0xFFFFB300.toInt()

/** Maps the appearance and position settings, plus the latest sample, to one [ReadoutStyle]. */
object ReadoutStyles {

  /**
   * [dynamicFill] and [dynamicText] are the chip colors the dynamic color mode draws with, so every
   * color is measured against the fill it lands on in every mode; the style itself keeps a null
   * fill and text in that mode and the pill takes the platform colors at draw time.
   */
  fun from(
    appearance: AppearanceSettings,
    position: PositionSettings,
    sample: SpeedSample?,
    dynamicFill: Int,
    dynamicText: Int,
  ): ReadoutStyle {
    val fill =
      when (appearance.colorMode) {
        ColorMode.DYNAMIC -> null
        ColorMode.CUSTOM -> appearance.backgroundColor
        ColorMode.MONOCHROME_LIGHT -> MONOCHROME_LIGHT_FILL
        ColorMode.MONOCHROME_DARK -> MONOCHROME_DARK_FILL
      }
    val resolvedText =
      when (appearance.colorMode) {
        ColorMode.DYNAMIC -> null
        ColorMode.CUSTOM -> appearance.textColor
        ColorMode.MONOCHROME_LIGHT -> MONOCHROME_LIGHT_TEXT
        ColorMode.MONOCHROME_DARK -> MONOCHROME_DARK_TEXT
      }
    val highlighted =
      appearance.thresholdHighlight &&
        sample != null &&
        sample.supported &&
        (sample.rxBytesPerSecond >= appearance.thresholdBytesPerSecond ||
          sample.txBytesPerSecond >= appearance.thresholdBytesPerSecond)
    val hasCustomTextColor =
      appearance.colorMode == ColorMode.CUSTOM && appearance.textColor != null
    // Each tint is measured only when something draws it: the search runs on every sample.
    val drawnFill = fill ?: dynamicFill
    // The theme's text on a custom fill steps to black or white when it cannot be read there, so
    // the highlight and the tints always have a readable color to step aside to.
    val baseText =
      if (resolvedText == null && fill != null) readableNeutral(dynamicText, fill) else resolvedText
    val text =
      if (highlighted && !hasCustomTextColor) {
        tintFor(
          drawnFill,
          HIGHLIGHT_TEXT,
          HIGHLIGHT_TEXT_ON_LIGHT,
          HIGHLIGHT_LIGHT_POLE,
          HIGHLIGHT_DARK_POLE,
        ) ?: baseText
      } else {
        baseText
      }
    // Both directions carry a tint or neither: one tinted half beside a plain one reads as a state.
    val tints: Pair<Int, Int>? =
      if (appearance.colorByDirection) {
        val download =
          tintFor(
            drawnFill,
            DOWNLOAD_TINT,
            DOWNLOAD_TINT_ON_LIGHT,
            DOWNLOAD_LIGHT_POLE,
            DOWNLOAD_DARK_POLE,
          )
        val upload =
          tintFor(drawnFill, UPLOAD_TINT, UPLOAD_TINT_ON_LIGHT, UPLOAD_LIGHT_POLE, UPLOAD_DARK_POLE)
        if (download != null && upload != null) download to upload else null
      } else {
        null
      }
    return ReadoutStyle(
      fill = fill,
      text = text,
      cornerRadiusDp = appearance.cornerRadiusDp,
      sidePaddingDp = position.chipPaddingDp,
      minWidthDp = position.minWidthDp,
      monospace = appearance.fontFamily == FontFamilyChoice.MONOSPACE,
      weight = appearance.fontWeight,
      downloadTint = tints?.first,
      uploadTint = tints?.second,
      highlighted = highlighted,
    )
  }
}

/**
 * The variant with the higher contrast against [fill], pushed toward whichever of [lightPole] and
 * [darkPole] reads better until it clears [MIN_TEXT_CONTRAST]. Null when no shade of the hue reads
 * on that fill, which is a mid-luminance fill: the text color draws instead, since a tint that
 * cannot be read tells nothing.
 */
private fun tintFor(fill: Int, vivid: Int, darker: Int, lightPole: Int, darkPole: Int): Int? {
  val better = if (contrastRatio(vivid, fill) >= contrastRatio(darker, fill)) vivid else darker
  val pole =
    if (contrastRatio(darkPole, fill) >= contrastRatio(lightPole, fill)) darkPole else lightPole
  return readableTint(better, fill, pole)
}

/**
 * Blends [tint] toward [pole] in steps until it clears [MIN_TEXT_CONTRAST] against [fill]; null
 * when not even the pole does.
 */
internal fun readableTint(tint: Int, fill: Int, pole: Int): Int? {
  var current = tint
  repeat(CONTRAST_STEPS) {
    if (contrastRatio(current, fill) >= MIN_TEXT_CONTRAST) return current
    current = blend(current, pole, CONTRAST_STEP_FRACTION)
  }
  return if (contrastRatio(pole, fill) >= MIN_TEXT_CONTRAST) pole else null
}

/**
 * Null when [candidate] reads on [fill], so the caller keeps drawing the platform's own color;
 * otherwise black or white, whichever reads better there.
 */
private fun readableNeutral(candidate: Int, fill: Int): Int? {
  if (contrastRatio(candidate, fill) >= MIN_TEXT_CONTRAST) return null
  return if (contrastRatio(BLACK, fill) >= contrastRatio(WHITE, fill)) BLACK else WHITE
}

private fun blend(from: Int, to: Int, fraction: Float): Int {
  fun channel(shift: Int): Int {
    val a = (from shr shift) and 0xFF
    val b = (to shr shift) and 0xFF
    return (a + (b - a) * fraction).toInt().coerceIn(0, 255)
  }
  return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** WCAG AA for text under 18 pt. */
internal const val MIN_TEXT_CONTRAST: Double = 4.5
private const val CONTRAST_STEPS: Int = 12
private const val CONTRAST_STEP_FRACTION: Float = 0.2f

/** WCAG contrast ratio between two colors, 1 for equal to 21 for black on white. */
internal fun contrastRatio(a: Int, b: Int): Double {
  val la = relativeLuminance(a)
  val lb = relativeLuminance(b)
  return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** WCAG relative luminance of an ARGB color's RGB channels, 0 for black to 1 for white. */
internal fun relativeLuminance(argb: Int): Double {
  fun channel(shift: Int): Double {
    val c = ((argb shr shift) and 0xFF) / 255.0
    return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
