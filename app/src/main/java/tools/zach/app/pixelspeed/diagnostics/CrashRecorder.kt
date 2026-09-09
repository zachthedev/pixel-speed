package tools.zach.app.pixelspeed.diagnostics

import java.io.File
import java.time.Instant
import java.time.ZoneId
import tools.zach.app.pixelspeed.log.AppLog

/**
 * Keeps the last uncaught exception in [file] with the log ring behind it, then hands the thread to
 * the handler that was installed before, so the platform's crash dialog and Play's vitals see the
 * crash as they would without this. One file, overwritten by the next crash; the diagnostics report
 * includes it until then. The hand-off happens whatever the record does: a record that fails on an
 * out-of-memory crash, or on a zone the platform cannot resolve, must not keep the process alive
 * with a dead main thread.
 */
class CrashRecorder(
  private val file: File,
  private val versionName: String,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
) : Thread.UncaughtExceptionHandler {
  private val previous: Thread.UncaughtExceptionHandler? =
    Thread.getDefaultUncaughtExceptionHandler()

  fun install() {
    Thread.setDefaultUncaughtExceptionHandler(this)
  }

  @Suppress("TooGenericExceptionCaught")
  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    try {
      val text: String =
        CrashRecord.render(
          at = Instant.now(),
          zone = zone(),
          versionName = versionName,
          threadName = thread.name,
          throwable = throwable,
          log = AppLog.lines(),
        )
      file.parentFile?.mkdirs()
      file.writeText(text)
    } catch (t: Throwable) {
      try {
        AppLog.e("Cannot keep the crash record", t)
      } catch (_: Throwable) {
        // The process is dying; the record is a courtesy, the hand-off below is the duty.
      }
    } finally {
      previous?.uncaughtException(thread, throwable)
    }
  }

  companion object {
    /** Where the record lives under the app's private files. */
    fun fileIn(filesDir: File): File = File(filesDir, "diagnostics/last-crash.txt")
  }
}
