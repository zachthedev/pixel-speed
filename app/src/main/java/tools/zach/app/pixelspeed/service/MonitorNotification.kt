package tools.zach.app.pixelspeed.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.text.ListFormatter
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import tools.zach.app.pixelspeed.MainActivity
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.state.SuppressionReason

private const val REQUEST_PAUSE: Int = 20
private const val REQUEST_RESUME: Int = 21
private const val REQUEST_OPEN: Int = 10
private const val REQUEST_GRANT_PERMISSION: Int = 11
private const val REQUEST_DISMISSED: Int = 12

/**
 * Renders a [NotificationModel] into a platform [Notification]. The channel is LOW: the platform
 * raises MIN foreground channels to LOW anyway, and LOW keeps the status bar icon.
 */
class MonitorNotification(private val context: Context) {

  private val manager: NotificationManager =
    context.getSystemService(NotificationManager::class.java)
  private val policy: NotificationPostPolicy = NotificationPostPolicy()
  // Each is a binder call whose arguments never change, so it runs once per service.
  private val openApp: PendingIntent by lazy {
    PendingIntent.getActivity(
      context,
      REQUEST_OPEN,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }
  private val dismissed: PendingIntent by lazy {
    PendingIntent.getBroadcast(
      context,
      REQUEST_DISMISSED,
      // A foreground broadcast, so the swipe is known before the next post rather than after it.
      Intent(MonitorActions.NOTIFICATION_DISMISSED)
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
      PendingIntent.FLAG_IMMUTABLE,
    )
  }

  fun ensureChannel() {
    val channel =
      NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.notification_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        )
        .apply {
          description = context.getString(R.string.notification_channel_description)
          setShowBadge(false)
        }
    manager.createNotificationChannel(channel)
  }

  fun build(model: NotificationModel, ongoing: Boolean = true): Notification {
    val builder =
      Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_readout)
        .setContentTitle(context.getString(titleResFor(model.title)))
        .setContentIntent(openApp)
        .setOngoing(ongoing)
        .setOnlyAlertOnce(true)
        // A state, not an event: the time it changed says nothing worth a line in the shade.
        .setShowWhen(false)
        .setCategory(Notification.CATEGORY_SERVICE)
        // Off the lock screen unless the notification is the readout itself, since a locked phone
        // shows no overlay and a service notice there is noise.
        .setVisibility(
          if (model.showsSpeed || model.promoted) Notification.VISIBILITY_PUBLIC
          else Notification.VISIBILITY_SECRET
        )

    bodyTextFor(model.body)?.let { builder.setContentText(it) }
    model.actions.forEach { action -> builder.addAction(actionFor(action)) }
    if (ongoing) builder.setDeleteIntent(dismissed)

    // The model already carries the platform gate; the version check here is for lint only.
    if (model.promoted && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
      builder.setRequestPromotedOngoing(true)
      model.shortCriticalText?.let { builder.setShortCriticalText(it) }
    }
    if (Build.VERSION.SDK_INT >= METRICS_MIN_SDK && model.metrics.isNotEmpty()) {
      val style =
        Notification.MetricStyle()
          .setMetrics(model.metrics.map { it.toPlatformMetric() })
          .setCriticalMetric(0)
      builder.setStyle(style)
    }

