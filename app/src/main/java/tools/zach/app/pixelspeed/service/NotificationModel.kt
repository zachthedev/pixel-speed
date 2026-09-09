package tools.zach.app.pixelspeed.service

import tools.zach.app.pixelspeed.format.FormattedSpeed
import tools.zach.app.pixelspeed.state.SuppressionReason

/** The notification's title line, one per [tools.zach.app.pixelspeed.state.MonitorState] shape. */
enum class NotificationTitle {
  OFF,
  RUNNING,
  HIDDEN,
  PAUSED,
  NEEDS_PERMISSION,
}

/** The notification's content line, or its absence when the title alone says enough. */
sealed interface NotificationBody {
  data object NeedsPermissionHint : NotificationBody

  data class PausedUntil(val timeText: String) : NotificationBody

  data class Hidden(val reasons: List<SuppressionReason>) : NotificationBody

  data class Speed(val download: FormattedSpeed, val upload: FormattedSpeed) : NotificationBody
}

/** One notification action button, mapped to a pending intent by [MonitorNotification]. */
sealed interface NotificationAction {
  data class Pause(val minutes: Int?) : NotificationAction

  data object Resume : NotificationAction

  data object Open : NotificationAction

  data object GrantPermission : NotificationAction
}

/** A traffic direction, for a Live Update metric or a paired speed body. */
enum class Direction {
  DOWNLOAD,
  UPLOAD,
}

/** One entry of a promoted notification's metric row. */
data class NotificationMetric(val direction: Direction, val value: FormattedSpeed)

/**
 * Everything [MonitorNotification] needs to render one notification, with no Android dependency so
 * [NotificationModelMapper] runs the same in a JVM test as on device.
 */
data class NotificationModel(
  val title: NotificationTitle,
  val body: NotificationBody?,
  val actions: List<NotificationAction>,
  val promoted: Boolean,
  val shortCriticalText: String?,
  val metrics: List<NotificationMetric>,
  val showsSpeed: Boolean,
)
