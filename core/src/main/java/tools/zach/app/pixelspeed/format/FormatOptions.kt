package tools.zach.app.pixelspeed.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Bits per second is what carriers and speed tests quote; bytes per second is what downloads show.
 */
@Serializable
enum class UnitSystem {
  BITS_PER_SECOND,
  BYTES_PER_SECOND,
}

/**
 * AUTO climbs the prefixes to keep the number under 1000; a fixed rung stops the label flipping.
 */
@Serializable
enum class UnitPrefix {
  AUTO,
  KILO,
  MEGA,
  GIGA,
}

/**
 * The lowest prefix AUTO drops to. Below it the number is a fraction of that unit, so a reader who
 * knows one unit reads every rate in it; BARE lets AUTO fall to bits and bytes per second.
 */
@Serializable
enum class AutoFloor {
  BARE,
  KILO,
  MEGA,
}

/** SIGNIFICANT keeps one digit count across magnitudes; FIXED keeps a set number of decimals. */
@Serializable
enum class PrecisionMode {
  SIGNIFICANT,
  FIXED,
}

/**
 * FULL spells out the unit (Kbps, MB/s). SHORT keeps the prefix and the bit or byte letter with the
 * "ps" and the "/s" dropped (Kb, MB). NONE renders no unit at all.
 */
@Serializable
enum class UnitLabel {
  /**
   * Dropped on the two-line stack and the flank halves, whose number then stays in megabits, and
   * full everywhere else.
   */
  AUTO,
  FULL,
  SHORT,
  NONE,
}

/** Everything the formatter needs from the active profile. */
@Serializable
data class FormatOptions(
  val unitSystem: UnitSystem = UnitSystem.BITS_PER_SECOND,
  val unitPrefix: UnitPrefix = UnitPrefix.AUTO,
  val autoFloor: AutoFloor = AutoFloor.MEGA,
  val precisionMode: PrecisionMode = PrecisionMode.SIGNIFICANT,
  val significantFigures: Int = 3,
  val decimals: Int = 1,
  val binaryPrefixes: Boolean = false,
  val unitLabel: UnitLabel = UnitLabel.AUTO,
  val fixedWidthDigits: Boolean = true,
  /**
   * AUTO never climbs past [autoFloor], so every number is a fraction or a multiple of the floor's
   * unit and a bare number keeps one meaning. Derived by [forStackAndHalves], never stored.
   */
  @Transient val pinnedToFloor: Boolean = false,
)

/**
 * The options the two-line stack and the flank halves format with. Under the AUTO label they draw
 * no unit, so their number must keep one meaning: with the AUTO prefix over a kilo or mega floor
 * the number is pinned to that floor's unit however high the rate climbs, so a gigabit link reads
 * "1200" and never a bare "1.20". A bare floor or a fixed prefix keeps the short label instead,
 * since a bare number there could belong to several tiers. Every other label stands.
 */
fun FormatOptions.forStackAndHalves(): FormatOptions =
  when {
    unitLabel != UnitLabel.AUTO -> this
    unitPrefix == UnitPrefix.AUTO && autoFloor != AutoFloor.BARE ->
      copy(unitLabel = UnitLabel.NONE, pinnedToFloor = true)
    else -> copy(unitLabel = UnitLabel.SHORT)
  }
