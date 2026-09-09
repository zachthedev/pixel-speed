package tools.zach.app.pixelspeed.ui.settings

/** Turns a duration in minutes into its label's plural unit and quantity. */
object PauseDurationLabels {
  enum class PauseUnit {
    MINUTES,
    HOURS,
    DAYS,
  }

  data class Resource(val unit: PauseUnit, val quantity: Int)

  /** Minutes under an hour stay as minutes, exact hours collapse to hours, a full day to days. */
  fun resourceFor(minutes: Int): Resource =
    when {
      minutes >= MINUTES_PER_DAY -> Resource(PauseUnit.DAYS, minutes / MINUTES_PER_DAY)
      minutes >= MINUTES_PER_HOUR && minutes % MINUTES_PER_HOUR == 0 ->
        Resource(PauseUnit.HOURS, minutes / MINUTES_PER_HOUR)
      else -> Resource(PauseUnit.MINUTES, minutes)
    }

  private const val MINUTES_PER_HOUR: Int = 60
  private const val MINUTES_PER_DAY: Int = 1440
}
