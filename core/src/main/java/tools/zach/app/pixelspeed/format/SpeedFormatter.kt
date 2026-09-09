package tools.zach.app.pixelspeed.format

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import tools.zach.app.pixelspeed.settings.SettingsBounds

private val BIT_LABELS_FULL = listOf("bps", "Kbps", "Mbps", "Gbps")
private val BIT_LABELS_SHORT = listOf("b", "Kb", "Mb", "Gb")
private val SI_BYTE_LABELS_FULL = listOf("B/s", "KB/s", "MB/s", "GB/s")
private val SI_BYTE_LABELS_SHORT = listOf("B", "KB", "MB", "GB")
private val BINARY_BYTE_LABELS_FULL = listOf("B/s", "KiB/s", "MiB/s", "GiB/s")
private val BINARY_BYTE_LABELS_SHORT = listOf("B", "KiB", "MiB", "GiB")
private val EIGHT = BigDecimal(8)
private val ONE_THOUSAND = BigDecimal(1000)
private const val FIGURE_SPACE = '\u2007'
private const val MAX_AUTO_INTEGER_DIGITS = 3
/**
 * Ten gigabytes per second, past any phone radio; the top of the range
 * [SpeedFormatter.widestNumber] covers.
 */
private const val WIDEST_PROBE_MAX_BYTES = 10_000_000_000L
// 1000 and 1023 B/s sit under the binary kilo boundary, where a fraction of a KiB/s runs longest.
private val WIDEST_PROBES = listOf(1L, 999L, 1000L, 1023L, 1_249_999_999L, WIDEST_PROBE_MAX_BYTES)
// The mega label carries the widest letter in every label set (Mbps, MB/s, MiB/s).
private const val WIDEST_LABEL_TIER = 2

/**
 * Renders a byte-per-second throughput as a locale-aware number and unit pair. Pure Kotlin with no
 * Android dependency, so it runs the same in a JVM test as on device.
 */
class SpeedFormatter(private val locale: Locale) {

  companion object {
    /** The number an unsupported or absent reading renders as; a reader can name it instead. */
    const val UNSUPPORTED_DASH: String = "\u2013"
  }

  /**
   * Formats [bytesPerSecond] per [options]. `Long.MIN_VALUE` renders as the unsupported sentinel;
   * any other negative value is a caller error.
   */
  fun format(bytesPerSecond: Long, options: FormatOptions): FormattedSpeed {
    if (bytesPerSecond == Long.MIN_VALUE) {
      return FormattedSpeed(UNSUPPORTED_DASH, "")
    }
    require(bytesPerSecond >= 0) { "bytesPerSecond must not be negative: $bytesPerSecond" }

    val labels = labelsFor(options)
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val significantFigures = clampSignificantFigures(options.significantFigures)
    val decimals = clampDecimals(options.decimals)

    val base = baseFor(options)
    val convertedValue = convertedValueOf(bytesPerSecond, options)

    val (tier, numberText, width) =
      if (options.unitPrefix == UnitPrefix.AUTO) {
        val (autoTier, autoDecimals) =
          autoTierAndDecimals(
            convertedValue,
            base,
            options.precisionMode,
            significantFigures,
            decimals,
            floorIndex(options),
            options.pinnedToFloor,
          )
        val rounded = renderAtTier(convertedValue, autoTier, autoDecimals, base)
        val autoWidth =
          if (autoTier == 0) {
            MAX_AUTO_INTEGER_DIGITS
          } else {
            widthFor(options.precisionMode, significantFigures, decimals)
          }
        Triple(autoTier, rounded.toPlainString(), autoWidth)
      } else {
        val tierIdx = tierIndex(options.unitPrefix)
        val magnitude = convertedValue.divide(BigDecimal(base).pow(tierIdx), MathContext(50))
        val (_, rounded) =
          roundAtTier(magnitude, options.precisionMode, significantFigures, decimals)
        Triple(
          tierIdx,
          rounded.toPlainString(),
          widthFor(options.precisionMode, significantFigures, decimals),
        )
      }

    var number = numberText.replace('.', separator)
    if (options.fixedWidthDigits) {
      number = number.padStart(width, FIGURE_SPACE)
    }
    return FormattedSpeed(number, if (options.unitLabel != UnitLabel.NONE) labels[tier] else "")
  }

