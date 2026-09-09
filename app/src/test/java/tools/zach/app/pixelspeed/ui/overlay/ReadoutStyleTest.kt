package tools.zach.app.pixelspeed.ui.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.AppearanceSettings
import tools.zach.app.pixelspeed.settings.ColorMode
import tools.zach.app.pixelspeed.settings.FontFamilyChoice
import tools.zach.app.pixelspeed.settings.FontWeightChoice
import tools.zach.app.pixelspeed.settings.PositionSettings

class ReadoutStyleTest {

  companion object {
    private val position = PositionSettings(chipPaddingDp = 8, minWidthDp = 40)

    /** The Pixel chip fill (system_accent1_600) the dynamic mode draws with. */
    private const val CHIP_FILL: Int = 0xFF4B5D8B.toInt()

    /** The Pixel chip text (system_neutral1_50) beside it. */
    private const val CHIP_TEXT: Int = 0xFFF2F0F4.toInt()

    private fun from(
      appearance: AppearanceSettings,
      position: PositionSettings,
      sample: SpeedSample?,
    ): ReadoutStyle =
      ReadoutStyles.from(
        appearance,
        position,
        sample,
        dynamicFill = CHIP_FILL,
        dynamicText = CHIP_TEXT,
      )

    private val supportedSample =
      SpeedSample(
        rxBytesPerSecond = 0,
        txBytesPerSecond = 0,
        atElapsedNanos = 0,
        transport = Transport.WIFI,
      )

    /**
     * Two tints must stay two colors to the eye, not only two integers: CIE76 distance above this
     * on every fill they are measured against.
     */
    private const val MIN_TINT_SEPARATION: Double = 30.0

    private fun assertSeparated(a: Int, b: Int, fill: String) {
      val distance = labDistance(a, b)
      assertTrue(
        distance >= MIN_TINT_SEPARATION,
        "tints ${a.toUInt().toString(16)} and ${b.toUInt().toString(16)} on $fill: dE $distance",
      )
    }

    /** CIE76 distance between two sRGB colors through CIE Lab under D65. */
    private fun labDistance(a: Int, b: Int): Double {
      val (l1, a1, b1) = lab(a)
      val (l2, a2, b2) = lab(b)
      return Math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
    }

    private fun lab(argb: Int): Triple<Double, Double, Double> {
      fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
      }
      val r = channel(16)
      val g = channel(8)
      val bl = channel(0)
      val x = (0.4124 * r + 0.3576 * g + 0.1805 * bl) / 0.95047
      val y = 0.2126 * r + 0.7152 * g + 0.0722 * bl
      val z = (0.0193 * r + 0.1192 * g + 0.9505 * bl) / 1.08883
      fun f(v: Double): Double = if (v > 0.008856) Math.cbrt(v) else 7.787 * v + 16.0 / 116.0
      return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    /** Hues around the wheel at three saturations, each solved to L* 38, 40, and 42. */
    @JvmStatic
    fun accentFills(): List<String> = buildList {
      for (hue in 0 until 360 step 15) {
        for (saturation in listOf(0.35, 0.7, 1.0)) {
          for (lStar in listOf(38.0, 40.0, 42.0)) {
            add(accent(hue.toDouble(), saturation, lStar).toUInt().toString(16))
          }
        }
      }
    }

    /** The sRGB color of [hue] and [saturation] whose relative luminance matches [lStar]. */
    private fun accent(hue: Double, saturation: Double, lStar: Double): Int {
      val target = Math.pow((lStar + 16.0) / 116.0, 3.0)
      var low = 0.0
      var high = 1.0
      var color = hsl(hue, saturation, 0.5)
      repeat(40) {
        val lightness = (low + high) / 2
        color = hsl(hue, saturation, lightness)
        if (relativeLuminance(color) < target) low = lightness else high = lightness
      }
      return color
    }

    private fun hsl(hue: Double, saturation: Double, lightness: Double): Int {
      val c = (1 - Math.abs(2 * lightness - 1)) * saturation
      val x = c * (1 - Math.abs((hue / 60.0) % 2 - 1))
      val m = lightness - c / 2
      val (r, g, b) =
        when {
          hue < 60 -> Triple(c, x, 0.0)
          hue < 120 -> Triple(x, c, 0.0)
          hue < 180 -> Triple(0.0, c, x)
          hue < 240 -> Triple(0.0, x, c)
          hue < 300 -> Triple(x, 0.0, c)
          else -> Triple(c, 0.0, x)
        }
      fun channel(v: Double): Int = Math.round((v + m) * 255).toInt().coerceIn(0, 255)
      return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    @JvmStatic
    fun colorModeCases(): List<Array<Any?>> =
      listOf(
        arrayOf(AppearanceSettings(colorMode = ColorMode.DYNAMIC), null, null),
        arrayOf(
          AppearanceSettings(
            colorMode = ColorMode.CUSTOM,
            backgroundColor = 0xFF112233.toInt(),
            textColor = 0xFF445566.toInt(),
          ),
          0xFF112233.toInt(),
          0xFF445566.toInt(),
        ),
        arrayOf(
          AppearanceSettings(
            colorMode = ColorMode.CUSTOM,
            backgroundColor = 0xFF112233.toInt(),
            textColor = null,
          ),
          0xFF112233.toInt(),
          null,
        ),
        arrayOf(
          AppearanceSettings(
            colorMode = ColorMode.CUSTOM,
            backgroundColor = null,
            textColor = 0xFF445566.toInt(),
          ),
          null,
          0xFF445566.toInt(),
        ),
        arrayOf(
          AppearanceSettings(colorMode = ColorMode.MONOCHROME_LIGHT),
          0xFFFFFFFF.toInt(),
          0xFF000000.toInt(),
        ),
        arrayOf(
          AppearanceSettings(colorMode = ColorMode.MONOCHROME_DARK),
          0xFF000000.toInt(),
          0xFFFFFFFF.toInt(),
        ),
      )
  }

  @ParameterizedTest(name = "{0} resolves to fill {1} and text {2}")
  @MethodSource("colorModeCases")
  fun `color mode resolves fill and text`(appearance: AppearanceSettings, fill: Int?, text: Int?) {
    val style = from(appearance, position, sample = null)
    assertEquals(fill, style.fill)
    assertEquals(text, style.text)
  }

  @Test
  fun `color by direction on the dynamic fill reads at AA and keeps two hues`() {
    val style = from(AppearanceSettings(colorByDirection = true), position, sample = null)
    val downloadTint = style.downloadTint!!
    val uploadTint = style.uploadTint!!
    for (tint in listOf(downloadTint, uploadTint)) {
      assertTrue(
        contrastRatio(tint, CHIP_FILL) >= MIN_TEXT_CONTRAST,
        "tint ${tint.toUInt().toString(16)}: ${contrastRatio(tint, CHIP_FILL)} under $MIN_TEXT_CONTRAST",
      )
    }
    assertNotEquals(downloadTint, uploadTint)
    assertNull(style.fill, "the dynamic mode keeps the platform fill")
  }

  @Test
  fun `a light fill takes the darker tints whatever the color mode`() {
    val whiteCustom =
      AppearanceSettings(
        colorMode = ColorMode.CUSTOM,
        backgroundColor = 0xFFFFFFFF.toInt(),
        colorByDirection = true,
      )
    val monochromeLight =
      AppearanceSettings(colorMode = ColorMode.MONOCHROME_LIGHT, colorByDirection = true)
    for (appearance in listOf(whiteCustom, monochromeLight)) {
      val style = from(appearance, position, sample = null)
      assertEquals(0xFF2E7D32.toInt(), style.downloadTint, appearance.colorMode.name)
      assertEquals(0xFF1565C0.toInt(), style.uploadTint, appearance.colorMode.name)
    }
  }

  @Test
  fun `on every gray the tints and the highlight read at AA or step aside`() {
    val grays = (0..255 step 15).map { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
    for (fill in grays + 0xFFFFFFFF.toInt()) {
      val appearance =
        AppearanceSettings(
          colorMode = ColorMode.CUSTOM,
          backgroundColor = fill,
          colorByDirection = true,
          thresholdHighlight = true,
          thresholdBytesPerSecond = 1_000_000,
        )
      val busy = supportedSample.copy(rxBytesPerSecond = 1_000_000)
      val style = from(appearance, position, busy)
      val name = fill.toUInt().toString(16)
      // Every color the pill draws reads, including the text a stepped-aside tint falls back to.
      val drawnText = style.text ?: CHIP_TEXT
      for (color in listOfNotNull(style.downloadTint, style.uploadTint, drawnText)) {
        assertTrue(
          contrastRatio(color, fill) >= MIN_TEXT_CONTRAST,
          "fill $name color ${color.toUInt().toString(16)}: ${contrastRatio(color, fill)}",
        )
      }
      assertEquals(style.downloadTint == null, style.uploadTint == null, "one tint alone on $name")
      // The tints step aside only where no shade of one hue reads: a pole pair fails.
      if (style.downloadTint == null) {
        val greenFails =
          contrastRatio(0xFFD6F5D0.toInt(), fill) < MIN_TEXT_CONTRAST &&
            contrastRatio(0xFF1B5E20.toInt(), fill) < MIN_TEXT_CONTRAST
        val blueFails =
          contrastRatio(0xFFD6E4FF.toInt(), fill) < MIN_TEXT_CONTRAST &&
            contrastRatio(0xFF0D47A1.toInt(), fill) < MIN_TEXT_CONTRAST
        assertTrue(greenFails || blueFails, "tints stepped aside on $name with a readable pole")
      }
      if (style.downloadTint != null && style.uploadTint != null) {
        assertSeparated(style.downloadTint!!, style.uploadTint!!, name)
        assertNotEquals(style.text, style.downloadTint, "highlight matches download on $name")
        assertNotEquals(style.text, style.uploadTint, "highlight matches upload on $name")
      }
    }
  }

  @Test
  fun `the theme text on a custom fill steps to black or white only when it cannot be read`() {
    val midGray =
      AppearanceSettings(colorMode = ColorMode.CUSTOM, backgroundColor = 0xFF808080.toInt())
    assertEquals(0xFF000000.toInt(), from(midGray, position, sample = null).text)
    val lightGray =
      AppearanceSettings(colorMode = ColorMode.CUSTOM, backgroundColor = 0xFFBBBBBB.toInt())
    assertEquals(0xFF000000.toInt(), from(lightGray, position, sample = null).text)
    val nearBlack =
      AppearanceSettings(colorMode = ColorMode.CUSTOM, backgroundColor = 0xFF202020.toInt())
    assertNull(from(nearBlack, position, sample = null).text)
  }

  @Test
  fun `a fill that reads one tint but not the other tints neither direction`() {
    // Light green clears AA on this blue and light blue does not.
    val appearance =
      AppearanceSettings(
        colorMode = ColorMode.CUSTOM,
        backgroundColor = 0xFF0058F0.toInt(),
        colorByDirection = true,
      )
    val style = from(appearance, position, sample = null)
    assertNull(style.downloadTint)
    assertNull(style.uploadTint)
  }

  @Test
  fun `highlight on a light fill uses the darker amber`() {
    val appearance =
      AppearanceSettings(
        colorMode = ColorMode.CUSTOM,
        backgroundColor = 0xFFFFFFFF.toInt(),
        thresholdHighlight = true,
        thresholdBytesPerSecond = 1_000_000,
      )
    val atThreshold = supportedSample.copy(rxBytesPerSecond = 1_000_000)
    assertEquals(0xFF9A5B00.toInt(), from(appearance, position, atThreshold).text)
  }

  /**
   * The dynamic chip fill is tone 40 of the wallpaper's hue, so the fill the tints land on can be
   * any hue at that lightness; every one of them must read.
   */
  @ParameterizedTest(name = "fill {0}")
  @MethodSource("accentFills")
  fun `tints and the highlight read at AA on every tone 40 accent`(fill: String) {
    val fillArgb = fill.toLong(16).toInt()
    val appearance =
      AppearanceSettings(
        colorByDirection = true,
        thresholdHighlight = true,
        thresholdBytesPerSecond = 1_000_000,
      )
    val atThreshold = supportedSample.copy(rxBytesPerSecond = 1_000_000)
    val style =
      ReadoutStyles.from(
        appearance,
        position,
        atThreshold,
        dynamicFill = fillArgb,
        dynamicText = CHIP_TEXT,
      )
    val colors = listOf(style.downloadTint!!, style.uploadTint!!, style.text!!)
    for (color in colors) {
      assertTrue(
        contrastRatio(color, fillArgb) >= MIN_TEXT_CONTRAST,
        "${color.toUInt().toString(16)} on $fill: ${contrastRatio(color, fillArgb)}",
      )
    }
    assertSeparated(style.downloadTint!!, style.uploadTint!!, fill)
  }

  @Test
  fun `color by direction false gives no tints`() {
    val style = from(AppearanceSettings(colorByDirection = false), position, sample = null)
    assertNull(style.downloadTint)
    assertNull(style.uploadTint)
  }

  @Test
  fun `highlight requires threshold highlight and a supported sample at or above the threshold`() {
    val appearance =
      AppearanceSettings(thresholdHighlight = true, thresholdBytesPerSecond = 1_000_000)
    val atThreshold = supportedSample.copy(rxBytesPerSecond = 1_000_000, txBytesPerSecond = 0)
    val aboveOnUpload = supportedSample.copy(rxBytesPerSecond = 0, txBytesPerSecond = 2_000_000)
    val belowBoth = supportedSample.copy(rxBytesPerSecond = 500_000, txBytesPerSecond = 500_000)
    val unsupported = supportedSample.copy(rxBytesPerSecond = 5_000_000, supported = false)

    assertTrue(from(appearance, position, atThreshold).highlighted)
    assertTrue(from(appearance, position, aboveOnUpload).highlighted)
    assertFalse(from(appearance, position, belowBoth).highlighted)
    assertFalse(from(appearance, position, unsupported).highlighted)
    assertFalse(from(appearance, position, sample = null).highlighted)
    assertFalse(
      from(appearance.copy(thresholdHighlight = false), position, atThreshold).highlighted
    )
  }

  @Test
  fun `highlight on the dynamic fill is an amber that reads at AA`() {
    val appearance =
      AppearanceSettings(
        colorMode = ColorMode.DYNAMIC,
        thresholdHighlight = true,
        thresholdBytesPerSecond = 1_000_000,
      )
    val atThreshold = supportedSample.copy(rxBytesPerSecond = 1_000_000)
    val text = from(appearance, position, atThreshold).text!!
    assertTrue(
      contrastRatio(text, CHIP_FILL) >= MIN_TEXT_CONTRAST,
      "highlight ${text.toUInt().toString(16)}: ${contrastRatio(text, CHIP_FILL)} under $MIN_TEXT_CONTRAST",
    )
  }

  @Test
  fun `highlight leaves a custom text color alone`() {
    val appearance =
      AppearanceSettings(
        colorMode = ColorMode.CUSTOM,
        textColor = 0xFF445566.toInt(),
        thresholdHighlight = true,
        thresholdBytesPerSecond = 1_000_000,
      )
    val atThreshold = supportedSample.copy(rxBytesPerSecond = 1_000_000)
    val style = from(appearance, position, atThreshold)
    assertTrue(style.highlighted)
    assertEquals(0xFF445566.toInt(), style.text)
  }

  @Test
  fun `highlight overrides a monochrome text color`() {
    val appearance =
      AppearanceSettings(
        colorMode = ColorMode.MONOCHROME_DARK,
        thresholdHighlight = true,
        thresholdBytesPerSecond = 1_000_000,
      )
    val atThreshold = supportedSample.copy(rxBytesPerSecond = 1_000_000)
    val style = from(appearance, position, atThreshold)
    assertTrue(style.highlighted)
    assertEquals(0xFFFFB300.toInt(), style.text)
  }

  @Test
  fun `geometry and type pass through from settings`() {
    val appearance =
      AppearanceSettings(
        cornerRadiusDp = 12,
        fontFamily = FontFamilyChoice.MONOSPACE,
        fontWeight = FontWeightChoice.BOLD,
      )
    val style = from(appearance, position, sample = null)
    assertEquals(12, style.cornerRadiusDp)
    assertEquals(8, style.sidePaddingDp)
    assertEquals(40, style.minWidthDp)
    assertTrue(style.monospace)
    assertEquals(FontWeightChoice.BOLD, style.weight)
  }
}
