package tools.zach.app.pixelspeed.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import tools.zach.app.pixelspeed.log.AppLog

/** The explicit intent actions the service answers. Every surface talks to it through these. */
object MonitorActions {
  private const val PREFIX: String = "tools.zach.app.pixelspeed.action."
  const val START: String = PREFIX + "START"
  const val PAUSE: String = PREFIX + "PAUSE"
  const val RESUME: String = PREFIX + "RESUME"
  const val TOGGLE: String = PREFIX + "TOGGLE"

  /**
   * A broadcast, not a service action: the ongoing notification's delete intent. A service start
   * would show the notification again, which is the one thing a dismissal must not do.
   */
  const val NOTIFICATION_DISMISSED: String = PREFIX + "NOTIFICATION_DISMISSED"

  /** Int extra on [PAUSE] and [TOGGLE]: the pause length in minutes. Absent means until resumed. */
  const val EXTRA_MINUTES: String = "tools.zach.app.pixelspeed.extra.MINUTES"

  fun intent(context: Context, action: String, minutes: Int? = null): Intent =
    Intent(context, MonitorService::class.java).setAction(action).apply {
      if (minutes != null) putExtra(EXTRA_MINUTES, minutes)
    }

  /**
   * A pending intent for a notification action. Extras do not distinguish pending intents, so the
   * update flag refreshes [minutes] on the one intent that [requestCode] names.
   */
  fun pendingIntent(
    context: Context,
    action: String,
    requestCode: Int,
    minutes: Int? = null,
  ): PendingIntent =
    PendingIntent.getForegroundService(
      context,
      requestCode,
      intent(context, action, minutes),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  /** True on a start the user's own switch requested; see [MonitorPlatform.start]. */
  const val EXTRA_USER_START: String = "tools.zach.app.pixelspeed.extra.USER_START"

  /** Returns false when the platform refuses a foreground start from the background. */
  fun start(context: Context, userAsked: Boolean): Boolean =
    send(context, intent(context, START).putExtra(EXTRA_USER_START, userAsked))

  /** Whether [intent] is a start the user's own switch requested; see [start]. */
  fun userAsked(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_USER_START, false) == true

  /** Delivers [action] to the service, starting it in the foreground when it is not running. */
  fun send(context: Context, action: String, minutes: Int? = null): Boolean =
    send(context, intent(context, action, minutes))

  private fun send(context: Context, intent: Intent): Boolean =
    try {
      context.startForegroundService(intent)
      true
    } catch (e: ForegroundServiceStartNotAllowedException) {
      AppLog.w("Foreground start refused for ${intent.action}; the app is in the background", e)
      false
    }

  /** Stopping needs no start allowance; the service releases the window in onDestroy. */
  fun stop(context: Context) {
    context.stopService(intent(context, START))
  }
}