  /**
   * Formats [rxBytesPerSecond] and [txBytesPerSecond] on one shared prefix, so a paired display
   * (download next to upload) never shows two different unit labels. In AUTO mode the prefix is
   * whichever the larger of the two values would pick alone; both values then render at that
   * prefix's decimal count, as [FIXED] would. A fixed prefix, or either value being the unsupported
   * sentinel, behaves exactly like two independent [format] calls: there is no shared prefix to
   * compute in the first case, and no second value to share it with in the second. So does an
   * option pinned to its floor: the floor is the tier for both values, so each keeps its own
   * significant figures.
   */
  fun formatPair(
    rxBytesPerSecond: Long,
    txBytesPerSecond: Long,
    options: FormatOptions,
  ): Pair<FormattedSpeed, FormattedSpeed> {
    val bothZero = rxBytesPerSecond == 0L && txBytesPerSecond == 0L
    val eitherUnsupported = rxBytesPerSecond == Long.MIN_VALUE || txBytesPerSecond == Long.MIN_VALUE
    if (!eitherUnsupported) {
      require(rxBytesPerSecond >= 0) { "rxBytesPerSecond must not be negative: $rxBytesPerSecond" }
      require(txBytesPerSecond >= 0) { "txBytesPerSecond must not be negative: $txBytesPerSecond" }
    }
    if (
      eitherUnsupported ||
        bothZero ||
        options.unitPrefix != UnitPrefix.AUTO ||
        options.pinnedToFloor
    ) {
      return format(rxBytesPerSecond, options) to format(txBytesPerSecond, options)
    }

    val labels = labelsFor(options)
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val significantFigures = clampSignificantFigures(options.significantFigures)
    val fixedDecimals = clampDecimals(options.decimals)
    val base = baseFor(options)
    val rxConverted = convertedValueOf(rxBytesPerSecond, options)
    val txConverted = convertedValueOf(txBytesPerSecond, options)
    val larger = if (rxConverted.compareTo(txConverted) >= 0) rxConverted else txConverted
    val (tier, decimals) =
      autoTierAndDecimals(
        larger,
        base,
        options.precisionMode,
        significantFigures,
        fixedDecimals,
        floorIndex(options),
        options.pinnedToFloor,
      )
    val width =
      if (tier == 0) {
        MAX_AUTO_INTEGER_DIGITS
      } else {
        widthFor(options.precisionMode, significantFigures, fixedDecimals)
      }
    val unit = if (options.unitLabel != UnitLabel.NONE) labels[tier] else ""

    fun render(convertedValue: BigDecimal): FormattedSpeed {
      var number =
        renderAtTier(convertedValue, tier, decimals, base).toPlainString().replace('.', separator)
      if (options.fixedWidthDigits) {
        number = number.padStart(width, FIGURE_SPACE)
      }
      return FormattedSpeed(number, unit)
    }

    return render(rxConverted) to render(txConverted)
  }

  /**
   * The widest number this option set renders, every digit an eight, so a look measured on it never
   * grows when the live value changes tier. Under AUTO the width is the fixed-width padding target.
   * Under a fixed prefix, or AUTO pinned to its floor, the digits grow without bound, so the width
   * comes from rendering the extremes of the range a phone radio reaches, 1 B/s to
   * [WIDEST_PROBE_MAX_BYTES].
   */
  fun widestNumber(options: FormatOptions): String {
    val width =
      if (options.unitPrefix == UnitPrefix.AUTO && !options.pinnedToFloor) {
        maxOf(
          widthFor(
            options.precisionMode,
            clampSignificantFigures(options.significantFigures),
            clampDecimals(options.decimals),
          ),
          MAX_AUTO_INTEGER_DIGITS,
        )
      } else {
        WIDEST_PROBES.maxOf { format(it, options).number.length }
      }
    return "8".repeat(width)
  }

  /** The widest unit label of the option set, or empty when the label is [UnitLabel.NONE]. */
  fun widestUnit(options: FormatOptions): String =
    if (options.unitLabel != UnitLabel.NONE) labelsFor(options)[WIDEST_LABEL_TIER] else ""

  private fun labelsFor(options: FormatOptions): List<String> {
    // AUTO spells the unit out here; the stack and the halves ask through forStackAndHalves().
    val full = options.unitLabel != UnitLabel.SHORT
    return when (options.unitSystem) {
      UnitSystem.BITS_PER_SECOND -> if (full) BIT_LABELS_FULL else BIT_LABELS_SHORT
      UnitSystem.BYTES_PER_SECOND ->
        if (options.binaryPrefixes) {
          if (full) BINARY_BYTE_LABELS_FULL else BINARY_BYTE_LABELS_SHORT
        } else {
          if (full) SI_BYTE_LABELS_FULL else SI_BYTE_LABELS_SHORT
        }
    }
  }

