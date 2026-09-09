package tools.zach.app.pixelspeed.debug

import android.app.Activity
import android.content.Intent
import android.net.TrafficStats
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View

/**
 * A debug-only activity driven over adb. It records every tap it receives with the platform's
 * obscured flags, which is how the overlay's touch passthrough is proven, and with the measure
 * extra it reads the traffic counters every 100 ms for ten seconds and logs the longest run of
 * unchanged readings, which is the counter cache's plateau, for the system total and for the Wi-Fi
 * interface on its own, since the platform may cache the two differently.
 */
class ProbeActivity : Activity() {

  private val handler = Handler(Looper.getMainLooper())
  private var activeRun: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // A plain tap target; the debug build has no user-facing text to translate here.
    val view = View(this)
    view.setBackgroundColor(0xFF203040.toInt())
    setContentView(view)
    if (measureRequested(intent)) measureCounters()
    if (cadenceRequested(intent)) measureCadence()
  }

  /** A second launch while in front lands here (singleTop); a measure request is honored. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (measureRequested(intent)) measureCounters()
    if (cadenceRequested(intent)) measureCadence()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    activeRun = null
    super.onDestroy()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      val obscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
      val partial = event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
      Log.i(
        TAG,
        "tap x=${event.rawX.toInt()} y=${event.rawY.toInt()} obscured=$obscured partiallyObscured=$partial",
      )
    }
    return true
  }

  private fun measureRequested(intent: Intent): Boolean = flagRequested(intent, EXTRA_MEASURE)

  private fun cadenceRequested(intent: Intent): Boolean = flagRequested(intent, EXTRA_CADENCE)

  /** A hostile bundle from another caller reads as no request, logged. */
  private fun flagRequested(intent: Intent, extra: String): Boolean =
    try {
      intent.getBooleanExtra(extra, false)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unreadable extras on the probe intent", e)
      false
    }

  /**
   * Reads the total counter on exact boundaries of each period in [CADENCE_PERIODS_MS], the way a
   * fixed-schedule sampler does, and logs how many reads repeated the previous value, which on a
   * platform that caches the counters for a second is the share of ticks that read stale data.
   */
  private fun measureCadence() {
    if (activeRun != null) {
      Log.w(TAG, "cadence refused: a run is active")
      return
    }
    var start = SystemClock.elapsedRealtime()
    var phase = 0
    var tick = 0
    var previous: Long = -1
    var stale = 0
    val run =
      object : Runnable {
        override fun run() {
          val period = CADENCE_PERIODS_MS[phase]
          val value = TrafficStats.getTotalRxBytes()
          val now = SystemClock.elapsedRealtime() - start
          if (value == TrafficStats.UNSUPPORTED.toLong()) {
            // Every later read would repeat it and read as a stale run rather than no counter.
            Log.i(TAG, "cadence period=$period unsupported=true")
            activeRun = null
            return
          }
          if (tick > 0 && value == previous) stale++
          Log.i(TAG, "cadence period=$period tick=$tick t=$now rx=$value")
          previous = value
          tick++
          if (tick < CADENCE_TICKS) {
            val next = start + period * tick
            handler.postAtTime(
              this,
              SystemClock.uptimeMillis() + (next - SystemClock.elapsedRealtime()),
            )
          } else {
            Log.i(TAG, "cadence period=$period ticks=$tick stale=$stale")
            phase++
            tick = 0
            stale = 0
            previous = -1
            if (phase < CADENCE_PERIODS_MS.size) {
              start = SystemClock.elapsedRealtime() + CADENCE_PHASE_GAP_MS
              handler.postDelayed(this, CADENCE_PHASE_GAP_MS)
            } else {
              activeRun = null
            }
          }
        }
      }
    activeRun = run
    handler.post(run)
  }

  /** One run at a time; a request during a run is refused so two runs never interleave. */
  private fun measureCounters() {
    if (activeRun != null) {
      Log.w(TAG, "measure refused: a run is active")
      return
    }
    val totals = mutableListOf<Pair<Long, Long>>()
    val wifi = mutableListOf<Pair<Long, Long>>()
    val start = SystemClock.elapsedRealtime()
    val tick =
      object : Runnable {
        override fun run() {
          val now = SystemClock.elapsedRealtime() - start
          totals += now to TrafficStats.getTotalRxBytes()
          wifi += now to TrafficStats.getRxBytes(WIFI_INTERFACE)
          if (now < MEASURE_MS) {
            handler.postDelayed(this, STEP_MS)
          } else {
            activeRun = null
            report("total", totals)
            report(WIFI_INTERFACE, wifi)
          }
        }
      }
    activeRun = tick
    handler.post(tick)
  }

  /** The plateau is the span between the first and last of the longest run of equal readings. */
  private fun report(name: String, readings: List<Pair<Long, Long>>) {
    if (readings.any { it.second == TrafficStats.UNSUPPORTED.toLong() }) {
      Log.i(TAG, "counters $name unsupported=true reads=${readings.size}")
      return
    }
    var bestStart = 0
    var bestEnd = 0
    var runStart = 0
    for (i in 1 until readings.size) {
      if (readings[i].second != readings[i - 1].second) runStart = i
      if (i - runStart > bestEnd - bestStart) {
        bestStart = runStart
        bestEnd = i
      }
    }
    val changes = readings.zipWithNext().count { (a, b) -> a.second != b.second }
    // The value still held for the step after the last equal reading.
    val plateauMs =
      readings[minOf(bestEnd + 1, readings.lastIndex)].first - readings[bestStart].first
    Log.i(
      TAG,
      "counters $name reads=${readings.size} changes=$changes longestUnchangedRun=${bestEnd - bestStart + 1} plateauMs=$plateauMs",
    )
    readings.forEach { (t, rx) -> Log.i(TAG, "read $name t=$t rx=$rx") }
  }

  private companion object {
    const val TAG: String = "PixelSpeedProbe"
    const val EXTRA_MEASURE: String = "measure"
    const val STEP_MS: Long = 100L
    const val MEASURE_MS: Long = 10_000L
    const val EXTRA_CADENCE: String = "cadence"
    val CADENCE_PERIODS_MS: List<Long> = listOf(1000L, 1050L, 1100L)
    const val CADENCE_TICKS: Int = 20
    const val CADENCE_PHASE_GAP_MS: Long = 2_000L
    const val WIFI_INTERFACE: String = "wlan0"
  }
}
