package tools.zach.app.pixelspeed.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * Everything the overlay window's layout params carry that is not a position: the type, the flag
 * set, the cutout mode, and the window alpha, decided from where the window sits and what it is
 * doing. Pure values, so the contract holds without a window manager.
 */
data class WindowParamsSpec(
  val type: Int,
  val flags: Int,
  val gravity: Int,
  val layoutInDisplayCutoutMode: Int,
  val alpha: Float,
  val format: Int,
) {
  companion object {
    /**
     * The alpha is the configured opacity; above the platform's touch-obscuring limit the window
     * swallows the touches that pass through it, which the settings screen warns about at the
     * moment of choice. A hidden window keeps its params with alpha zero, which exempts it from the
     * obscuring rules entirely.
     */
    // The window manager applies gravity with no layout direction, so the absolute side is chosen
    // here from the direction the placement runs in.
    @SuppressLint("RtlHardcoded")
    fun of(
      inStatusBar: Boolean,
      editing: Boolean,
      visible: Boolean,
      opacity: Float,
      rtl: Boolean,
    ): WindowParamsSpec {
      var flags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
      // Outside edit mode the window is never touchable; inside it, touches land only on the pill
      // and everything around it still reaches the app below. A hidden window keeps its surface
      // at alpha zero, so it must not take touches either, or an invisible pill would eat them.
      flags =
        if (editing && visible) flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        else flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      // The bar strip is outside the normal layout limits, and the hole beside the pill is a cutout
      // the default mode would push the window away from.
      if (inStatusBar) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
      return WindowParamsSpec(
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags = flags,
        gravity = Gravity.TOP or (if (rtl) Gravity.RIGHT else Gravity.LEFT),
        layoutInDisplayCutoutMode =
          if (inStatusBar) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
          else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,
        alpha = if (visible) finite(opacity).coerceIn(0f, 1f) else 0f,
        format = PixelFormat.TRANSLUCENT,
      )
    }

    /** A comparison with NaN is always false, so a NaN opacity would slip past every bound. */
    private fun finite(opacity: Float): Float = if (opacity.isNaN()) 0f else opacity
  }
}
