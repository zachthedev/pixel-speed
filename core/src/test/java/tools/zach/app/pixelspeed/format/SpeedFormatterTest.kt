package tools.zach.app.pixelspeed.format

import java.text.DecimalFormatSymbols
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

private val US = Locale.US
private const val UNSUPPORTED_SENTINEL = "\u2013"
private const val FIGURE_SPACE = '\u2007'

class SpeedFormatterTest {

  private val formatter = SpeedFormatter(US)

  @Test
  fun `unsupported sentinel gives dash and empty unit`() {
    val result = formatter.format(Long.MIN_VALUE, FormatOptions().bare())
    assertEquals(UNSUPPORTED_SENTINEL, result.number)
    assertEquals("", result.unit)
  }

  @Test
  fun `negative value that is not the sentinel throws with the value named`() {
    val exception =
      assertThrows(IllegalArgumentException::class.java) {
        formatter.format(-42L, FormatOptions().bare())
      }
    assertEquals(true, exception.message?.contains("-42") == true)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bitsVsBytesRows")
  fun `bits versus bytes for the same throughput`(
    label: String,
    bytesPerSecond: Long,
    options: FormatOptions,
    expectedNumber: String,
    expectedUnit: String,
  ) {
    val result = formatter.format(bytesPerSecond, options)
    assertEquals(expectedNumber, result.number, label)
    assertEquals(expectedUnit, result.unit, label)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("autoBoundaryRows")
  fun `AUTO climbs prefixes at the SI kilo boundary`(
    label: String,
    bytesPerSecond: Long,
    expectedNumber: String,
    expectedUnit: String,
  ) {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(bytesPerSecond, options)
    assertEquals(expectedNumber, result.number, label)
    assertEquals(expectedUnit, result.unit, label)
  }

  @Test
  fun `rounding that carries a value to 1000 bumps AUTO to the next prefix`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BITS_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(124_994L, options)
    assertEquals("1.00", result.number)
    assertEquals("Mbps", result.unit)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("significantFigureRows")
  fun `significant figures trade decimals for integer digits`(
    label: String,
    bytesPerSecond: Long,
    significantFigures: Int,
    expectedNumber: String,
  ) {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          significantFigures = significantFigures,
          fixedWidthDigits = false,
        )
        .bare()
    val result = formatter.format(bytesPerSecond, options)
    assertEquals(expectedNumber, result.number, label)
  }

  @ParameterizedTest(name = "decimals={0}")
  @CsvSource("0, 12 MB/s", "1, 12.3 MB/s", "2, 12.35 MB/s")
  fun `FIXED precision holds a constant decimal count`(decimals: Int, expected: String) {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          precisionMode = PrecisionMode.FIXED,
          decimals = decimals,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(12_345_678L, options)
    assertEquals(expected, "${result.number} ${result.unit}")
  }

  @Test
  fun `fixed KILO prefix keeps growing digits above 1000`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          unitPrefix = UnitPrefix.KILO,
          precisionMode = PrecisionMode.SIGNIFICANT,
          significantFigures = 3,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(12_345_678L, options)
    assertEquals("12346", result.number)
    assertEquals("KB/s", result.unit)
  }

