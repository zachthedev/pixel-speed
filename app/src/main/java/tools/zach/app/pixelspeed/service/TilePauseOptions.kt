package tools.zach.app.pixelspeed.service

import tools.zach.app.pixelspeed.settings.Profile

/**
 * What the tile dialog's pause control offers for one profile: the profile's durations plus "until
 * I resume" as null, and the one a plain tap uses, which is the tile pause duration when it is set
 * and "until I resume" otherwise.
 */
object TilePauseOptions {
  fun options(profile: Profile): List<Int?> =
    profile.pause.pauseDurationsMinutes.distinct().sorted() + listOf(null)

  fun default(profile: Profile): Int? = profile.pause.tilePauseDurationMinutes
}
