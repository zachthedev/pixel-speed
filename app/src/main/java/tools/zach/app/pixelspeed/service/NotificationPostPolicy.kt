package tools.zach.app.pixelspeed.service

/**
 * Whether a post of the ongoing notification may reach the shade. Android 14 lets the user swipe a
 * foreground service's notification away while the service runs, so a swipe keeps every later state
 * post (on, hidden, paused) silent while the service stays resident. The notification shows again
 * when the service starts, which the switch, a reboot, or a shortcut does once it is stopped, or
 * when the notification becomes the readout, a surface the user turns on themselves. The readout
 * always posts, since it is the reading, so a swipe on it changes nothing and leaves nothing
 * behind.
 *
 * Posts arrive from the publisher's dispatcher and the swipe from the main thread, so every entry
 * point is synchronized.
 */
class NotificationPostPolicy {
  private var dismissed: Boolean = false
  private var readoutShown: Boolean = false

  /**
   * Records a post about to happen and returns whether it may reach the shade; [readout] says the
   * notification carries the speed.
   */
  @Synchronized
  fun onPost(readout: Boolean): Boolean {
    if (readout && !readoutShown) dismissed = false
    readoutShown = readout
    return readout || !dismissed
  }

  /** The user swiped the notification away. */
  @Synchronized
  fun onDismissed() {
    if (!readoutShown) dismissed = true
  }

  /** startForeground showed the notification again. */
  @Synchronized
  fun onStarted() {
    dismissed = false
  }
}
