package tools.zach.app.pixelspeed.service

/**
 * Milliseconds from a source that only moves forward, for ordering a start against the refusal that
 * answers it; the wall clock can jump between the two reads.
 */
fun interface Uptime {
  fun millis(): Long
}