    return builder.build()
  }

  /** Posts unless the user swiped the notification away; see [NotificationPostPolicy]. */
  fun post(model: NotificationModel) {
    val built = build(model)
    // The policy is asked last, so a swipe that lands while the notification builds still counts.
    if (!policy.onPost(readout = model.showsSpeed || model.promoted)) return
    manager.notify(NOTIFICATION_ID, built)
  }

  /** The delete intent fired: the user swiped the ongoing notification away. */
  fun onDismissed() {
    policy.onDismissed()
  }

  /** A start intent showed the notification through startForeground. */
  fun onStarted() {
    policy.onStarted()
  }

  /**
   * The way back after a permission loss: a dismissable notification on its own id, since the
   * foreground one leaves with the service.
   */
  fun postNeedsPermission(model: NotificationModel) {
    manager.notify(NEEDS_PERMISSION_NOTIFICATION_ID, build(model, ongoing = false))
  }

  fun clearNeedsPermission() {
    manager.cancel(NEEDS_PERMISSION_NOTIFICATION_ID)
  }

  private fun titleResFor(title: NotificationTitle): Int =
    when (title) {
      NotificationTitle.OFF -> R.string.notification_title_off
      NotificationTitle.RUNNING -> R.string.notification_title_running
      NotificationTitle.HIDDEN -> R.string.notification_title_hidden
      NotificationTitle.PAUSED -> R.string.notification_title_paused
      NotificationTitle.NEEDS_PERMISSION -> R.string.notification_title_needs_permission
    }

  private fun bodyTextFor(body: NotificationBody?): String? =
    when (body) {
      null -> null
      NotificationBody.NeedsPermissionHint ->
        context.getString(R.string.notification_text_needs_permission)
      is NotificationBody.PausedUntil ->
        context.getString(R.string.notification_body_paused_until, body.timeText)
      is NotificationBody.Hidden ->
        ListFormatter.getInstance(context.resources.configuration.locales[0])
          .format(body.reasons.map { context.getString(reasonResFor(it)) })
      is NotificationBody.Speed ->
        context.getString(
          R.string.notification_body_speed,
          body.download.number.trim(),
          body.download.unit,
          body.upload.number.trim(),
          body.upload.unit,
        )
    }

  private fun reasonResFor(reason: SuppressionReason): Int =
    when (reason) {
      SuppressionReason.SCREEN_OFF -> R.string.notification_reason_screen_off
      SuppressionReason.KEYGUARD -> R.string.notification_reason_keyguard
      SuppressionReason.OFFLINE -> R.string.notification_reason_offline
      SuppressionReason.IDLE -> R.string.notification_reason_idle
      SuppressionReason.FULLSCREEN -> R.string.notification_reason_fullscreen
      SuppressionReason.LANDSCAPE -> R.string.notification_reason_landscape
      SuppressionReason.BATTERY_SAVER -> R.string.notification_reason_battery_saver
    }

  private fun actionFor(action: NotificationAction): Notification.Action =
    when (action) {
      is NotificationAction.Pause ->
        Notification.Action.Builder(
            null,
            context.getString(R.string.notification_action_pause),
            MonitorActions.pendingIntent(
              context,
              MonitorActions.PAUSE,
              REQUEST_PAUSE,
              action.minutes,
            ),
          )
          .build()
      NotificationAction.Resume ->
        Notification.Action.Builder(
            null,
            context.getString(R.string.notification_action_resume),
            MonitorActions.pendingIntent(context, MonitorActions.RESUME, REQUEST_RESUME),
          )
          .build()
      NotificationAction.Open ->
        Notification.Action.Builder(
            null,
            context.getString(R.string.notification_action_open),
            openApp,
          )
          .build()
      NotificationAction.GrantPermission ->
        Notification.Action.Builder(
            null,
            context.getString(R.string.notification_action_grant_permission),
            grantPermission(),
          )
          .build()
    }

  @RequiresApi(METRICS_MIN_SDK)
  private fun NotificationMetric.toPlatformMetric(): Notification.Metric {
    val label =
      context.getString(
        when (direction) {
          Direction.DOWNLOAD -> R.string.notification_metric_label_download
          Direction.UPLOAD -> R.string.notification_metric_label_upload
        }
      )
    return Notification.Metric(
      Notification.Metric.FixedText(value.number.trim(), value.unit),
      label,
    )
  }

  private fun grantPermission(): PendingIntent =
    PendingIntent.getActivity(
      context,
      REQUEST_GRANT_PERMISSION,
      Intent(
          AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
          "package:${context.packageName}".toUri(),
        )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  companion object {
    const val CHANNEL_ID: String = "monitor"
    const val NOTIFICATION_ID: Int = 1
    const val NEEDS_PERMISSION_NOTIFICATION_ID: Int = 2
  }
}
