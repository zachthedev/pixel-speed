package tools.zach.app.pixelspeed.state

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.settings.BehaviorSettings
import tools.zach.app.pixelspeed.settings.OfflineMode

class SuppressionRulesTest {

  private val allSignalsOn =
    SuppressionSignals(
      screenInteractive = false,
      keyguardLocked = true,
      online = false,
      idle = true,
      fullscreen = true,
      landscape = true,
      powerSaveOn = true,
    )

  private val allSignalsCalm =
    SuppressionSignals(
      screenInteractive = true,
      keyguardLocked = false,
      online = true,
      idle = false,
      fullscreen = false,
      landscape = false,
      powerSaveOn = false,
    )

  private val allSettingsOff =
    BehaviorSettings(
      hideWhenIdle = false,
      offlineMode = OfflineMode.DASHES,
      hideInFullscreen = false,
      hideInLandscape = false,
      pauseOnBatterySaver = false,
    )

  private val allSettingsOn =
    BehaviorSettings(
      hideWhenIdle = true,
      offlineMode = OfflineMode.HIDE,
      hideInFullscreen = true,
      hideInLandscape = true,
      pauseOnBatterySaver = true,
    )

  companion object {
    @JvmStatic
    fun optionalRuleRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "OFFLINE fires only with HIDE",
          SuppressionReason.OFFLINE,
          { signals: SuppressionSignals -> signals.copy(online = false) },
          { behavior: BehaviorSettings -> behavior.copy(offlineMode = OfflineMode.HIDE) },
          { behavior: BehaviorSettings -> behavior.copy(offlineMode = OfflineMode.DASHES) },
        ),
        Arguments.of(
          "IDLE fires only with hideWhenIdle",
          SuppressionReason.IDLE,
          { signals: SuppressionSignals -> signals.copy(idle = true) },
          { behavior: BehaviorSettings -> behavior.copy(hideWhenIdle = true) },
          { behavior: BehaviorSettings -> behavior.copy(hideWhenIdle = false) },
        ),
        Arguments.of(
          "FULLSCREEN fires only with hideInFullscreen",
          SuppressionReason.FULLSCREEN,
          { signals: SuppressionSignals -> signals.copy(fullscreen = true) },
          { behavior: BehaviorSettings -> behavior.copy(hideInFullscreen = true) },
          { behavior: BehaviorSettings -> behavior.copy(hideInFullscreen = false) },
        ),
        Arguments.of(
          "LANDSCAPE fires only with hideInLandscape",
          SuppressionReason.LANDSCAPE,
          { signals: SuppressionSignals -> signals.copy(landscape = true) },
          { behavior: BehaviorSettings -> behavior.copy(hideInLandscape = true) },
          { behavior: BehaviorSettings -> behavior.copy(hideInLandscape = false) },
        ),
        Arguments.of(
          "BATTERY_SAVER fires only with pauseOnBatterySaver",
          SuppressionReason.BATTERY_SAVER,
          { signals: SuppressionSignals -> signals.copy(powerSaveOn = true) },
          { behavior: BehaviorSettings -> behavior.copy(pauseOnBatterySaver = true) },
          { behavior: BehaviorSettings -> behavior.copy(pauseOnBatterySaver = false) },
        ),
      )
  }

  @Test
  fun `every signal on with every setting off gives only the fixed reasons`() {
    val result = SuppressionRules.evaluate(allSettingsOff, allSignalsOn)
    assertEquals(setOf(SuppressionReason.SCREEN_OFF, SuppressionReason.KEYGUARD), result)
  }

  @Test
  fun `every signal on with every setting on gives all seven reasons`() {
    val result = SuppressionRules.evaluate(allSettingsOn, allSignalsOn)
    assertEquals(SuppressionReason.entries.toSet(), result)
  }

  @Test
  fun `all signals calm gives the empty set`() {
    val result = SuppressionRules.evaluate(allSettingsOn, allSignalsCalm)
    assertEquals(emptySet<SuppressionReason>(), result)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("optionalRuleRows")
  fun `optional rule fires with its setting on and not off`(
    name: String,
    reason: SuppressionReason,
    withSignal: (SuppressionSignals) -> SuppressionSignals,
    withSettingOn: (BehaviorSettings) -> BehaviorSettings,
    withSettingOff: (BehaviorSettings) -> BehaviorSettings,
  ) {
    val signals = withSignal(allSignalsCalm)
    val on = SuppressionRules.evaluate(withSettingOn(BehaviorSettings()), signals)
    val off = SuppressionRules.evaluate(withSettingOff(BehaviorSettings()), signals)
    assertEquals(true, reason in on)
    assertEquals(false, reason in off)
  }
}
