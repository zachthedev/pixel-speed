package tools.zach.app.pixelspeed.log

import android.util.Log
import tools.zach.app.pixelspeed.TAG

/** One line the app logged, kept for the diagnostics report. */
data class LogLine(
  /** Wall-clock time of the line in epoch milliseconds. */
  val timeMillis: Long,
  /** The logcat level letter: D, I, W, or E. */
  val level: Char,
  val message: String,
  /** The stack trace of the throwable logged with the line, or null. */
  val trace: String?,
)

/**
 * The app's log. Every line goes to logcat under [TAG] and into a bounded ring, so a diagnostics
 * report the user shares carries what the process did before it, without a file on disk and without
 * a service watching the log. The ring keeps the newest [CAPACITY] lines; a trace is cut at
 * [TRACE_LIMIT] characters so one exception cannot fill it.
 */
object AppLog {
  const val CAPACITY: Int = 300
  private const val TRACE_LIMIT: Int = 4000

  private val URI: Regex = Regex("""(?:content|file)://\S+""")
  private val lock: Any = Any()
  private val ring: ArrayDeque<LogLine> = ArrayDeque(CAPACITY)

  fun d(message: String, throwable: Throwable? = null) {
    Log.d(TAG, message, throwable)
    keep('D', message, throwable)
  }

  fun i(message: String, throwable: Throwable? = null) {
    Log.i(TAG, message, throwable)
    keep('I', message, throwable)
  }

  fun w(message: String, throwable: Throwable? = null) {
    Log.w(TAG, message, throwable)
    keep('W', message, throwable)
  }

  fun e(message: String, throwable: Throwable? = null) {
    Log.e(TAG, message, throwable)
    keep('E', message, throwable)
  }

  /** The kept lines, oldest first. */
  fun lines(): List<LogLine> = synchronized(lock) { ring.toList() }

  /**
   * A kept line never carries a content or file URI: a document provider names the user's own file
   * in its exception message, and the diagnostics report prints kept lines verbatim.
   */
  fun redact(text: String): String = URI.replace(text, "<uri>")

  /** Drops every kept line. */
  fun clear() {
    synchronized(lock) { ring.clear() }
  }

  private fun keep(level: Char, message: String, throwable: Throwable?) {
    val trace: String? = throwable?.stackTraceToString()?.take(TRACE_LIMIT)?.let(::redact)
    val line = LogLine(System.currentTimeMillis(), level, redact(message), trace)
    synchronized(lock) {
      if (ring.size == CAPACITY) ring.removeFirst()
      ring.addLast(line)
    }
  }
}
