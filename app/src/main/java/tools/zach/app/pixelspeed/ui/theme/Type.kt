package tools.zach.app.pixelspeed.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import tools.zach.app.pixelspeed.R

private fun flexFont(weight: FontWeight): Font =
  Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
  )

/** Google Sans Flex is one variable file; each weight is an axis setting on it. */
val GoogleSansFlex: FontFamily =
  FontFamily(
    flexFont(FontWeight.Normal),
    flexFont(FontWeight.Medium),
    flexFont(FontWeight.SemiBold),
    flexFont(FontWeight.Bold),
  )

private fun TextStyle.withFlex(): TextStyle = copy(fontFamily = GoogleSansFlex)

/**
 * The Material defaults with every style, emphasized ones included, moved onto Google Sans Flex.
 */
val Typography: Typography =
  Typography().let { base ->
    base.copy(
      displayLarge = base.displayLarge.withFlex(),
      displayMedium = base.displayMedium.withFlex(),
      displaySmall = base.displaySmall.withFlex(),
      headlineLarge = base.headlineLarge.withFlex(),
      headlineMedium = base.headlineMedium.withFlex(),
      headlineSmall = base.headlineSmall.withFlex(),
      titleLarge = base.titleLarge.withFlex(),
      titleMedium = base.titleMedium.withFlex(),
      titleSmall = base.titleSmall.withFlex(),
      bodyLarge = base.bodyLarge.withFlex(),
      bodyMedium = base.bodyMedium.withFlex(),
      bodySmall = base.bodySmall.withFlex(),
      labelLarge = base.labelLarge.withFlex(),
      labelMedium = base.labelMedium.withFlex(),
      labelSmall = base.labelSmall.withFlex(),
      displayLargeEmphasized = base.displayLargeEmphasized.withFlex(),
      displayMediumEmphasized = base.displayMediumEmphasized.withFlex(),
      displaySmallEmphasized = base.displaySmallEmphasized.withFlex(),
      headlineLargeEmphasized = base.headlineLargeEmphasized.withFlex(),
      headlineMediumEmphasized = base.headlineMediumEmphasized.withFlex(),
      headlineSmallEmphasized = base.headlineSmallEmphasized.withFlex(),
      titleLargeEmphasized = base.titleLargeEmphasized.withFlex(),
      titleMediumEmphasized = base.titleMediumEmphasized.withFlex(),
      titleSmallEmphasized = base.titleSmallEmphasized.withFlex(),
      bodyLargeEmphasized = base.bodyLargeEmphasized.withFlex(),
      bodyMediumEmphasized = base.bodyMediumEmphasized.withFlex(),
      bodySmallEmphasized = base.bodySmallEmphasized.withFlex(),
      labelLargeEmphasized = base.labelLargeEmphasized.withFlex(),
      labelMediumEmphasized = base.labelMediumEmphasized.withFlex(),
      labelSmallEmphasized = base.labelSmallEmphasized.withFlex(),
    )
  }
