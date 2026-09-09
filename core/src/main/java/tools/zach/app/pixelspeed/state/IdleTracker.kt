package tools.zach.app.pixelspeed.state

/**
 * Tracks whether the link has been quiet for at least [delayMillis]. [thresholdBytesPerSecond] is
 * the per-direction ceiling below which a sample counts as quiet; a direction at or above it counts
 * as loud.
 */
class IdleTracker(private val thresholdBytesPerSecond: Long, private val delayMillis: Long) {
  private var quietSinceMillis: Long? = null

  /** Feeds one sample and returns whether the link counts as idle after it. */
  fun onSample(rxBytesPerSecond: Long, txBytesPerSecond: Long, atMillis: Long): Boolean {
    val quiet =
      rxBytesPerSecond < thresholdBytesPerSecond && txBytesPerSecond < thresholdBytesPerSecond
    if (!quiet) {
      quietSinceMillis = null
      return false
    }
    val since = quietSinceMillis ?: atMillis.also { quietSinceMillis = it }
    return atMillis - since >= delayMillis
  }

  /** Forgets the quiet period, so the next tick starts a new one. */
  fun reset() {
    quietSinceMillis = null
  }
}
