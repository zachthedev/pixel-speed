package tools.zach.app.pixelspeed.service

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.format.FormatOptions
import tools.zach.app.pixelspeed.format.PrecisionMode
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.format.UnitPrefix
import tools.zach.app.pixelspeed.format.UnitSystem
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.ContentSettings
import tools.zach.app.pixelspeed.settings.NotificationSettings
import tools.zach.app.pixelspeed.settings.PauseSettings
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.ReadoutSurface
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.SuppressionReason

class NotificationModelMapperTest {

  private val locale: Locale = Locale.US
  private val zone: ZoneOffset = ZoneOffset.UTC
  private val formatter: SpeedFormatter = SpeedFormatter(locale)

  // The fixed clock sits on the same day as every deadline row below unless a row says otherwise.
  private val now: Instant = Instant.parse("2024-01-01T10:00:00Z")

  private fun mapper(
    sdkInt: Int,
    now: Instant = this.now,
    hour24: Boolean = false,
  ): NotificationModelMapper =
    NotificationModelMapper(
      formatter,
      locale,
      zone,
      sdkInt,
      sdkInt >= 36,
      hour24 = { hour24 },
      Clock.fixed(now, zone),
      bestPattern = CldrPatterns.bestPattern,
    )

  private fun sample(rx: Long, tx: Long, supported: Boolean = true): SpeedSample =
    SpeedSample(rx, tx, atElapsedNanos = 0L, transport = Transport.WIFI, supported = supported)

  private val fixedKiloOptions: FormatOptions =
    FormatOptions(
      unitSystem = UnitSystem.BYTES_PER_SECOND,
      unitPrefix = UnitPrefix.KILO,
      precisionMode = PrecisionMode.FIXED,
      decimals = 0,
      fixedWidthDigits = false,
      unitLabel = UnitLabel.FULL,
      binaryPrefixes = false,
    )

  // Rule 1: title.
  @ParameterizedTest(name = "{0}")
  @MethodSource("titleRows")
  fun `maps state to title`(name: String, state: MonitorState, expected: NotificationTitle) {
    val model = mapper(sdkInt = 35).map(state, sample = null, profile = Profile())
    assertEquals(expected, model.title)
  }

  companion object {
    @JvmStatic
    fun titleRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of("off gives OFF", MonitorState.Off, NotificationTitle.OFF),
        Arguments.of(
          "needs permission gives NEEDS_PERMISSION",
          MonitorState.NeedsPermission,
          NotificationTitle.NEEDS_PERMISSION,
        ),
        Arguments.of("paused gives PAUSED", MonitorState.Paused(null), NotificationTitle.PAUSED),
        Arguments.of(
          "visible running gives RUNNING",
          MonitorState.Running(emptySet(), overlayOn = true),
          NotificationTitle.RUNNING,
        ),
        Arguments.of(
          "suppressed running gives HIDDEN",
          MonitorState.Running(setOf(SuppressionReason.SCREEN_OFF), overlayOn = true),
          NotificationTitle.HIDDEN,
        ),
        Arguments.of(
          "overlay-off running with no suppressions gives RUNNING",
          MonitorState.Running(emptySet(), overlayOn = false),
          NotificationTitle.RUNNING,
        ),
        Arguments.of(
          "overlay-off running with suppressions gives HIDDEN",
          MonitorState.Running(setOf(SuppressionReason.IDLE), overlayOn = false),
          NotificationTitle.HIDDEN,
        ),
      )

