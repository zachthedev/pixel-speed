package tools.zach.app.pixelspeed.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppLogTest {

  @BeforeEach
  fun clearRing() {
    AppLog.clear()
  }

  @Test
  fun `lines come back oldest first with their level`() {
    AppLog.d("first")
    AppLog.i("second")
    AppLog.w("third")
    AppLog.e("fourth")

    val lines: List<LogLine> = AppLog.lines()

    assertEquals(listOf("first", "second", "third", "fourth"), lines.map { it.message })
    assertEquals(listOf('D', 'I', 'W', 'E'), lines.map { it.level })
    assertTrue(lines.all { it.trace == null }, "no throwable, no trace")
  }

  @Test
  fun `the ring keeps the newest lines and drops the oldest past its capacity`() {
    repeat(AppLog.CAPACITY + 5) { AppLog.i("line $it") }

    val lines: List<LogLine> = AppLog.lines()

    assertEquals(AppLog.CAPACITY, lines.size)
    assertEquals("line 5", lines.first().message)
    assertEquals("line ${AppLog.CAPACITY + 4}", lines.last().message)
  }

  @Test
  fun `a throwable's trace is kept and cut at the limit`() {
    val deep = IllegalStateException("x".repeat(6000))

    AppLog.e("failed", deep)

    val trace: String? = AppLog.lines().single().trace
    assertTrue(trace != null && trace.startsWith("java.lang.IllegalStateException"), "$trace")
    assertEquals(4000, trace?.length)
  }

  @Test
  fun `a content or file uri in a message or a trace is replaced before it is kept`() {
    val uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fx.json"

    AppLog.w("Cannot open $uri")
    AppLog.e("Import failed", java.io.FileNotFoundException("No content provider: $uri"))
    AppLog.i("Read file:///sdcard/Download/settings.json")

    val lines: List<LogLine> = AppLog.lines()
    assertEquals("Cannot open <uri>", lines[0].message)
    assertTrue(lines[1].trace?.contains("No content provider: <uri>") == true, "${lines[1].trace}")
    assertTrue(lines[1].trace?.contains("content://") == false, "${lines[1].trace}")
    assertEquals("Read <uri>", lines[2].message)
  }

  @Test
  fun `clear empties the ring`() {
    AppLog.w("something")

    AppLog.clear()

    assertTrue(AppLog.lines().isEmpty())
    assertNull(AppLog.lines().firstOrNull())
  }
}