  @Test
  fun `fixed MEGA prefix keeps decimals below one`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          unitPrefix = UnitPrefix.MEGA,
          precisionMode = PrecisionMode.FIXED,
          decimals = 2,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(1_234L, options)
    assertEquals("0.00", result.number)
    assertEquals("MB/s", result.unit)
  }

  @Test
  fun `fixed KILO prefix below one keeps three significant figures`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          unitPrefix = UnitPrefix.KILO,
          precisionMode = PrecisionMode.SIGNIFICANT,
          significantFigures = 3,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(500L, options)
    assertEquals("0.500", result.number)
    assertEquals("KB/s", result.unit)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("zeroRows")
  fun `zero takes the decimals its neighbors would at the same unit`(
    label: String,
    options: FormatOptions,
    expectedNumber: String,
    expectedUnit: String,
  ) {
    val result = formatter.format(0L, options)
    assertEquals(expectedNumber, result.number, label)
    assertEquals(expectedUnit, result.unit, label)
  }

  @Test
  fun `below one kilo shows an integer with the bare unit for bits`() {
    val options =
      FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitLabel = UnitLabel.FULL).bare()
    val result = formatter.format(100L, options)
    assertEquals("800", result.number)
    assertEquals("bps", result.unit)
  }

  @Test
  fun `below one kilo shows an integer with the bare unit for bytes`() {
    val options =
      FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitLabel = UnitLabel.FULL).bare()
    val result = formatter.format(512L, options)
    assertEquals("512", result.number)
    assertEquals("B/s", result.unit)
  }

  @Test
  fun `the below-one-kilo threshold checks the converted value, not the raw byte rate`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BITS_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(512L, options)
    assertEquals("4.10", result.number)
    assertEquals("Kbps", result.unit)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shortLabelRows")
  fun `SHORT strips the ps and the slash-s at every tier`(
    label: String,
    bytesPerSecond: Long,
    options: FormatOptions,
    expectedUnit: String,
  ) {
    val result = formatter.format(bytesPerSecond, options.copy(unitLabel = UnitLabel.SHORT))
    assertEquals(expectedUnit, result.unit, label)
  }

  @Test
  fun `NONE empties only the unit`() {
    val shown =
      FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitLabel = UnitLabel.FULL).bare()
    val hidden = shown.copy(unitLabel = UnitLabel.NONE)
    val shownResult = formatter.format(1_000_000L, shown)
    val hiddenResult = formatter.format(1_000_000L, hidden)
    assertEquals(shownResult.number, hiddenResult.number)
    assertEquals("", hiddenResult.unit)
  }

  @Test
  fun `widestUnit under SHORT is the mega short label`() {
    assertEquals(
      "Mb",
      formatter.widestUnit(
        FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitLabel = UnitLabel.SHORT).bare()
      ),
    )
    assertEquals(
      "MB",
      formatter.widestUnit(
        FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitLabel = UnitLabel.SHORT).bare()
      ),
    )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("floorRows")
  fun `AUTO holds at the floor as a fraction of that unit`(
    label: String,
    bytesPerSecond: Long,
    options: FormatOptions,
    expectedNumber: String,
    expectedUnit: String,
  ) {
    val result = formatter.format(bytesPerSecond, options.copy(fixedWidthDigits = false))
    assertEquals(expectedNumber, result.number, label)
    assertEquals(expectedUnit, result.unit, label)
  }

  @Test
  fun `the default floor is mega, so a low rate reads as a fraction of a megabit`() {
    assertEquals(AutoFloor.MEGA, FormatOptions().autoFloor)
    val low = formatter.format(1_230L, FormatOptions(fixedWidthDigits = false))
    assertEquals("0.01", low.number)
    assertEquals("Mbps", low.unit)
  }

  @Test
  fun `AUTO spells the unit out and drops it only through forStackAndHalves`() {
    val auto = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND).bare()
    assertEquals(UnitLabel.AUTO, auto.unitLabel, "the default")
    assertEquals("Mbps", formatter.widestUnit(auto))
    assertEquals("Kbps", formatter.format(12_500L, auto).unit)
    val floored = auto.copy(autoFloor = AutoFloor.MEGA)
    assertEquals("", formatter.widestUnit(floored.forStackAndHalves()))
    assertEquals("Mb", formatter.widestUnit(auto.forStackAndHalves()), "a bare floor keeps a label")
    assertEquals(
      UnitLabel.FULL,
      auto.copy(unitLabel = UnitLabel.FULL).forStackAndHalves().unitLabel,
    )
    assertEquals(
      UnitLabel.SHORT,
      auto.copy(unitLabel = UnitLabel.SHORT).forStackAndHalves().unitLabel,
    )
    assertEquals(
      UnitLabel.NONE,
      auto.copy(unitLabel = UnitLabel.NONE).forStackAndHalves().unitLabel,
    )
  }

  @Test
  fun `the bare stack number stays in megabits past a gigabit while the full look climbs`() {
    val auto = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, fixedWidthDigits = false)
    val stacked = auto.forStackAndHalves()
    val gigabit = 150_000_000L
    assertEquals("1.20", formatter.format(gigabit, auto).number)
    assertEquals("Gbps", formatter.format(gigabit, auto).unit)
    assertEquals("1200", formatter.format(gigabit, stacked).number)
    assertEquals("", formatter.format(gigabit, stacked).unit)
    assertEquals(
      "0.00",
      formatter.format(100L, stacked).number,
      "below the floor as the floor does",
    )
  }

  @Test
  fun `a floor or prefix that lets the number change tier keeps the short label on the stack`() {
    val bare = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, autoFloor = AutoFloor.BARE)
    assertEquals(UnitLabel.SHORT, bare.forStackAndHalves().unitLabel)
    assertFalse(bare.forStackAndHalves().pinnedToFloor)
    val fixed = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitPrefix = UnitPrefix.KILO)
    assertEquals(UnitLabel.SHORT, fixed.forStackAndHalves().unitLabel)
    val kilo = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, autoFloor = AutoFloor.KILO)
    assertEquals(UnitLabel.NONE, kilo.forStackAndHalves().unitLabel, "a kilo floor pins too")
    assertTrue(kilo.forStackAndHalves().pinnedToFloor)
  }

  @Test
  fun `a pinned pair keeps each direction's own significant figures`() {
    val pinned = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND).forStackAndHalves()
    val (download, upload) = formatter.formatPair(400_000L, 112_500_000L, pinned)
    assertEquals("3.20", download.number.trim(), "the slow direction keeps its decimals")
    assertEquals("${FIGURE_SPACE}900", upload.number, "padded to the slot the pair shares")
    assertEquals(download.unit, upload.unit, "one label across the pair")
    assertEquals("", download.unit)
    val (idle, active) = formatter.formatPair(0L, 112_500_000L, pinned)
    assertEquals("0.00", idle.number.trim(), "an idle direction keeps the floor's fraction")
    assertEquals("900", active.number.trim())
    val (bothFast, other) = formatter.formatPair(150_000_000L, 112_500_000L, pinned)
    assertEquals("1200", bothFast.number.trim())
    assertEquals("900", other.number.trim())
    val kiloFixed =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          autoFloor = AutoFloor.KILO,
          precisionMode = PrecisionMode.FIXED,
          decimals = 1,
          binaryPrefixes = true,
        )
        .forStackAndHalves()
    val (slow, fast) = formatter.formatPair(512L, 2_097_152L, kiloFixed)
    assertEquals("0.5", slow.number.trim(), "a binary kilo floor with fixed decimals")
    assertEquals("2048.0", fast.number.trim())
    assertEquals("", slow.unit)
  }

  @Test
  fun `the widest number covers the fraction just under the binary kilo boundary`() {
    val options =
      FormatOptions(
        unitSystem = UnitSystem.BYTES_PER_SECOND,
        autoFloor = AutoFloor.KILO,
        binaryPrefixes = true,
        significantFigures = 6,
        unitPrefix = UnitPrefix.KILO,
      )
    val widest = formatter.widestNumber(options)
    for (bytes in listOf(1000L, 1023L)) {
      val live = formatter.format(bytes, options).number
      assertTrue(live.length <= widest.length, "$bytes B/s renders '$live', wider than '$widest'")
    }
  }

  @Test
  fun `a pinned option measures its widest number on the pinned tier's growing digits`() {
    val pinned = FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND).forStackAndHalves()
    assertEquals("88888", formatter.widestNumber(pinned), "80 Gbps is 80000 in megabits")
    assertEquals("80000", formatter.format(10_000_000_000L, pinned).number.trim())
    assertEquals(
      "0.00",
      formatter.format(1L, pinned).number.trim(),
      "below the floor the floor's own fraction rule holds",
    )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sevenLocaleRows")
  fun `the short unit stays the same ASCII label across every shipped locale`(locale: Locale) {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          unitPrefix = UnitPrefix.KILO,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.SHORT,
        )
        .bare()
    val result = SpeedFormatter(locale).format(12_300L, options)
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    assertEquals("KB", result.unit, locale.toString())
    assertEquals("12${separator}3", result.number, locale.toString())
  }

  @Test
  fun `locale drives the decimal separator with ASCII digits`() {
    val options = FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND).bare()
    val germany = SpeedFormatter(Locale.GERMANY).format(12_300L, options)
    val us = SpeedFormatter(Locale.US).format(12_300L, options)
    assertEquals("12,3", germany.number)
    assertEquals("12.3", us.number)
  }

  @Test
  fun `fixed width digits pad SIGNIFICANT three to a common length`() {
    val padded =
      FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, fixedWidthDigits = true).bare()
    val nineNineNine = formatter.format(1_230L, padded)
    val nineNine = formatter.format(123_000L, padded)
    assertEquals(4, nineNineNine.number.length)
    assertEquals(4, nineNine.number.length)
    assertEquals(true, nineNine.number.startsWith(FIGURE_SPACE))
  }

  @Test
  fun `fixed width digits off leaves the number unpadded`() {
    val unpadded =
      FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, fixedWidthDigits = false).bare()
    val result = formatter.format(123_000L, unpadded)
    assertEquals("123", result.number)
  }

  @Test
  fun `break round bits multiplier flip turns the bits row red`() {
    val options =
      FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitLabel = UnitLabel.FULL).bare()
    val result = formatter.format(1_000_000L, options)
    assertEquals("8.00", result.number)
    assertEquals("Mbps", result.unit)
  }

  @Test
  fun `formatPair shares the larger value's prefix in bits`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BITS_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val (smaller, larger) = formatter.formatPair(3_475L, 172_500L, options)
    assertEquals("0.03", smaller.number)
    assertEquals("Mbps", smaller.unit)
    assertEquals("1.38", larger.number)
    assertEquals("Mbps", larger.unit)
  }

  @Test
  fun `formatPair shares the larger value's prefix in bytes SI`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val (smaller, larger) = formatter.formatPair(1_000L, 999_500L, options)
    assertEquals("0.00", smaller.number)
    assertEquals("MB/s", smaller.unit)
    assertEquals("1.00", larger.number)
    assertEquals("MB/s", larger.unit)
  }

  @Test
  fun `formatPair with a fixed prefix matches two independent format calls`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          unitPrefix = UnitPrefix.KILO,
          precisionMode = PrecisionMode.SIGNIFICANT,
          significantFigures = 3,
        )
        .bare()
    val paired = formatter.formatPair(12_345_678L, 500L, options)
    val independent = formatter.format(12_345_678L, options) to formatter.format(500L, options)
    assertEquals(independent, paired)
  }

  @Test
  fun `formatPair renders zero beside a nonzero value at the shared prefix`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BITS_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val (zero, nonzero) = formatter.formatPair(0L, 172_500L, options)
    assertEquals("0.00", zero.number)
    assertEquals("Mbps", zero.unit)
    assertEquals("1.38", nonzero.number)
    assertEquals("Mbps", nonzero.unit)
  }

  @Test
  fun `formatPair with both sides zero renders bare integers on a bare floor`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BITS_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val (rx, tx) = formatter.formatPair(0L, 0L, options)
    assertEquals("0", rx.number)
    assertEquals("bps", rx.unit)
    assertEquals("0", tx.number)
    assertEquals("bps", tx.unit)
  }

  @Test
  fun `formatPair with the unsupported sentinel on one side formats the other alone`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val (unsupported, alone) = formatter.formatPair(Long.MIN_VALUE, 1_000_000L, options)
    assertEquals(UNSUPPORTED_SENTINEL, unsupported.number)
    assertEquals("", unsupported.unit)
    assertEquals("1.00", alone.number)
    assertEquals("MB/s", alone.unit)
  }

  @Test
  fun `formatPair pads both sides to the same width`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          fixedWidthDigits = true,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val (smaller, larger) = formatter.formatPair(1_230L, 12_345_678L, options)
    assertEquals("12.3", larger.number)
    assertEquals("MB/s", larger.unit)
    assertEquals(larger.number.length, smaller.number.length)
    assertEquals(true, smaller.number.startsWith(FIGURE_SPACE))
  }

  @Test
  fun `zero significant figures under a fixed prefix returns instead of looping`() {
    val options =
      FormatOptions(unitPrefix = UnitPrefix.MEGA, significantFigures = 0, fixedWidthDigits = false)
        .bare()
    val oneFigure = options.copy(significantFigures = 1)
    assertEquals(formatter.format(40_000L, oneFigure), formatter.format(40_000L, options))
  }

  @Test
  fun `an unbounded decimal count clamps to six`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          precisionMode = PrecisionMode.FIXED,
          decimals = 100_000,
          fixedWidthDigits = false,
        )
        .bare()
    val sixDecimals = options.copy(decimals = 6)
    assertEquals(formatter.format(12_345_678L, sixDecimals), formatter.format(12_345_678L, options))
  }

  @Test
  fun `one significant figure rounds off integer digits, not just decimals`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          significantFigures = 1,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(12_345_678L, options)
    assertEquals("10", result.number)
    assertEquals("MB/s", result.unit)
  }

  @Test
  fun `six significant figures keep every digit AUTO can show`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          significantFigures = 6,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(12_345_678L, options)
    assertEquals("12.3457", result.number)
    assertEquals("MB/s", result.unit)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("widestRows")
  fun `the widest strings of an AUTO option set are the padding target`(
    name: String,
    options: FormatOptions,
    widestNumber: String,
    widestUnit: String,
  ) {
    val formatter = SpeedFormatter(Locale.US)
    assertEquals(widestNumber, formatter.widestNumber(options), "$name: number")
    assertEquals(widestUnit, formatter.widestUnit(options), "$name: unit")
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("widestBoundRows")
  fun `no live value up to ten gigabytes per second renders wider than the widest strings`(
    name: String,
    options: FormatOptions,
  ) {
    val formatter = SpeedFormatter(Locale.US)
    val widestNumber = formatter.widestNumber(options)
    val widestUnit = formatter.widestUnit(options)
    val probes =
      listOf(0L, 1L, 9L, 124L, 999L, 1_000L, 124_999L, 125_000L, 1_249_999_999L, 10_000_000_000L)
    probes.forEach { bytes ->
      val live = formatter.format(bytes, options)
      assertTrue(
        live.number.length <= widestNumber.length,
        "$name: $bytes B/s renders '${live.number}', wider than '$widestNumber'",
      )
      assertTrue(
        live.unit.length <= widestUnit.length,
        "$name: $bytes B/s labels '${live.unit}', wider than '$widestUnit'",
      )
    }
  }

  @Test
  fun `zero under a fixed MEGA prefix`() {
    val options =
      FormatOptions(
          unitSystem = UnitSystem.BYTES_PER_SECOND,
          unitPrefix = UnitPrefix.MEGA,
          fixedWidthDigits = false,
          unitLabel = UnitLabel.FULL,
        )
        .bare()
    val result = formatter.format(0L, options)
    assertEquals("0.00", result.number)
    assertEquals("MB/s", result.unit)
  }

  companion object {
    @JvmStatic
    fun bitsVsBytesRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "1,000,000 bytes per second as bits is 8.00 Mbps",
          1_000_000L,
          FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitLabel = UnitLabel.FULL).bare(),
          "8.00",
          "Mbps",
        ),
        Arguments.of(
          "1,000,000 bytes per second as bytes is 1.00 MB/s",
          1_000_000L,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitLabel = UnitLabel.FULL)
            .bare(),
          "1.00",
          "MB/s",
        ),
        Arguments.of(
          "1,048,576 bytes per second in binary bytes is 1.00 MiB/s",
          1_048_576L,
          FormatOptions(
              unitSystem = UnitSystem.BYTES_PER_SECOND,
              binaryPrefixes = true,
              unitLabel = UnitLabel.FULL,
            )
            .bare(),
          "1.00",
          "MiB/s",
        ),
        Arguments.of(
          "1,048,576 bytes per second in bits is 8.39 Mbps",
          1_048_576L,
          FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitLabel = UnitLabel.FULL).bare(),
          "8.39",
          "Mbps",
        ),
      )

    @JvmStatic
    fun autoBoundaryRows(): List<Arguments> =
      listOf(
        Arguments.of("999 B/s stays below the kilo boundary", 999L, "999", "B/s"),
        Arguments.of("1000 B/s crosses into kilo", 1_000L, "1.00", "KB/s"),
        Arguments.of("999,499 rounds down and stays in kilo", 999_499L, "999", "KB/s"),
        Arguments.of("999,500 rounds up and bumps to mega", 999_500L, "1.00", "MB/s"),
        Arguments.of("12,345,678 stays in mega", 12_345_678L, "12.3", "MB/s"),
        Arguments.of("1,234,567,890 bumps to giga", 1_234_567_890L, "1.23", "GB/s"),
        Arguments.of(
          "12,345,678,901 stays in giga as the ceiling",
          12_345_678_901L,
          "12.3",
          "GB/s",
        ),
      )

    @JvmStatic
    fun significantFigureRows(): List<Arguments> =
      listOf(
        Arguments.of("1,230 bytes at three figures keeps two decimals", 1_230L, 3, "1.23"),
        Arguments.of("12,300 bytes at three figures keeps one decimal", 12_300L, 3, "12.3"),
        Arguments.of("123,000 bytes at three figures keeps no decimals", 123_000L, 3, "123"),
        Arguments.of("12,300 bytes at four figures keeps two decimals", 12_300L, 4, "12.30"),
      )

    @JvmStatic
    fun widestRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "three figures in bits",
          FormatOptions(unitLabel = UnitLabel.FULL).bare(),
          "8888",
          "Mbps",
        ),
        Arguments.of(
          "one figure keeps the three raw digits",
          FormatOptions(significantFigures = 1, unitLabel = UnitLabel.FULL).bare(),
          "888",
          "Mbps",
        ),
        Arguments.of(
          "six figures",
          FormatOptions(significantFigures = 6, unitLabel = UnitLabel.FULL).bare(),
          "8888888",
          "Mbps",
        ),
        Arguments.of(
          "fixed one decimal in SI bytes",
          FormatOptions(
              unitSystem = UnitSystem.BYTES_PER_SECOND,
              precisionMode = PrecisionMode.FIXED,
              decimals = 1,
              unitLabel = UnitLabel.FULL,
            )
            .bare(),
          "88888",
          "MB/s",
        ),
        Arguments.of(
          "binary bytes carry the i",
          FormatOptions(
              unitSystem = UnitSystem.BYTES_PER_SECOND,
              binaryPrefixes = true,
              unitLabel = UnitLabel.FULL,
            )
            .bare(),
          "8888",
          "MiB/s",
        ),
        Arguments.of("labels off", FormatOptions(unitLabel = UnitLabel.NONE).bare(), "8888", ""),
      )

    @JvmStatic
    fun floorRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "kilo rate at the mega floor",
          12_300L,
          FormatOptions(autoFloor = AutoFloor.MEGA),
          "0.10",
          "Mbps",
        ),
        Arguments.of(
          "bare rate at the mega floor",
          50L,
          FormatOptions(autoFloor = AutoFloor.MEGA),
          "0.00",
          "Mbps",
        ),
        Arguments.of(
          "zero at the mega floor",
          0L,
          FormatOptions(autoFloor = AutoFloor.MEGA),
          "0.00",
          "Mbps",
        ),
        Arguments.of(
          "one significant figure keeps one decimal at the mega floor",
          37_500L,
          FormatOptions(autoFloor = AutoFloor.MEGA, significantFigures = 1),
          "0.3",
          "Mbps",
        ),
        Arguments.of(
          "two significant figures keep one decimal at the mega floor",
          37_500L,
          FormatOptions(autoFloor = AutoFloor.MEGA, significantFigures = 2),
          "0.3",
          "Mbps",
        ),
        Arguments.of(
          "zero fixed decimals keep one decimal at the mega floor",
          37_500L,
          FormatOptions(
            autoFloor = AutoFloor.MEGA,
            precisionMode = PrecisionMode.FIXED,
            decimals = 0,
          ),
          "0.3",
          "Mbps",
        ),
        Arguments.of(
          "zero fixed decimals keep one decimal at the kilo floor",
          50L,
          FormatOptions(
            autoFloor = AutoFloor.KILO,
            precisionMode = PrecisionMode.FIXED,
            decimals = 0,
          ),
          "0.4",
          "Kbps",
        ),
        Arguments.of(
          "one significant figure stays whole on a bare floor",
          50L,
          FormatOptions(autoFloor = AutoFloor.BARE, significantFigures = 1),
          "400",
          "bps",
        ),
        Arguments.of(
          "zero keeps the same one decimal at a whole-number precision",
          0L,
          FormatOptions(
            autoFloor = AutoFloor.MEGA,
            precisionMode = PrecisionMode.FIXED,
            decimals = 0,
          ),
          "0.0",
          "Mbps",
        ),
        Arguments.of(
          "mega rate at the mega floor climbs as usual",
          1_540_000L,
          FormatOptions(autoFloor = AutoFloor.MEGA),
          "12.3",
          "Mbps",
        ),
        Arguments.of(
          "bare rate at the kilo floor",
          50L,
          FormatOptions(autoFloor = AutoFloor.KILO),
          "0.40",
          "Kbps",
        ),
        Arguments.of(
          "kilo rate at the kilo floor climbs as usual",
          12_300L,
          FormatOptions(autoFloor = AutoFloor.KILO),
          "98.4",
          "Kbps",
        ),
        Arguments.of(
          "fixed decimals at the floor keep the fixed count",
          12_300L,
          FormatOptions(
            autoFloor = AutoFloor.MEGA,
            precisionMode = PrecisionMode.FIXED,
            decimals = 1,
          ),
          "0.1",
          "Mbps",
        ),
        Arguments.of(
          "bytes at the mega floor",
          12_300L,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, autoFloor = AutoFloor.MEGA),
          "0.01",
          "MB/s",
        ),
      )

    @JvmStatic
    fun shortLabelRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "bare bits",
          10L,
          FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND).bare(),
          "b",
        ),
        Arguments.of(
          "kilo bits",
          100_000L,
          FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitPrefix = UnitPrefix.KILO)
            .bare(),
          "Kb",
        ),
        Arguments.of(
          "mega bits",
          100_000L,
          FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitPrefix = UnitPrefix.MEGA)
            .bare(),
          "Mb",
        ),
        Arguments.of(
          "giga bits",
          100_000L,
          FormatOptions(unitSystem = UnitSystem.BITS_PER_SECOND, unitPrefix = UnitPrefix.GIGA)
            .bare(),
          "Gb",
        ),
        Arguments.of(
          "bare bytes",
          10L,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND).bare(),
          "B",
        ),
        Arguments.of(
          "kilo bytes",
          100_000L,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitPrefix = UnitPrefix.KILO)
            .bare(),
          "KB",
        ),
        Arguments.of(
          "mega bytes",
          100_000L,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitPrefix = UnitPrefix.MEGA)
            .bare(),
          "MB",
        ),
        Arguments.of(
          "giga bytes",
          100_000L,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND, unitPrefix = UnitPrefix.GIGA)
            .bare(),
          "GB",
        ),
      )

    @JvmStatic
    fun sevenLocaleRows(): List<Arguments> =
      listOf(
        Arguments.of(Locale.US),
        Arguments.of(Locale.GERMANY),
        Arguments.of(Locale.FRANCE),
        Arguments.of(Locale.forLanguageTag("es")),
        Arguments.of(Locale.forLanguageTag("hi")),
        Arguments.of(Locale.forLanguageTag("pt-BR")),
        Arguments.of(Locale.SIMPLIFIED_CHINESE),
      )

    @JvmStatic
    fun widestBoundRows(): List<Arguments> =
      UnitPrefix.entries.flatMap { prefix ->
        UnitSystem.entries.flatMap { system ->
          listOf(
            Arguments.of(
              "$prefix $system significant, fixed width",
              FormatOptions(unitSystem = system, unitPrefix = prefix, fixedWidthDigits = true)
                .bare(),
            ),
            Arguments.of(
              "$prefix $system significant, free width",
              FormatOptions(unitSystem = system, unitPrefix = prefix, fixedWidthDigits = false)
                .bare(),
            ),
            Arguments.of(
              "$prefix $system fixed two decimals",
              FormatOptions(
                  unitSystem = system,
                  unitPrefix = prefix,
                  precisionMode = PrecisionMode.FIXED,
                  decimals = 2,
                )
                .bare(),
            ),
          )
        }
      }

    @JvmStatic
    fun zeroRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "zero bits per second",
          FormatOptions(
              unitSystem = UnitSystem.BITS_PER_SECOND,
              fixedWidthDigits = false,
              unitLabel = UnitLabel.FULL,
            )
            .bare(),
          "0",
          "bps",
        ),
        Arguments.of(
          "zero bytes per second",
          FormatOptions(
              unitSystem = UnitSystem.BYTES_PER_SECOND,
              fixedWidthDigits = false,
              unitLabel = UnitLabel.FULL,
            )
            .bare(),
          "0",
          "B/s",
        ),
        Arguments.of(
          "zero under a fixed KILO prefix",
          FormatOptions(
              unitSystem = UnitSystem.BITS_PER_SECOND,
              unitPrefix = UnitPrefix.KILO,
              fixedWidthDigits = false,
              unitLabel = UnitLabel.FULL,
            )
            .bare(),
          "0.00",
          "Kbps",
        ),
      )
  }
}

/** Every case here was written on a bare floor, where AUTO falls to bits and bytes. */
private fun FormatOptions.bare(): FormatOptions = copy(autoFloor = AutoFloor.BARE)
