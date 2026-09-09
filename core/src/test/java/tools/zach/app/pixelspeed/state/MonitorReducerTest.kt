package tools.zach.app.pixelspeed.state

import java.time.Instant
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.settings.ReadoutSurface

class MonitorReducerTest {

  private fun baseInputs(
    enabled: Boolean = true,
    hasOverlayPermission: Boolean = true,
    paused: Boolean = false,
    pausedUntilEpochMillis: Long? = null,
    nowEpochMillis: Long = 1000L,
    suppressions: Set<SuppressionReason> = emptySet(),
    surface: ReadoutSurface = ReadoutSurface.OVERLAY,
  ): MonitorInputs =
    MonitorInputs(
      enabled = enabled,
      hasOverlayPermission = hasOverlayPermission,
      paused = paused,
      pausedUntilEpochMillis = pausedUntilEpochMillis,
      nowEpochMillis = nowEpochMillis,
      suppressions = suppressions,
      surface = surface,
    )

  companion object {
    @JvmStatic
    fun stateRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "disabled wins over everything",
          MonitorInputs(
            enabled = false,
            hasOverlayPermission = false,
            paused = true,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.Off,
        ),
        Arguments.of(
          "missing permission gives NeedsPermission",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = false,
            paused = false,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.NeedsPermission,
        ),
        Arguments.of(
          "no permission beats a pending pause",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = false,
            paused = true,
            pausedUntilEpochMillis = 2000L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.NeedsPermission,
        ),
        Arguments.of(
          "paused with no deadline",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.Paused(null),
        ),
        Arguments.of(
          "paused with a deadline ahead of now",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = 2000L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.Paused(Instant.ofEpochMilli(2000L)),
        ),
        Arguments.of(
          "deadline exactly equal to now is not paused",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = 1000L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.Running(emptySet(), overlayOn = true),
        ),
        Arguments.of(
          "deadline one millisecond ahead is paused",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = 1001L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.Paused(Instant.ofEpochMilli(1001L)),
        ),
        Arguments.of(
          "running with surface BOTH keeps the overlay on",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = false,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.BOTH,
          ),
          MonitorState.Running(emptySet(), overlayOn = true),
        ),
        Arguments.of(
          "running with surface LIVE_UPDATE turns the overlay off",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = false,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.LIVE_UPDATE,
          ),
          MonitorState.Running(emptySet(), overlayOn = false),
        ),
        Arguments.of(
          "running with surface OVERLAY keeps the overlay on",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = false,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          MonitorState.Running(emptySet(), overlayOn = true),
        ),
      )

    @JvmStatic
    fun resumeDelayRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "no pending pause gives null",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = false,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          null,
        ),
        Arguments.of(
          "pause with no deadline gives null",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = null,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          null,
        ),
        Arguments.of(
          "expired deadline gives null",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = 1000L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          null,
        ),
        Arguments.of(
          "deadline one millisecond ahead gives delay 1",
          MonitorInputs(
            enabled = true,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = 1001L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          1L,
        ),
        Arguments.of(
          "disabled with a pending deadline gives null",
          MonitorInputs(
            enabled = false,
            hasOverlayPermission = true,
            paused = true,
            pausedUntilEpochMillis = 2000L,
            nowEpochMillis = 1000L,
            suppressions = emptySet<SuppressionReason>(),
            surface = ReadoutSurface.OVERLAY,
          ),
          null,
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateRows")
  fun `reduce follows the precedence rules`(
    name: String,
    inputs: MonitorInputs,
    expected: MonitorState,
  ) {
    assertEquals(expected, MonitorReducer.reduce(inputs))
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resumeDelayRows")
  fun `resumeDelayMillis reports a pending deadline only`(
    name: String,
    inputs: MonitorInputs,
    expected: Long?,
  ) {
    assertEquals(expected, MonitorReducer.resumeDelayMillis(inputs))
  }

  @Test
  fun `Running visible is false with any suppression`() {
    val running =
      MonitorReducer.reduce(baseInputs(suppressions = setOf(SuppressionReason.IDLE)))
        as MonitorState.Running
    assertEquals(false, running.visible)
  }

  @Test
  fun `Running sampling is false only when a reason stops sampling`() {
    val nonStopping =
      MonitorReducer.reduce(baseInputs(suppressions = setOf(SuppressionReason.IDLE)))
        as MonitorState.Running
    assertEquals(true, nonStopping.sampling)

    val stopping =
      MonitorReducer.reduce(baseInputs(suppressions = setOf(SuppressionReason.SCREEN_OFF)))
        as MonitorState.Running
    assertEquals(false, stopping.sampling)
  }

  @Test
  fun `the lock screen hides the readout but keeps sampling so the unlock shows a reading`() {
    val locked =
      MonitorReducer.reduce(baseInputs(suppressions = setOf(SuppressionReason.KEYGUARD)))
        as MonitorState.Running
    assertEquals(false, locked.visible)
    assertEquals(true, locked.sampling)
  }
}
