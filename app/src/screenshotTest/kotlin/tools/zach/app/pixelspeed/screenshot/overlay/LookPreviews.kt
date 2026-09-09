package tools.zach.app.pixelspeed.screenshot.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.screenshot.ComponentVariants
import tools.zach.app.pixelspeed.screenshot.Samples
import tools.zach.app.pixelspeed.screenshot.Shot
import tools.zach.app.pixelspeed.settings.AppearanceSettings
import tools.zach.app.pixelspeed.settings.ColorMode
import tools.zach.app.pixelspeed.settings.ContentSettings
import tools.zach.app.pixelspeed.settings.DirectionMarker
import tools.zach.app.pixelspeed.settings.FontFamilyChoice
import tools.zach.app.pixelspeed.settings.FontWeightChoice
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.ui.overlay.FlankHalf
import tools.zach.app.pixelspeed.ui.overlay.SlimRibbon
import tools.zach.app.pixelspeed.ui.overlay.SpeedReadout
import tools.zach.app.pixelspeed.ui.overlay.rememberOverlayComposition

@Composable
private fun Look(style: OverlayStyle) {
  val profile = Profile(appearance = AppearanceSettings(style = style))
  val composition = rememberOverlayComposition(profile, Samples.wifi, promotedChipAvailable = false)
  Shot {
    Column {
      SpeedReadout(
        values = composition.measureValues,
        style = style,
        colors = composition.presentation.colors,
        textSize = composition.presentation.textSize,
        readoutStyle = composition.presentation.readoutStyle,
        showDownload = composition.presentation.showDownload,
        showUpload = composition.presentation.showUpload,
        directionOrder = composition.presentation.directionOrder,
        directionMarker = composition.presentation.directionMarker,
        combined = composition.presentation.combined,
        showNetworkGlyph = composition.presentation.showNetworkGlyph,
      )
      Spacer(Modifier.height(4.dp))
      SpeedReadout(
        values = composition.values,
        style = style,
        colors = composition.presentation.colors,
        textSize = composition.presentation.textSize,
        readoutStyle = composition.presentation.readoutStyle,
        showDownload = composition.presentation.showDownload,
        showUpload = composition.presentation.showUpload,
        directionOrder = composition.presentation.directionOrder,
        directionMarker = composition.presentation.directionMarker,
        combined = composition.presentation.combined,
        showNetworkGlyph = composition.presentation.showNetworkGlyph,
      )
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun LookChip() {
  Look(OverlayStyle.CHIP)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookCompact() {
  Look(OverlayStyle.COMPACT)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookCompact12() {
  Look(OverlayStyle.COMPACT12)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookStack() {
  Look(OverlayStyle.STACK)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookStackNoUnit() {
  Look(OverlayStyle.STACK_NO_UNIT)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookSingle() {
  Look(OverlayStyle.SINGLE)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookSingleNoUnit() {
  Look(OverlayStyle.SINGLE_NO_UNIT)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookHalo() {
  Look(OverlayStyle.HALO)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookGlyphs() {
  Look(OverlayStyle.GLYPHS)
}

@PreviewTest
@ComponentVariants
@Composable
fun LookSlimRibbon() {
  val profile = Profile()
  val composition = rememberOverlayComposition(profile, Samples.wifi, promotedChipAvailable = false)
  Shot {
    Column {
      SlimRibbon(
        values = composition.measureValues,
        colors = composition.presentation.colors,
        readoutStyle = composition.presentation.readoutStyle,
      )
      Spacer(Modifier.height(4.dp))
      SlimRibbon(
        values = composition.values,
        colors = composition.presentation.colors,
        readoutStyle = composition.presentation.readoutStyle,
      )
    }
  }
}

/** Both flank halves at the widest and the live values, for one profile. */
@Composable
private fun FlankHalves(profile: Profile) {
  val composition = rememberOverlayComposition(profile, Samples.wifi, promotedChipAvailable = false)
  Shot {
    Column {
      Row {
        FlankHalf(
          composition.measureValues,
          download = true,
          presentation = composition.presentation,
        )
        Spacer(Modifier.width(8.dp))
        FlankHalf(
          composition.measureValues,
          download = false,
          presentation = composition.presentation,
        )
      }
      Spacer(Modifier.height(4.dp))
      Row {
        FlankHalf(composition.values, download = true, presentation = composition.presentation)
        Spacer(Modifier.width(8.dp))
        FlankHalf(composition.values, download = false, presentation = composition.presentation)
      }
    }
  }
}

@PreviewTest
@ComponentVariants
@Composable
fun LookFlankHalves() {
  FlankHalves(Profile())
}

/**
 * The presentation fields a flank half draws apart from the tints (fill, text, corners, typeface,
 * weight, marker), each set off its default, so a half that ignores one changes this shot; the
 * tints have their own shot, since a tint masks the text color.
 */
@PreviewTest
@ComponentVariants
@Composable
fun LookFlankHalvesStyled() {
  FlankHalves(
    Profile(
      content = ContentSettings(directionMarker = DirectionMarker.LETTERS),
      appearance =
        AppearanceSettings(
          colorMode = ColorMode.MONOCHROME_LIGHT,
          cornerRadiusDp = 4,
          fontFamily = FontFamilyChoice.MONOSPACE,
          fontWeight = FontWeightChoice.BOLD,
        ),
    )
  )
}

/** The direction tints on the dynamic chip fill, the mode every fresh install draws with. */
@PreviewTest
@ComponentVariants
@Composable
fun LookFlankHalvesTinted() {
  FlankHalves(Profile(appearance = AppearanceSettings(colorByDirection = true)))
}

/** No marker drawn: the two halves are bare digits, told apart by position and a spoken name. */
@PreviewTest
@ComponentVariants
@Composable
fun LookFlankHalvesBare() {
  FlankHalves(Profile(content = ContentSettings(directionMarker = DirectionMarker.NONE)))
}
