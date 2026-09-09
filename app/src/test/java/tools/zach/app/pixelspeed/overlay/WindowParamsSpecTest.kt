package tools.zach.app.pixelspeed.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager.LayoutParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

/** Rows state the window contract: the flag set, the cutout mode, and the alpha. */
class WindowParamsSpecTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("flagRows")
  fun `the flags and cutout mode follow where the window sits and whether it is being edited`(
    name: String,
    inStatusBar: Boolean,
    editing: Boolean,
    expectedFlags: Int,
    expectedCutoutMode: Int,
  ) {
    val spec =
      WindowParamsSpec.of(inStatusBar, editing, visible = true, opacity = 0.5f, rtl = false)

    assertEquals(expectedFlags, spec.flags, "$name: flags")
    assertEquals(expectedCutoutMode, spec.layoutInDisplayCutoutMode, "$name: cutout mode")
  }

  @ParameterizedTest(name = "in the bar {0}")
  @CsvSource("false", "true")
  fun `a hidden window is not touchable even while it is being edited`(inStatusBar: Boolean) {
    val spec =
      WindowParamsSpec.of(inStatusBar, editing = true, visible = false, opacity = 0.5f, rtl = false)

    assertEquals(LayoutParams.FLAG_NOT_TOUCHABLE, spec.flags and LayoutParams.FLAG_NOT_TOUCHABLE)
    assertEquals(0, spec.flags and LayoutParams.FLAG_NOT_TOUCH_MODAL)
  }

  @ParameterizedTest(name = "visible {0}, opacity {1} gives alpha {2}")
  @CsvSource(
    "true, 0.5, 0.5",
    "true, 0.8, 0.8",
    "true, 0.9, 0.9",
    "true, 1.0, 1.0",
    "true, 1.5, 1.0",
    "true, -0.2, 0.0",
    "true, NaN, 0.0",
    "false, 0.5, 0.0",
    "false, 1.0, 0.0",
  )
  fun `the alpha is the opacity held to zero through one and a hidden window is fully transparent`(
    visible: Boolean,
    opacity: Float,
    alpha: Float,
  ) {
    val spec = WindowParamsSpec.of(false, false, visible, opacity, rtl = false)
    assertEquals(alpha, spec.alpha, 0.0001f)
  }

  @Test
  fun `a right-to-left display anchors the window at the top right so x runs from that edge`() {
    val spec = WindowParamsSpec.of(false, false, visible = true, opacity = 0.5f, rtl = true)

    assertEquals(Gravity.TOP or Gravity.RIGHT, spec.gravity)
  }

  @ParameterizedTest(name = "in status bar {0}, editing {1}")
  @CsvSource("false, false", "true, false", "false, true", "true, true")
  fun `every spec is an application overlay anchored top-left with a translucent surface`(
    inStatusBar: Boolean,
    editing: Boolean,
  ) {
    val spec =
      WindowParamsSpec.of(inStatusBar, editing, visible = true, opacity = 0.5f, rtl = false)

    assertEquals(LayoutParams.TYPE_APPLICATION_OVERLAY, spec.type)
    assertEquals(Gravity.TOP or Gravity.LEFT, spec.gravity)
    assertEquals(PixelFormat.TRANSLUCENT, spec.format)
    assertTrue(spec.flags and LayoutParams.FLAG_NOT_FOCUSABLE != 0, "never focusable")
  }

  companion object {
    private const val BASE: Int =
      LayoutParams.FLAG_NOT_FOCUSABLE or
        LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        LayoutParams.FLAG_HARDWARE_ACCELERATED

    @JvmStatic
    fun flagRows(): List<Arguments> =
      listOf(
        Arguments.of(
          "outside the bar, not editing: touches pass through, cutout kept clear",
          false,
          false,
          BASE or LayoutParams.FLAG_NOT_TOUCHABLE,
          LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,
        ),
        Arguments.of(
          "in the bar, not editing: no layout limits, laid out beside the cutout",
          true,
          false,
          BASE or LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
          LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
        ),
        Arguments.of(
          "outside the bar, editing: the pill takes touches, the rest still passes",
          false,
          true,
          BASE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
          LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,
        ),
        Arguments.of(
          "in the bar, editing",
          true,
          true,
          BASE or LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
          LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
        ),
      )
  }
}