  private fun tierIndex(prefix: UnitPrefix): Int =
    when (prefix) {
      UnitPrefix.KILO -> 1
      UnitPrefix.MEGA -> 2
      UnitPrefix.GIGA -> 3
      UnitPrefix.AUTO -> throw IllegalStateException("AUTO has no fixed tier index")
    }

  private fun baseFor(options: FormatOptions): Int =
    if (options.unitSystem == UnitSystem.BITS_PER_SECOND || !options.binaryPrefixes) {
      1000
    } else {
      1024
    }

  private fun convertedValueOf(bytesPerSecond: Long, options: FormatOptions): BigDecimal =
    if (options.unitSystem == UnitSystem.BITS_PER_SECOND) {
      BigDecimal(bytesPerSecond).multiply(EIGHT)
    } else {
      BigDecimal(bytesPerSecond)
    }

  /**
   * The tier AUTO settles on and the decimals it renders there. A value under [floor] is held at
   * the floor as a fraction of that unit, with the precision's digits past the leading zero and
   * never fewer than one, so a low rate reads as 0.05 Mbps rather than climbing down to Kbps and a
   * one-figure or whole-number precision still shows 0.3 Mbps instead of a bare zero. A [pinned]
   * option holds a value over the floor there too, with growing integer digits, so a bare number
   * never changes unit.
   */
  private fun autoTierAndDecimals(
    convertedValue: BigDecimal,
    base: Int,
    precisionMode: PrecisionMode,
    significantFigures: Int,
    fixedDecimals: Int,
    floor: Int,
    pinned: Boolean,
  ): Pair<Int, Int> {
    val natural: Pair<Int, Int> =
      if (convertedValue.compareTo(ONE_THOUSAND) < 0) {
        0 to 0
      } else {
        val (tier, rounded) =
          climbAutoTier(convertedValue, base, precisionMode, significantFigures, fixedDecimals)
        tier to rounded.scale()
      }
    if (pinned && natural.first > floor) {
      val magnitude = convertedValue.divide(BigDecimal(base).pow(floor), MathContext(50))
      val (_, rounded) =
        roundAtTier(
          magnitude,
          precisionMode,
          significantFigures,
          fixedDecimals,
          truncatesDigits = true,
        )
      return floor to rounded.scale()
    }
    if (natural.first >= floor) return natural
    val decimals =
      when (precisionMode) {
        PrecisionMode.SIGNIFICANT -> significantFigures - 1
        PrecisionMode.FIXED -> fixedDecimals
      }
    return floor to decimals.coerceAtLeast(1)
  }

  private fun floorIndex(options: FormatOptions): Int =
    when (options.autoFloor) {
      AutoFloor.BARE -> 0
      AutoFloor.KILO -> 1
      AutoFloor.MEGA -> 2
    }

  /**
   * Renders [convertedValue] at [tier] to exactly [decimals] decimal places, tier zero being bare.
   */
  private fun renderAtTier(
    convertedValue: BigDecimal,
    tier: Int,
    decimals: Int,
    base: Int,
  ): BigDecimal {
    val magnitude =
      if (tier == 0) {
        convertedValue
      } else {
        convertedValue.divide(BigDecimal(base).pow(tier), MathContext(50))
      }
    return magnitude.setScale(decimals, RoundingMode.HALF_UP)
  }

  /**
   * Climbs from the kilo tier upward while the rounded magnitude still reaches 1000, capping at the
   * giga tier so larger values keep growing digits instead of climbing further.
   */
  private fun climbAutoTier(
    convertedValue: BigDecimal,
    base: Int,
    precisionMode: PrecisionMode,
    significantFigures: Int,
    fixedDecimals: Int,
  ): Pair<Int, BigDecimal> {
    var tier = 1
    while (true) {
      val magnitude = convertedValue.divide(BigDecimal(base).pow(tier), MathContext(50))
      val (_, rounded) =
        roundAtTier(
          magnitude,
          precisionMode,
          significantFigures,
          fixedDecimals,
          truncatesDigits = true,
        )
      if (tier < 3 && rounded.compareTo(ONE_THOUSAND) >= 0) {
        tier++
        continue
      }
      return tier to rounded
    }
  }

