package tools.zach.app.pixelspeed.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.settings.PauseSettings
import tools.zach.app.pixelspeed.settings.Profile

class TilePauseOptionsTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `the dialog offers the profile's durations then until resumed, and taps the tile default`(
    name: String,
    pause: PauseSettings,
    options: List<Int?>,
    default: Int?,
  ) {
    val profile = Profile(pause = pause)
    assertEquals(options, TilePauseOptions.options(profile), "$name: options")
    assertEquals(default, TilePauseOptions.default(profile), "$name: default")
  }

  companion object {
    @JvmStatic
    fun rows(): List<Arguments> =
      listOf(
        Arguments.of(
          "defaults: three durations, no tile duration, so a tap pauses until resumed",
          PauseSettings(),
          listOf(5, 15, 60, null),
          null,
        ),
        Arguments.of(
          "a tile duration is the tap's choice",
          PauseSettings(pauseDurationsMinutes = listOf(30), tilePauseDurationMinutes = 30),
          listOf(30, null),
          30,
        ),
        Arguments.of(
          "unsorted and repeated durations come out sorted and once",
          PauseSettings(pauseDurationsMinutes = listOf(60, 5, 60, 15)),
          listOf(5, 15, 60, null),
          null,
        ),
      )
  }
}
