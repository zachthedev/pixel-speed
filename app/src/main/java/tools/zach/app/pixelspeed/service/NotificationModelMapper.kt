package tools.zach.app.pixelspeed.service

import android.text.format.DateFormat
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import tools.zach.app.pixelspeed.format.FormattedSpeed
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.ReadoutSurface
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.SuppressionReason

private const val SHORT_CRITICAL_TEXT_MAX_LENGTH: Int = 7
/**
 * The first SDK with `Notification.MetricStyle`; the mapper and the renderer gate on it together.
 */
internal const val METRICS_MIN_SDK: Int = 37

/**
 * Turns the current [MonitorState], sample, and [Settings] into a [NotificationModel]. Pure Kotlin,
 * so it runs the same in a JVM test as on device.
 */
class NotificationModelMapper(
  private val formatter: SpeedFormatter,
  private val locale: Locale,
  private val zone: ZoneId,
  private val sdkInt: Int,
  /** From [PromotedChip.available]; the platform gate and the user's permission in one answer. */
  private val promotedAvailable: Boolean,
  /** The device's 24-hour clock setting, read at format time since the user can change it. */
  private val hour24: () -> Boolean,
  private val clock: Clock = Clock.system(zone),
  /** The locale's pattern for a skeleton; ICU's generator on device, CLDR data in a JVM test. */
  private val bestPattern: (Locale, String) -> String = DateFormat::getBestDateTimePattern,
) {

  /**
   * Whether the notification carries the speed: only while running, and only when asked or
   * promoted.
   */
  fun showsSpeed(state: MonitorState, profile: Profile): Boolean {
    val notification = profile.notification
    return state is MonitorState.Running &&
      (notification.notificationShowsSpeed || notification.surface != ReadoutSurface.OVERLAY)
  }

  fun map(state: MonitorState, sample: SpeedSample?, profile: Profile): NotificationModel {
    val surface = profile.notification.surface
    val showsSpeed = showsSpeed(state, profile)
    val promoted =
      state is MonitorState.Running && surface != ReadoutSurface.OVERLAY && promotedAvailable
    // The lock screen is public: the sampler keeps running behind it so the unlock has a reading
    // ready, but the notification shows the dashes there, never live traffic.
    val locked = state is MonitorState.Running && SuppressionReason.KEYGUARD in state.suppressions
    val usableSample: SpeedSample? = sample?.takeIf { it.supported && !locked }
    val sampleUsable = usableSample != null

    val speedPair: Pair<FormattedSpeed, FormattedSpeed>? =
      if (state is MonitorState.Running && (showsSpeed || promoted)) {
        val options = profile.content.format.copy(unitLabel = UnitLabel.FULL)
        if (usableSample != null) {
          formatter.formatPair(
            usableSample.rxBytesPerSecond,
            usableSample.txBytesPerSecond,
            options,
          )
        } else {
          val dash = formatter.format(Long.MIN_VALUE, options)
          dash to dash
        }
      } else {
        null
      }

    return NotificationModel(
      title = titleFor(state),
      body = bodyFor(state, showsSpeed, speedPair),
      actions = actionsFor(state, profile.pause.notificationPauseDurationMinutes),
      promoted = promoted,
      shortCriticalText = shortCriticalTextFor(promoted, sampleUsable, speedPair),
      metrics = metricsFor(promoted, sampleUsable, speedPair),
      showsSpeed = showsSpeed,
    )
  }

  private fun titleFor(state: MonitorState): NotificationTitle =
    when (state) {
      MonitorState.Off -> NotificationTitle.OFF
      MonitorState.NeedsPermission -> NotificationTitle.NEEDS_PERMISSION
      is MonitorState.Paused -> NotificationTitle.PAUSED
      is MonitorState.Running ->
        if (state.visible || (!state.overlayOn && state.suppressions.isEmpty())) {
          NotificationTitle.RUNNING
        } else {
          NotificationTitle.HIDDEN
        }
    }

  private fun actionsFor(
    state: MonitorState,
    notificationPauseMinutes: Int?,
  ): List<NotificationAction> =
    when (state) {
      MonitorState.Off -> listOf(NotificationAction.Open)
      MonitorState.NeedsPermission ->
        listOf(NotificationAction.GrantPermission, NotificationAction.Open)
      is MonitorState.Paused -> listOf(NotificationAction.Resume, NotificationAction.Open)
      is MonitorState.Running ->
        listOf(NotificationAction.Pause(notificationPauseMinutes), NotificationAction.Open)
    }

  private fun bodyFor(
    state: MonitorState,
    showsSpeed: Boolean,
    speedPair: Pair<FormattedSpeed, FormattedSpeed>?,
  ): NotificationBody? =
    when (state) {
      MonitorState.Off -> null
      MonitorState.NeedsPermission -> NotificationBody.NeedsPermissionHint
      is MonitorState.Paused -> {
        val until = state.until
        if (until == null) {
          null
        } else {
          NotificationBody.PausedUntil(
            DeadlineFormatter.format(until, clock.millis(), locale, zone, hour24(), bestPattern)
          )
        }
      }
      is MonitorState.Running ->
        if (showsSpeed) {
          val (download, upload) = requireNotNull(speedPair) { "showsSpeed requires a speed pair" }
          NotificationBody.Speed(download, upload)
        } else if (!state.visible) {
          val reasons = state.suppressions.sortedBy { it.ordinal }
          NotificationBody.Hidden(reasons)
        } else {
          null
        }
    }

  private fun shortCriticalTextFor(
    promoted: Boolean,
    sampleUsable: Boolean,
    speedPair: Pair<FormattedSpeed, FormattedSpeed>?,
  ): String? {
    if (!promoted || !sampleUsable || speedPair == null) return null
    val number = speedPair.first.number.trim()
    val unit = speedPair.first.unit
    val withUnit = if (unit.isEmpty()) number else "$number $unit"
    return if (withUnit.length <= SHORT_CRITICAL_TEXT_MAX_LENGTH) withUnit else number
  }

  private fun metricsFor(
    promoted: Boolean,
    sampleUsable: Boolean,
    speedPair: Pair<FormattedSpeed, FormattedSpeed>?,
  ): List<NotificationMetric> {
    if (!promoted || sdkInt < METRICS_MIN_SDK || !sampleUsable || speedPair == null)
      return emptyList()
    val (download, upload) = speedPair
    return listOf(
      NotificationMetric(Direction.DOWNLOAD, download),
      NotificationMetric(Direction.UPLOAD, upload),
    )
  }
}
