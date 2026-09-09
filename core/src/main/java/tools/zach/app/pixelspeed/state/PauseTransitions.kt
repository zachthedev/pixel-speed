package tools.zach.app.pixelspeed.state

import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsBounds

/**
 * The settings transforms behind pause, resume, and profile cycling. Pure functions, so every
 * surface changes the persisted state the same way.
 */
object PauseTransitions {
  private const val MILLIS_PER_MINUTE: Long = 60_000L

  /** The shortest pause every surface accepts, from [SettingsBounds.PAUSE_MINUTES]. */
  val MIN_MINUTES: Int = SettingsBounds.PAUSE_MINUTES.first

  /** The longest pause every surface accepts, from [SettingsBounds.PAUSE_MINUTES]. */
  val MAX_MINUTES: Int = SettingsBounds.PAUSE_MINUTES.last

  /** True for a duration inside [SettingsBounds.PAUSE_MINUTES]; the check for outside input. */
  fun isValidDuration(minutes: Int): Boolean = minutes in SettingsBounds.PAUSE_MINUTES

  /** A pause is live while the flag is set and the deadline, if any, is still ahead. */
  fun isPaused(settings: Settings, nowEpochMillis: Long): Boolean {
    val until = settings.pausedUntilEpochMillis
    return settings.paused && (until == null || until > nowEpochMillis)
  }

  /** [durationMinutes] null means until resumed. A non-positive duration is treated as null. */
  fun pause(settings: Settings, nowEpochMillis: Long, durationMinutes: Int?): Settings {
    val until = durationMinutes?.takeIf { it > 0 }?.let { nowEpochMillis + it * MILLIS_PER_MINUTE }
    return settings.copy(paused = true, pausedUntilEpochMillis = until)
  }

  /** Clears the pause flag and its deadline. */
  fun resume(settings: Settings): Settings =
    settings.copy(paused = false, pausedUntilEpochMillis = null)

  /** Resumes a live pause, otherwise starts one. An expired deadline counts as not paused. */
  fun toggle(settings: Settings, nowEpochMillis: Long, durationMinutes: Int?): Settings =
    if (isPaused(settings, nowEpochMillis)) resume(settings)
    else pause(settings, nowEpochMillis, durationMinutes)
}
