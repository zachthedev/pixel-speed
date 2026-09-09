package tools.zach.app.pixelspeed.ui.home

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.format.FormatOptions
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.format.UnitSystem
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.settings.AppearanceSettings
import tools.zach.app.pixelspeed.settings.BehaviorSettings
import tools.zach.app.pixelspeed.settings.ContentSettings
import tools.zach.app.pixelspeed.settings.Layout
import tools.zach.app.pixelspeed.settings.PauseSettings
import tools.zach.app.pixelspeed.settings.PositionSettings
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings

class HomeSummariesTest {

  private fun settingsWith(
    profile: Profile,
    startOnBoot: Boolean = true,
    advancedMode: Boolean = false,
  ): Settings =
    Settings(
      profiles = listOf(profile),
      activeProfileId = profile.id,
      startOnBoot = startOnBoot,
      advancedMode = advancedMode,
    )

  @ParameterizedTest(name = "{0}")
  @MethodSource("contentRows")
  fun `the content line decides the directions and drops the unit when its label is off`(
    name: String,
    showDownload: Boolean,
    showUpload: Boolean,
    layout: Layout,
    style: OverlayStyle,
    format: FormatOptions,
    expectedDirections: ShownDirections,
    expectedUnit: String,
  ) {
    val profile =
      Profile(
        content =
          ContentSettings(
            showDownload = showDownload,
            showUpload = showUpload,
            layout = layout,
            format = format,
          ),
        appearance = AppearanceSettings(style = style),
      )
    val data = contentSummaryData(settingsWith(profile), Locale.US)
    assertEquals(expectedDirections, data.directions, name)
    assertEquals(layout, data.layout, name)
    assertEquals(expectedUnit, data.unit, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("positionRows")
  fun `the position line carries the anchor`(name: String, anchor: Anchor) {
    val profile = Profile(position = PositionSettings(anchor = anchor))
    assertEquals(anchor, positionSummaryData(settingsWith(profile)).anchor, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("appearanceRows")
  fun `the appearance line rounds the opacity to a whole percent`(
    name: String,
    opacity: Float,
    expectedPercent: Int,
  ) {
    val profile = Profile(appearance = AppearanceSettings(windowOpacity = opacity))
    val data = appearanceSummaryData(settingsWith(profile))
    assertEquals(expectedPercent, data.opacityPercent, name)
    assertEquals(opacity, data.windowOpacity, name)
    assertEquals(profile.appearance.style, data.style, name)
    assertEquals(profile.appearance.colorMode, data.colorMode, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("behaviorRows")
  fun `the behavior line lists the rules that are on in screen order`(
    name: String,
    hideWhenIdle: Boolean,
    hideInFullscreen: Boolean,
    hideInLandscape: Boolean,
    pauseOnBatterySaver: Boolean,
    updateIntervalMs: Int,
    expectedRules: List<HideRule>,
  ) {
    val profile =
      Profile(
        behavior =
          BehaviorSettings(
            hideWhenIdle = hideWhenIdle,
            hideInFullscreen = hideInFullscreen,
            hideInLandscape = hideInLandscape,
            pauseOnBatterySaver = pauseOnBatterySaver,
            updateIntervalMs = updateIntervalMs,
          )
      )
    val data = behaviorSummaryData(settingsWith(profile))
    assertEquals(expectedRules, data.activeRules, name)
    assertEquals(updateIntervalMs, data.updateIntervalMs, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profilesRows")
  fun `the profiles line names the active profile only once there are two or more`(
    name: String,
    profiles: List<Profile>,
    activeId: String,
    expectedCount: Int,
    expectedName: String?,
  ) {
    val data = profilesSummaryData(Settings(profiles = profiles, activeProfileId = activeId))
    assertEquals(expectedCount, data.count, name)
    assertEquals(expectedName, data.activeName, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("systemRows")
  fun `the system line carries boot start and whether advanced mode is on`(
    name: String,
    startOnBoot: Boolean,
    advancedMode: Boolean,
  ) {
    val data =
      systemSummaryData(
        settingsWith(Profile(), startOnBoot = startOnBoot, advancedMode = advancedMode)
      )
    assertEquals(startOnBoot, data.startOnBoot, name)
    assertEquals(advancedMode, data.advancedMode, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pauseRows")
  fun `the pause line carries the configured durations`(
    name: String,
    durationsMinutes: List<Int>,
  ) {
    val profile = Profile(pause = PauseSettings(durationsMinutes))
    assertEquals(durationsMinutes, pauseSummaryData(settingsWith(profile)).durationsMinutes, name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("partsRows")
  fun `a line keeps only the parts that carry text`(
    name: String,
    parts: List<String?>,
    expected: List<String>,
  ) {
    assertEquals(expected, summaryParts(*parts.toTypedArray()), name)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profilesRows")
  fun `one profile leaves the active name out`(
    name: String,
    profiles: List<Profile>,
    activeId: String,
    expectedCount: Int,
    expectedName: String?,
  ) {
    if (expectedCount > 1) return
    assertNull(
      profilesSummaryData(Settings(profiles = profiles, activeProfileId = activeId)).activeName,
      name,
    )
  }

  companion object {
    @JvmStatic
    fun contentRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "the single look keeps the full unit on the two-line layout",
          true,
          true,
          Layout.TWO_LINES,
          OverlayStyle.SINGLE,
          FormatOptions(),
          ShownDirections.BOTH,
          "Mbps",
        ),
        Arguments.of(
          "the stack look drops the unit on the one-line layout",
          true,
          true,
          Layout.ONE_LINE,
          OverlayStyle.STACK,
          FormatOptions(),
          ShownDirections.BOTH,
          "",
        ),
        Arguments.of(
          "both directions, one line, bits with the full label",
          true,
          true,
          Layout.ONE_LINE,
          OverlayStyle.CHIP,
          FormatOptions(unitLabel = UnitLabel.FULL),
          ShownDirections.BOTH,
          "Mbps",
        ),
        Arguments.of(
          "download only, two lines resolve to the stack, which drops the label",
          true,
          false,
          Layout.TWO_LINES,
          OverlayStyle.CHIP,
          FormatOptions(unitSystem = UnitSystem.BYTES_PER_SECOND),
          ShownDirections.DOWNLOAD_ONLY,
          "",
        ),
        Arguments.of(
          "upload only, download-only layout",
          false,
          true,
          Layout.DOWNLOAD_ONLY,
          OverlayStyle.CHIP,
          FormatOptions(unitLabel = UnitLabel.FULL),
          ShownDirections.UPLOAD_ONLY,
          "Mbps",
        ),
        Arguments.of(
          "neither direction, combined layout, unit label off",
          false,
          false,
          Layout.COMBINED,
          OverlayStyle.CHIP,
          FormatOptions(unitLabel = UnitLabel.NONE),
          ShownDirections.NONE,
          "",
        ),
      )

    @JvmStatic
    fun positionRows(): List<Arguments> =
      listOf(
        Arguments.of("a status bar anchor", Anchor.STATUS_BAR_CUTOUT),
        Arguments.of("a custom anchor", Anchor.CUSTOM),
      )

    @JvmStatic
    fun appearanceRows(): List<Arguments> =
      listOf(
        Arguments.of("0.8 becomes 80", 0.8f, 80),
        Arguments.of("fully opaque becomes 100", 1.0f, 100),
        Arguments.of("fully clear becomes 0", 0.0f, 0),
        Arguments.of("a half percent rounds up", 0.305f, 31),
        Arguments.of("just under a half percent rounds down", 0.304f, 30),
      )

    @JvmStatic
    fun behaviorRows(): List<Arguments> =
      listOf(
        Arguments.of("no rules on", false, false, false, false, 1000, emptyList<HideRule>()),
        Arguments.of("one rule on", true, false, false, false, 1500, listOf(HideRule.IDLE)),
        Arguments.of(
          "every rule on, in screen order",
          true,
          true,
          true,
          true,
          2000,
          listOf(HideRule.IDLE, HideRule.FULLSCREEN, HideRule.LANDSCAPE, HideRule.BATTERY_SAVER),
        ),
        Arguments.of(
          "the last two rules on",
          false,
          false,
          true,
          true,
          5000,
          listOf(HideRule.LANDSCAPE, HideRule.BATTERY_SAVER),
        ),
      )

    @JvmStatic
    fun profilesRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "one profile",
          listOf(Profile(id = "default", name = "Default")),
          "default",
          1,
          null,
        ),
        Arguments.of(
          "three profiles with a non-default active",
          listOf(
            Profile(id = "default", name = "Default"),
            Profile(id = "work", name = "Work"),
            Profile(id = "home", name = "Home"),
          ),
          "work",
          3,
          "Work",
        ),
      )

    @JvmStatic
    fun systemRows(): List<Arguments> =
      listOf(
        Arguments.of("boot start on, regular mode", true, false),
        Arguments.of("boot start off, regular mode", false, false),
        Arguments.of("boot start on, advanced mode", true, true),
        Arguments.of("boot start off, advanced mode", false, true),
      )

    @JvmStatic
    fun pauseRows(): List<Arguments> =
      listOf(
        Arguments.of("the default durations", listOf(5, 15, 60)),
        Arguments.of("a single duration", listOf(30)),
      )

    @JvmStatic
    fun partsRows(): List<Arguments> =
      listOf(
        Arguments.of("all present", listOf("a", "b", "c"), listOf("a", "b", "c")),
        Arguments.of("an empty unit drops out", listOf("a", "b", ""), listOf("a", "b")),
        Arguments.of("a null optional drops out", listOf("a", null), listOf("a")),
        Arguments.of("blank is empty", listOf(" ", "a"), listOf("a")),
        Arguments.of("nothing", listOf<String?>(null, ""), emptyList<String>()),
      )
  }
}
