package tools.zach.app.pixelspeed.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdleTrackerTest {

  @Test
  fun `quiet samples return false until the delay elapses`() {
    val tracker = IdleTracker(thresholdBytesPerSecond = 1024, delayMillis = 5000)
    assertEquals(false, tracker.onSample(0, 0, atMillis = 0))
    assertEquals(false, tracker.onSample(0, 0, atMillis = 4999))
    assertEquals(true, tracker.onSample(0, 0, atMillis = 5000))
  }

  // The quiet period restarts at the first quiet sample after the loud one (5000), so it takes
  // a full delay from there, at 10000, to read idle again.
  @Test
  fun `a loud sample restarts the quiet period`() {
    val tracker = IdleTracker(thresholdBytesPerSecond = 1024, delayMillis = 5000)
    tracker.onSample(0, 0, atMillis = 0)
    assertEquals(false, tracker.onSample(2000, 0, atMillis = 3000))
    assertEquals(false, tracker.onSample(0, 0, atMillis = 5000))
    assertEquals(false, tracker.onSample(0, 0, atMillis = 8000))
    assertEquals(true, tracker.onSample(0, 0, atMillis = 10000))
  }

  @Test
  fun `rx at the threshold is loud`() {
    val tracker = IdleTracker(thresholdBytesPerSecond = 1024, delayMillis = 0)
    assertEquals(false, tracker.onSample(1024, 0, atMillis = 0))
  }

  @Test
  fun `both directions one below the threshold is quiet`() {
    val tracker = IdleTracker(thresholdBytesPerSecond = 1024, delayMillis = 0)
    assertEquals(true, tracker.onSample(1023, 1023, atMillis = 0))
  }

  @Test
  fun `reset ends the quiet period so the next quiet sample returns false`() {
    val tracker = IdleTracker(thresholdBytesPerSecond = 1024, delayMillis = 5000)
    tracker.onSample(0, 0, atMillis = 0)
    assertEquals(true, tracker.onSample(0, 0, atMillis = 5000))
    tracker.reset()
    assertEquals(false, tracker.onSample(0, 0, atMillis = 5000))
  }

  @Test
  fun `a single quiet sample is idle at once with zero delay`() {
    val tracker = IdleTracker(thresholdBytesPerSecond = 1024, delayMillis = 0)
    assertEquals(true, tracker.onSample(0, 0, atMillis = 0))
  }
}
