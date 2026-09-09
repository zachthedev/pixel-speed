package tools.zach.app.pixelspeed.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.format.FormatOptions
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.overlay.LandscapeAnchorMode
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.overlay.StatusBarFit
import tools.zach.app.pixelspeed.sampling.CounterSourceKind
import tools.zach.app.pixelspeed.sampling.Smoothing

/** One row per key in the settings schema, so a broken default reads as the field that broke. */
class SettingsTest {
  companion object {
    private val settings = Settings()
    private val profile = settings.activeProfile

    @JvmStatic
    fun defaultRows(): List<Arguments> =
      listOf(
        Arguments.of("schemaVersion", SettingsMigrations.CURRENT_VERSION, settings.schemaVersion),
        Arguments.of("enabled", false, settings.enabled),
        Arguments.of("startOnBoot", true, settings.startOnBoot),
        Arguments.of("paused", false, settings.paused),
        Arguments.of("pausedUntilEpochMillis", null, settings.pausedUntilEpochMillis),
        Arguments.of("profiles.size", 1, settings.profiles.size),
        Arguments.of("activeProfileId", "default", settings.activeProfileId),
        Arguments.of("automationIntents", false, settings.automationIntents),
        Arguments.of("advancedMode", false, settings.advancedMode),
        Arguments.of("profile.id", "default", profile.id),
        Arguments.of("profile.name", "Default", profile.name),
        Arguments.of("content.showDownload", true, profile.content.showDownload),
        Arguments.of("content.showUpload", true, profile.content.showUpload),
        Arguments.of("content.layout", Layout.ONE_LINE, profile.content.layout),
        Arguments.of(
          "content.directionOrder",
          DirectionOrder.DOWNLOAD_FIRST,
          profile.content.directionOrder,
        ),
        Arguments.of(
          "content.directionMarker",
          DirectionMarker.ARROWS,
          profile.content.directionMarker,
        ),
        Arguments.of("content.format", FormatOptions(), profile.content.format),
        Arguments.of("content.showNetworkGlyph", false, profile.content.showNetworkGlyph),
        Arguments.of("position.anchor", Anchor.STATUS_BAR_CUTOUT, profile.position.anchor),
        Arguments.of("position.offsetXdp", 0, profile.position.offsetXdp),
        Arguments.of("position.offsetYdp", 0, profile.position.offsetYdp),
        Arguments.of("position.customXdp", 0, profile.position.customXdp),
        Arguments.of("position.customYdp", 0, profile.position.customYdp),
        Arguments.of(
          "position.landscapeAnchorMode",
          LandscapeAnchorMode.SAME,
          profile.position.landscapeAnchorMode,
        ),
        Arguments.of("position.landscapeAnchor", Anchor.TOP_END, profile.position.landscapeAnchor),
        Arguments.of("position.avoidCutout", true, profile.position.avoidCutout),
        Arguments.of("position.textSizeSp", 14, profile.position.textSizeSp),
        Arguments.of("position.chipPaddingDp", 12, profile.position.chipPaddingDp),
        Arguments.of("position.minWidthDp", 0, profile.position.minWidthDp),
        Arguments.of("appearance.style", OverlayStyle.CHIP, profile.appearance.style),
        Arguments.of("appearance.statusBarFit", StatusBarFit.AUTO, profile.appearance.statusBarFit),
        Arguments.of("appearance.colorMode", ColorMode.DYNAMIC, profile.appearance.colorMode),
        Arguments.of("appearance.backgroundColor", null, profile.appearance.backgroundColor),
        Arguments.of("appearance.textColor", null, profile.appearance.textColor),
        Arguments.of("appearance.windowOpacity", 0.8f, profile.appearance.windowOpacity),
        Arguments.of("appearance.cornerRadiusDp", 999, profile.appearance.cornerRadiusDp),
        Arguments.of(
          "appearance.fontFamily",
          FontFamilyChoice.SYSTEM,
          profile.appearance.fontFamily,
        ),
        Arguments.of(
          "appearance.fontWeight",
          FontWeightChoice.MEDIUM,
          profile.appearance.fontWeight,
        ),
        Arguments.of("appearance.colorByDirection", false, profile.appearance.colorByDirection),
        Arguments.of(
          "appearance.thresholdHighlight",
          false,
          profile.appearance.thresholdHighlight,
        ),
        Arguments.of(
          "appearance.thresholdBytesPerSecond",
          1_250_000L,
          profile.appearance.thresholdBytesPerSecond,
        ),
        Arguments.of("appearance.blurBehind", false, profile.appearance.blurBehind),
        Arguments.of("behavior.updateIntervalMs", 1000, profile.behavior.updateIntervalMs),
        Arguments.of("behavior.smoothing", Smoothing.NONE, profile.behavior.smoothing),
        Arguments.of("behavior.hideWhenIdle", false, profile.behavior.hideWhenIdle),
        Arguments.of(
          "behavior.idleThresholdBytesPerSecond",
          1024L,
          profile.behavior.idleThresholdBytesPerSecond,
        ),
        Arguments.of("behavior.idleDelayMs", 5000, profile.behavior.idleDelayMs),
        Arguments.of("behavior.offlineMode", OfflineMode.DASHES, profile.behavior.offlineMode),
        Arguments.of("behavior.hideInFullscreen", false, profile.behavior.hideInFullscreen),
        Arguments.of("behavior.hideInLandscape", false, profile.behavior.hideInLandscape),
        Arguments.of(
          "behavior.pauseOnBatterySaver",
          false,
          profile.behavior.pauseOnBatterySaver,
        ),
        Arguments.of(
          "pause.pauseDurationsMinutes",
          listOf(5, 15, 60),
          profile.pause.pauseDurationsMinutes,
        ),
        Arguments.of(
          "pause.tilePauseDurationMinutes",
          null,
          profile.pause.tilePauseDurationMinutes,
        ),
        Arguments.of(
          "pause.notificationPauseDurationMinutes",
          15,
          profile.pause.notificationPauseDurationMinutes,
        ),
        Arguments.of(
          "notification.notificationShowsSpeed",
          false,
          profile.notification.notificationShowsSpeed,
        ),
        Arguments.of(
          "notification.surface",
          ReadoutSurface.OVERLAY,
          profile.notification.surface,
        ),
        Arguments.of(
          "counterSource",
          CounterSourceKind.PHYSICAL_INTERFACES,
          profile.counterSource,
        ),
      )
  }

  @ParameterizedTest(name = "{0} defaults to {1}")
  @MethodSource("defaultRows")
  fun `default value matches schema`(name: String, expected: Any?, actual: Any?) {
    assertEquals(expected, actual, name)
  }

  @Test
  fun `activeProfile falls back to the first profile when the id matches nothing`() {
    val first = Profile(id = "a", name = "A")
    val second = Profile(id = "b", name = "B")
    val settingsWithMissingActive =
      Settings(profiles = listOf(first, second), activeProfileId = "missing")

    assertEquals(first, settingsWithMissingActive.activeProfile)
  }

  @Test
  fun `activeProfile returns the matching profile when the id matches`() {
    val first = Profile(id = "a", name = "A")
    val second = Profile(id = "b", name = "B")
    val settingsWithMatchingActive =
      Settings(profiles = listOf(first, second), activeProfileId = "b")

    assertEquals(second, settingsWithMatchingActive.activeProfile)
  }

  @Test
  fun `activeProfile returns the default profile when profiles is empty`() {
    val settingsWithNoProfiles = Settings(profiles = emptyList())

    assertEquals(SettingsDefaults.defaultProfile, settingsWithNoProfiles.activeProfile)
  }
}
