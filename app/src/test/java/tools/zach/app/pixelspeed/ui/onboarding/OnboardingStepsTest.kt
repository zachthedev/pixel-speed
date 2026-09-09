package tools.zach.app.pixelspeed.ui.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.overlay.OverlayPlacement
import tools.zach.app.pixelspeed.overlay.RectDp

class OnboardingStepsTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("deviceRows")
  fun `step list follows version and manufacturer`(
    name: String,
    sdkInt: Int,
    manufacturer: String,
    forceKeepAlive: Boolean,
    deviceSecure: Boolean,
    hasCenteredHole: Boolean,
    expected: List<OnboardingStep>,
  ) {
    assertEquals(
      expected,
      OnboardingSteps.forDevice(
        sdkInt,
        manufacturer,
        forceKeepAlive,
        deviceSecure,
        hasCenteredHole,
      ),
    )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("centeredHoleRows")
  fun `the centered hole is the top cutout nearest the display center`(
    name: String,
    cutoutRects: List<RectDp>,
    widthDp: Int,
    expected: RectDp?,
  ) {
    assertEquals(expected, centeredTopHole(cutoutRects, widthDp))
    assertEquals(expected != null, hasCenteredTopHole(cutoutRects, widthDp))
  }

  @Test
  fun `the demo's busier bar runs its icons up to the hole and stops short of the end icons`() {
    val geometry =
      DisplayGeometry(
        widthDp = 427,
        heightDp = 952,
        statusBarDp = 52,
        navigationBarDp = 48,
        cutoutRects = listOf(RectDp(198, 0, 228, 48)),
        landscape = false,
      )

    val bar = measuredBarPastHole(geometry, endIconsDp = 68)

    assertEquals(
      MeasuredBar(
        startFreeDp = 228,
        endFreeDp = 427 - OverlayPlacement.barEndInsetDp(geometry) - 68,
      ),
      bar,
    )
    assertNull(measuredBarPastHole(geometry.copy(cutoutRects = emptyList()), endIconsDp = 68))
  }

  @Test
  fun `a display with no cutouts has no centered hole`() {
    assertNull(centeredTopHole(emptyList(), 427))
    assertFalse(hasCenteredTopHole(emptyList(), 427))
  }

  @ParameterizedTest(name = "persisted {0} over {1} steps resumes at {2}")
  @MethodSource("resumeRows")
  fun `resume index clamps into the list`(persisted: Int, count: Int, expected: Int) {
    val steps = List(count) { OnboardingStep.WELCOME }
    assertEquals(expected, OnboardingSteps.resumeIndex(persisted, steps))
  }

  @Test
  fun `only the overlay and start steps refuse skipping`() {
    val unskippable = OnboardingStep.entries.filterNot(OnboardingSteps::isSkippable)
    assertEquals(listOf(OnboardingStep.OVERLAY, OnboardingStep.START), unskippable)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("hintRows")
  fun `restricted settings hint needs the guarded version, source, and a failed return`(
    name: String,
    sdkInt: Int,
    packageSource: Int,
    granted: Boolean,
    returned: Boolean,
    expected: Boolean,
  ) {
    val applies = RestrictedSettingsHint.applies(sdkInt, packageSource, granted, returned)
    if (expected) assertTrue(applies, name) else assertFalse(applies, name)
  }

  companion object {
    private const val API_32: Int = 32
    private const val API_33: Int = 33
    private const val API_34: Int = 34
    private const val API_35: Int = 35
    private const val API_37: Int = 37
    private const val SOURCE_UNSPECIFIED: Int = 0
    private const val SOURCE_STORE: Int = 2
    private const val SOURCE_LOCAL_FILE: Int = 3
    private const val SOURCE_DOWNLOADED_FILE: Int = 4

    @JvmStatic
    fun deviceRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "Pixel on API 37: no notifications skip, no keep-alive",
          API_37,
          "Google",
          false,
          true,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "Samsung on API 37 gets the keep-alive step",
          API_37,
          "samsung",
          false,
          true,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.KEEP_ALIVE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "API 32 has no notification permission but keeps the tile step",
          API_32,
          "Google",
          false,
          true,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.TILE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "forced keep-alive shows the step on a Google device",
          API_37,
          "Google",
          true,
          true,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.KEEP_ALIVE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "no secure lock, no notice step",
          API_37,
          "Google",
          false,
          false,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "manufacturer casing and padding do not matter",
          API_33,
          "  GOOGLE ",
          false,
          true,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "a centered hole adds the side flip step right before Start",
          API_37,
          "Google",
          false,
          true,
          true,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.SIDE_FLIP,
            OnboardingStep.START,
          ),
        ),
        Arguments.of(
          "no centered hole, no side flip step",
          API_37,
          "Google",
          false,
          true,
          false,
          listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.OVERLAY,
            OnboardingStep.NOTIFICATIONS,
            OnboardingStep.TILE,
            OnboardingStep.SYSTEM_NOTICE,
            OnboardingStep.START,
          ),
        ),
      )

    @JvmStatic
    fun centeredHoleRows(): List<Arguments> {
      val centered = RectDp(198, 0, 228, 48)
      val offCenter = RectDp(20, 0, 50, 24)
      val tooLow = RectDp(198, 40, 228, 88)
      return listOf(
        Arguments.of("a centered top rect qualifies", listOf(centered), 427, centered),
        Arguments.of("an off-center rect does not qualify", listOf(offCenter), 427, null),
        Arguments.of(
          "a rect too far from the top edge does not qualify",
          listOf(tooLow),
          427,
          null,
        ),
        Arguments.of(
          "the rect nearest the center wins among several",
          listOf(offCenter, centered),
          427,
          centered,
        ),
        Arguments.of("no rects at all", emptyList<RectDp>(), 427, null),
      )
    }

    @JvmStatic
    fun resumeRows(): List<Arguments> =
      listOf(
        Arguments.of(0, 5, 0),
        Arguments.of(3, 5, 3),
        Arguments.of(4, 5, 4),
        Arguments.of(9, 5, 4),
        Arguments.of(-2, 5, 0),
        Arguments.of(3, 0, 0),
      )

    @JvmStatic
    fun hintRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "downloaded file on API 35 after a failed return",
          API_35,
          SOURCE_DOWNLOADED_FILE,
          false,
          true,
          true,
        ),
        Arguments.of(
          "local file on API 37 after a failed return",
          API_37,
          SOURCE_LOCAL_FILE,
          false,
          true,
          true,
        ),
        Arguments.of("API 34 never guards", API_34, SOURCE_DOWNLOADED_FILE, false, true, false),
        Arguments.of("store installs are exempt", API_37, SOURCE_STORE, false, true, false),
        Arguments.of(
          "adb and session installs report unspecified",
          API_37,
          SOURCE_UNSPECIFIED,
          false,
          true,
          false,
        ),
        Arguments.of(
          "no hint before the user has been to settings",
          API_37,
          SOURCE_DOWNLOADED_FILE,
          false,
          false,
          false,
        ),
        Arguments.of("no hint once granted", API_37, SOURCE_DOWNLOADED_FILE, true, true, false),
      )
  }
}
