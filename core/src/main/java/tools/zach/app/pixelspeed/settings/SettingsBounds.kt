package tools.zach.app.pixelspeed.settings

/**
 * Every bound the settings screens and the import checker share, so a value a slider can reach is a
 * value an import accepts. Import checks the stored unit; a screen converts to the unit its slider
 * shows through the factors here. The one asymmetry is the interval floor, which the platform
 * raises at runtime through [updateIntervalFloorMs]: import keeps the lower floor so a file from an
 * older device still loads, and the sampler clamps.
 */
object SettingsBounds {
  const val BYTES_PER_KB: Long = 1_000L
  const val BYTES_PER_MEGABIT: Long = 125_000L
  const val MILLIS_PER_SECOND: Int = 1_000

  /** How many profiles a settings file holds. */
  val PROFILE_COUNT: IntRange = 1..12
  /** The length of a profile's id and name. */
  val TEXT_LENGTH: IntRange = 1..64
  /** Significant figures in a formatted rate. */
  val SIGNIFICANT_FIGURES: IntRange = 1..6
  /** Decimal places in a fixed-decimals rate. */
  val DECIMALS: IntRange = 0..6
  /** The readout's text size. */
  val TEXT_SIZE_SP: IntRange = 9..24
  /** The pill's side padding. */
  val CHIP_PADDING_DP: IntRange = 0..16
  /** The pill's minimum width; 0 lets the text set it. */
  val MIN_WIDTH_DP: IntRange = 0..240
  /** A nudge from the anchor in either axis. */
  val OFFSET_DP: IntRange = -200..200
  /** A dropped position in dp; wider than any display, so a foldable's outer edge still fits. */
  val CUSTOM_POSITION_DP: IntRange = -4096..4096
  /**
   * The window's opacity as the slider shows it. The top of the range passes the platform's
   * touch-obscuring limit, so the screen warns there rather than the bound refusing it.
   */
  val WINDOW_OPACITY_PERCENT: IntRange = 30..100
  /** The window's opacity as the window manager takes it. */
  val WINDOW_OPACITY: ClosedFloatingPointRange<Float> =
    WINDOW_OPACITY_PERCENT.first / 100f..WINDOW_OPACITY_PERCENT.last / 100f
  /** The pill's corner radius; the top of the range means fully round. */
  val CORNER_RADIUS_DP: IntRange = 0..999
  /** A rate threshold as the slider shows it. */
  val THRESHOLD_MBPS: IntRange = 1..1000
  /** A rate threshold as the sampler compares it. */
  val THRESHOLD_BYTES_PER_SECOND: LongRange =
    THRESHOLD_MBPS.first * BYTES_PER_MEGABIT..THRESHOLD_MBPS.last * BYTES_PER_MEGABIT
  /** The sampling interval. */
  val UPDATE_INTERVAL_MS: IntRange = 500..5000
  /** The rate under which the connection counts as idle, as the slider shows it. */
  val IDLE_THRESHOLD_KBPS: IntRange = 0..1000
  /** The idle rate as the tracker compares it. */
  val IDLE_THRESHOLD_BYTES_PER_SECOND: LongRange =
    IDLE_THRESHOLD_KBPS.first * BYTES_PER_KB..IDLE_THRESHOLD_KBPS.last * BYTES_PER_KB
  /** How long a connection stays idle before the overlay hides, as the slider shows it. */
  val IDLE_DELAY_SECONDS: IntRange = 1..60
  /** The idle delay as the tracker measures it. */
  val IDLE_DELAY_MS: IntRange =
    IDLE_DELAY_SECONDS.first * MILLIS_PER_SECOND..IDLE_DELAY_SECONDS.last * MILLIS_PER_SECOND
  /** How many pause presets a profile offers. */
  val PAUSE_DURATION_COUNT: IntRange = 1..5
  /** A pause duration: one minute to one day. */
  val PAUSE_MINUTES: IntRange = 1..1440

  /**
   * The shortest interval the platform's counters honor: Android 15 caches TrafficStats reads for
   * about a second, so a shorter tick reads the same total twice and shows zero.
   */
  fun updateIntervalFloorMs(sdkInt: Int): Int =
    if (sdkInt >= COUNTER_CACHE_SDK) COUNTER_CACHE_INTERVAL_MS else UPDATE_INTERVAL_MS.first

  /**
   * The period the sampler ticks at for a profile's [updateIntervalMs]: the floored interval, held
   * a margin past the counter cache where the platform has one. The cache refreshes on the first
   * read at least a second after the last refresh, so a tick every exact second lands inside the
   * old window about half the time and reads the same counters twice; a tick just past the second
   * never does, and a longer one is past it already. The setting keeps its own label.
   */
  fun samplerTickMs(updateIntervalMs: Int, sdkInt: Int): Int {
    val floored: Int = maxOf(updateIntervalMs, updateIntervalFloorMs(sdkInt))
    val cleared: Int = COUNTER_CACHE_INTERVAL_MS + COUNTER_CACHE_MARGIN_MS
    return if (sdkInt >= COUNTER_CACHE_SDK && floored < cleared) cleared else floored
  }

  private const val COUNTER_CACHE_SDK: Int = 35
  private const val COUNTER_CACHE_INTERVAL_MS: Int = 1000
  private const val COUNTER_CACHE_MARGIN_MS: Int = 100
}
