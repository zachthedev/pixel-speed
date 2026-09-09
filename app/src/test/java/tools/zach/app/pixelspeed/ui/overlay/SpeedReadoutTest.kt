package tools.zach.app.pixelspeed.ui.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SpeedReadoutTest {

  companion object {
    @JvmStatic
    fun dominantCases(): List<Array<Any>> =
      listOf(
        arrayOf(2_000_000L, 500_000L, true),
        arrayOf(500_000L, 2_000_000L, false),
        arrayOf(1_000_000L, 1_000_000L, true),
        arrayOf(0L, 0L, true),
        arrayOf(0L, 1L, false),
        arrayOf(1L, 0L, true),
      )

    @JvmStatic
    fun directionCases(): List<Array<Any>> =
      listOf(
        // showDownload, showUpload, download, upload, expected download
        arrayOf(true, false, 100L, 900L, true),
        arrayOf(false, true, 900L, 100L, false),
        arrayOf(true, true, 900L, 100L, true),
        arrayOf(true, true, 100L, 900L, false),
        arrayOf(true, true, 500L, 500L, true),
        arrayOf(false, false, 100L, 900L, true),
      )

    @JvmStatic
    fun strictlyOrderedCases(): List<Array<Long>> =
      listOf(arrayOf(2_000_000L, 500_000L), arrayOf(1L, 0L), arrayOf(1_000_000L, 999_999L))
  }

  @ParameterizedTest(name = "download {0}bps vs upload {1}bps favors download: {2}")
  @MethodSource("dominantCases")
  fun `is download dominant picks the larger rate and ties favor download`(
    downloadBytesPerSecond: Long,
    uploadBytesPerSecond: Long,
    expected: Boolean,
  ) {
    assertEquals(expected, isDownloadDominant(downloadBytesPerSecond, uploadBytesPerSecond))
  }

  @ParameterizedTest(name = "show down {0} up {1} rates {2} and {3} shows download: {4}")
  @MethodSource("directionCases")
  fun `single value shows the only visible direction else the dominant one`(
    showDownload: Boolean,
    showUpload: Boolean,
    downloadBytesPerSecond: Long,
    uploadBytesPerSecond: Long,
    expected: Boolean,
  ) {
    assertEquals(
      expected,
      singleDirectionIsDownload(
        showDownload,
        showUpload,
        downloadBytesPerSecond,
        uploadBytesPerSecond,
      ),
    )
  }

  @ParameterizedTest(name = "break round: swapping {0} and {1} flips the winner")
  @MethodSource("strictlyOrderedCases")
  fun `swapping the rates flips the dominant direction`(larger: Long, smaller: Long) {
    val forward = isDownloadDominant(larger, smaller)
    val reversed = isDownloadDominant(smaller, larger)
    assertNotEquals(forward, reversed)
  }
}