    @JvmStatic
    fun promotedRows(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "promoted true on 36 with LIVE_UPDATE",
          MonitorState.Running(emptySet()),
          ReadoutSurface.LIVE_UPDATE,
          36,
          true,
        ),
        Arguments.of(
          "promoted true on 36 with BOTH",
          MonitorState.Running(emptySet()),
          ReadoutSurface.BOTH,
          36,
          true,
        ),
        Arguments.of(
          "promoted false on 35",
          MonitorState.Running(emptySet()),
          ReadoutSurface.LIVE_UPDATE,
          35,
          false,
        ),
        Arguments.of(
          "promoted false with OVERLAY",
          MonitorState.Running(emptySet()),
          ReadoutSurface.OVERLAY,
          36,
          false,
        ),
        Arguments.of(
          "promoted false when paused",
          MonitorState.Paused(null),
          ReadoutSurface.LIVE_UPDATE,
          36,
          false,
        ),
      )
  }

  // Rule 2: actions.
  @Test
  fun `running gives pause then open with the profile pause duration`() {
    val profile = Profile(pause = PauseSettings(notificationPauseDurationMinutes = 20))
    val model = mapper(sdkInt = 35).map(MonitorState.Running(emptySet()), sample = null, profile)
    assertEquals(listOf(NotificationAction.Pause(20), NotificationAction.Open), model.actions)
  }

  @Test
  fun `paused gives resume then open`() {
    val model = mapper(sdkInt = 35).map(MonitorState.Paused(null), sample = null, Profile())
    assertEquals(listOf(NotificationAction.Resume, NotificationAction.Open), model.actions)
  }

  @Test
  fun `needs permission gives grant permission then open`() {
    val model = mapper(sdkInt = 35).map(MonitorState.NeedsPermission, sample = null, Profile())
    assertEquals(
      listOf(NotificationAction.GrantPermission, NotificationAction.Open),
      model.actions,
    )
  }

  @Test
  fun `off gives open alone`() {
    val model = mapper(sdkInt = 35).map(MonitorState.Off, sample = null, Profile())
    assertEquals(listOf(NotificationAction.Open), model.actions)
  }

  // Rule 3: paused deadline formatting.
  @Test
  fun `paused with a deadline formats the time in the given locale and zone`() {
    val until = Instant.parse("2024-01-01T14:35:00Z")
    val model =
      mapper(sdkInt = 35).map(MonitorState.Paused(until), sample = null, profile = Profile())
    val body = model.body as NotificationBody.PausedUntil
    assertTrue(body.timeText.contains("2:35") && body.timeText.contains("PM"), body.timeText)
  }

  @Test
  fun `paused with a deadline on a 24-hour device drops the day period`() {
    val until = Instant.parse("2024-01-01T14:35:00Z")
    val model =
      mapper(sdkInt = 35, hour24 = true)
        .map(MonitorState.Paused(until), sample = null, profile = Profile())
    val body = model.body as NotificationBody.PausedUntil
    assertTrue(body.timeText.contains("14:35") && !body.timeText.contains("PM"), body.timeText)
  }

  @Test
  fun `paused with a deadline on another day carries the date`() {
    val until = Instant.parse("2024-01-02T14:35:00Z")
    val model =
      mapper(sdkInt = 35).map(MonitorState.Paused(until), sample = null, profile = Profile())
    val body = model.body as NotificationBody.PausedUntil
    assertTrue(body.timeText.contains("Jan 2") && body.timeText.contains("2:35"), body.timeText)
  }

  @Test
  fun `paused without a deadline has no body`() {
    val model = mapper(sdkInt = 35).map(MonitorState.Paused(null), sample = null, Profile())
    assertNull(model.body)
  }

  // Rule 3: speed body.
  @Test
  fun `running with a supported sample gives a speed body`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = true, surface = ReadoutSurface.OVERLAY)
      )
    val model =
      mapper(sdkInt = 35)
        .map(
          MonitorState.Running(emptySet()),
          sample(1_000_000L, 2_000_000L),
          (profile),
        )
    val expected =
      formatter
        .formatPair(1_000_000L, 2_000_000L, profile.content.format.copy(unitLabel = UnitLabel.FULL))
        .let { NotificationBody.Speed(it.first, it.second) }
    assertEquals(expected, model.body)
  }

  @Test
  fun `speed body carries the full label even when the profile's format is SHORT`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = true, surface = ReadoutSurface.OVERLAY),
        content = ContentSettings(format = FormatOptions(unitLabel = UnitLabel.SHORT)),
      )
    val model =
      mapper(sdkInt = 35)
        .map(MonitorState.Running(emptySet()), sample(1_000_000L, 2_000_000L), profile)
    val expected =
      formatter.formatPair(1_000_000L, 2_000_000L, FormatOptions(unitLabel = UnitLabel.FULL)).let {
        NotificationBody.Speed(it.first, it.second)
      }
    assertEquals(expected, model.body)
  }

  @Test
  fun `behind the lock screen a live sample still gives the dash speed body`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = true, surface = ReadoutSurface.OVERLAY)
      )
    val locked = MonitorState.Running(setOf(SuppressionReason.KEYGUARD))
    val model = mapper(sdkInt = 35).map(locked, sample(1_000_000L, 2_000_000L), profile)
    val body = model.body as NotificationBody.Speed
    assertEquals(SpeedFormatter.UNSUPPORTED_DASH, body.download.number)
    assertEquals(SpeedFormatter.UNSUPPORTED_DASH, body.upload.number)
  }

  @Test
  fun `running with a null sample gives the dash speed body`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = true, surface = ReadoutSurface.OVERLAY)
      )
    val model = mapper(sdkInt = 35).map(MonitorState.Running(emptySet()), sample = null, profile)
    val dash = formatter.format(Long.MIN_VALUE, profile.content.format)
    assertEquals(NotificationBody.Speed(dash, dash), model.body)
  }

  @Test
  fun `running with an unsupported sample gives the dash speed body`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = true, surface = ReadoutSurface.OVERLAY)
      )
    val model =
      mapper(sdkInt = 35)
        .map(
          MonitorState.Running(emptySet()),
          sample(1L, 1L, supported = false),
          (profile),
        )
    val dash = formatter.format(Long.MIN_VALUE, profile.content.format)
    assertEquals(NotificationBody.Speed(dash, dash), model.body)
  }

  // Rule 3: hidden body, reasons in enum order.
  @Test
  fun `hidden body lists reasons in enum order regardless of input order`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = false, surface = ReadoutSurface.OVERLAY)
      )
    val state = MonitorState.Running(setOf(SuppressionReason.IDLE, SuppressionReason.SCREEN_OFF))
    val model = mapper(sdkInt = 35).map(state, sample = null, profile)
    assertEquals(
      NotificationBody.Hidden(listOf(SuppressionReason.SCREEN_OFF, SuppressionReason.IDLE)),
      model.body,
    )
  }

  @Test
  fun `visible running without shown speed has no body`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = false, surface = ReadoutSurface.OVERLAY)
      )
    val model = mapper(sdkInt = 35).map(MonitorState.Running(emptySet()), sample = null, profile)
    assertNull(model.body)
  }

  @Test
  fun `off has no body`() {
    val model = mapper(sdkInt = 35).map(MonitorState.Off, sample = null, Profile())
    assertNull(model.body)
  }

  @Test
  fun `needs permission gives the permission hint body`() {
    val model = mapper(sdkInt = 35).map(MonitorState.NeedsPermission, sample = null, Profile())
    assertEquals(NotificationBody.NeedsPermissionHint, model.body)
  }

  // Rule 4: promoted.
  @ParameterizedTest(name = "{0}")
  @MethodSource("promotedRows")
  fun `computes promoted`(
    name: String,
    state: MonitorState,
    surface: ReadoutSurface,
    sdkInt: Int,
    expected: Boolean,
  ) {
    val profile = Profile(notification = NotificationSettings(surface = surface))
    val model = mapper(sdkInt).map(state, sample = null, profile)
    assertEquals(expected, model.promoted)
  }

  // Rule 5: short critical text.
  @Test
  fun `short critical text keeps the unit at seven characters`() {
    val profile =
      Profile(
        notification = NotificationSettings(surface = ReadoutSurface.LIVE_UPDATE),
        content = ContentSettings(format = fixedKiloOptions),
      )
    val model =
      mapper(sdkInt = 36).map(MonitorState.Running(emptySet()), sample(12_000L, 0L), profile)
    assertEquals("12 KB/s", model.shortCriticalText)
  }

  @Test
  fun `short critical text drops the unit past seven characters`() {
    val profile =
      Profile(
        notification = NotificationSettings(surface = ReadoutSurface.LIVE_UPDATE),
        content = ContentSettings(format = fixedKiloOptions),
      )
    val model =
      mapper(sdkInt = 36).map(MonitorState.Running(emptySet()), sample(123_000L, 0L), profile)
    assertEquals("123", model.shortCriticalText)
  }

  @Test
  fun `short critical text is null when the sample is null`() {
    val profile = Profile(notification = NotificationSettings(surface = ReadoutSurface.LIVE_UPDATE))
    val model = mapper(sdkInt = 36).map(MonitorState.Running(emptySet()), sample = null, profile)
    assertNull(model.shortCriticalText)
  }

  @Test
  fun `short critical text is null when the sample is unsupported`() {
    val profile = Profile(notification = NotificationSettings(surface = ReadoutSurface.LIVE_UPDATE))
    val model =
      mapper(sdkInt = 36)
        .map(
          MonitorState.Running(emptySet()),
          sample(1L, 1L, supported = false),
          (profile),
        )
    assertNull(model.shortCriticalText)
  }

  // Rule 6: metrics.
  @Test
  fun `metrics are empty below sdk 37`() {
    val profile = Profile(notification = NotificationSettings(surface = ReadoutSurface.LIVE_UPDATE))
    val model =
      mapper(sdkInt = 36).map(MonitorState.Running(emptySet()), sample(1_000L, 2_000L), profile)
    assertEquals(emptyList<NotificationMetric>(), model.metrics)
  }

  @Test
  fun `metrics hold download then upload from sdk 37 when promoted and supported`() {
    val profile =
      Profile(
        notification = NotificationSettings(surface = ReadoutSurface.LIVE_UPDATE),
        content = ContentSettings(format = fixedKiloOptions),
      )
    val model =
      mapper(sdkInt = 37).map(MonitorState.Running(emptySet()), sample(1_000L, 2_000L), profile)
    val expected =
      formatter.formatPair(1_000L, 2_000L, fixedKiloOptions).let {
        listOf(
          NotificationMetric(Direction.DOWNLOAD, it.first),
          NotificationMetric(Direction.UPLOAD, it.second),
        )
      }
    assertEquals(expected, model.metrics)
  }

  // Rule 7: showsSpeed.
  @Test
  fun `showsSpeed is true when the profile asks for it on the overlay surface`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = true, surface = ReadoutSurface.OVERLAY)
      )
    val model = mapper(sdkInt = 35).map(MonitorState.Running(emptySet()), sample = null, profile)
    assertEquals(true, model.showsSpeed)
  }

  @Test
  fun `showsSpeed is true on a non-overlay surface regardless of the flag`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = false, surface = ReadoutSurface.LIVE_UPDATE)
      )
    val model = mapper(sdkInt = 35).map(MonitorState.Running(emptySet()), sample = null, profile)
    assertEquals(true, model.showsSpeed)
  }

  @Test
  fun `showsSpeed is false when both triggers are off`() {
    val profile =
      Profile(
        notification =
          NotificationSettings(notificationShowsSpeed = false, surface = ReadoutSurface.OVERLAY)
      )
    val model = mapper(sdkInt = 35).map(MonitorState.Running(emptySet()), sample = null, profile)
    assertEquals(false, model.showsSpeed)
  }
}