  /**
   * Rounds [magnitude] for one tier. FIXED always uses the configured decimal count. SIGNIFICANT
   * derives decimals from the digit count, then re-derives them if rounding carries the value into
   * the next digit count (0.99995 becomes 1.00, not 1.000). When [truncatesDigits] is true and
   * [significantFigures] is fewer than the integer digit count, rounding reaches into the integer
   * part itself (12.345678 at one figure becomes 10), which is what AUTO climbing needs to honor a
   * caller's requested figure count at any magnitude. A fixed prefix passes false instead: rule 4
   * keeps every integer digit there regardless of magnitude, so decimals only ever floor at zero.
   */
  private fun roundAtTier(
    magnitude: BigDecimal,
    precisionMode: PrecisionMode,
    significantFigures: Int,
    fixedDecimals: Int,
    truncatesDigits: Boolean = false,
  ): Pair<Int, BigDecimal> =
    when (precisionMode) {
      PrecisionMode.FIXED ->
        fixedDecimals to magnitude.setScale(fixedDecimals, RoundingMode.HALF_UP)
      PrecisionMode.SIGNIFICANT -> {
        val exponent = integerDigitExponent(magnitude)
        val rawDecimals = significantFigures - (exponent + 1)
        val decimals = if (truncatesDigits) rawDecimals else rawDecimals.coerceAtLeast(0)
        val rounded = magnitude.setScale(decimals, RoundingMode.HALF_UP)
        val rolledExponent = integerDigitExponent(rounded)
        if (rolledExponent != exponent) {
          val rawAdjusted = significantFigures - (rolledExponent + 1)
          val adjustedDecimals = if (truncatesDigits) rawAdjusted else rawAdjusted.coerceAtLeast(0)
          adjustedDecimals to magnitude.setScale(adjustedDecimals, RoundingMode.HALF_UP)
        } else {
          decimals to rounded
        }
      }
    }

  /**
   * Returns e such that value falls in the half-open range from ten to the e to ten to the e plus
   * one, negative when value is below one. An exact zero has no such range; it returns zero rather
   * than looping, since repeated multiplication by ten never moves zero past one.
   */
  private fun integerDigitExponent(value: BigDecimal): Int {
    if (value.signum() == 0) {
      return 0
    }
    var scaled = value
    var exponent = 0
    if (scaled.compareTo(BigDecimal.ONE) >= 0) {
      while (scaled.compareTo(BigDecimal.TEN) >= 0) {
        scaled = scaled.divide(BigDecimal.TEN)
        exponent++
      }
    } else {
      while (scaled.compareTo(BigDecimal.ONE) < 0) {
        scaled = scaled.multiply(BigDecimal.TEN)
        exponent--
      }
    }
    return exponent
  }

  /**
   * The padding target for a tiered number: the longest string this precision setting can produce
   * across the one-to-three integer digit range AUTO climbs through before moving to the next
   * prefix. SIGNIFICANT trades decimals for integer digits as magnitude grows, so the longest form
   * sits at the smallest integer digit count, not the largest. FIXED keeps a constant decimal
   * count, so the longest form sits at three integer digits, the widest a tier reaches before AUTO
   * would climb past it. A fixed prefix reusing this width can still overflow it for very large
   * magnitudes; padding never shortens a longer string.
   */
  private fun widthFor(precisionMode: PrecisionMode, significantFigures: Int, decimals: Int): Int =
    when (precisionMode) {
      PrecisionMode.SIGNIFICANT ->
        (1..MAX_AUTO_INTEGER_DIGITS).maxOf { integerDigits ->
          val figureDecimals = (significantFigures - integerDigits).coerceAtLeast(0)
          lengthFor(integerDigits, figureDecimals)
        }
      PrecisionMode.FIXED -> lengthFor(MAX_AUTO_INTEGER_DIGITS, decimals)
    }

  private fun lengthFor(integerDigits: Int, decimals: Int): Int =
    integerDigits + (if (decimals > 0) 1 else 0) + decimals

  /**
   * Clamps a caller-supplied significant-figure count to a range this formatter can render safely.
   * Below one figure, [roundAtTier] can round a magnitude to an exact zero and loop forever
   * deriving its digit count; above six, the string grows without a practical bound.
   */
  private fun clampSignificantFigures(significantFigures: Int): Int =
    significantFigures.coerceIn(SettingsBounds.SIGNIFICANT_FIGURES)

  /** Clamps a caller-supplied decimal count so `FIXED` mode cannot produce an unbounded string. */
  private fun clampDecimals(decimals: Int): Int = decimals.coerceIn(SettingsBounds.DECIMALS)
}
