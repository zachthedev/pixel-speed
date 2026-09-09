package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.overlay.StatusBarFit
import tools.zach.app.pixelspeed.settings.AppearanceSettings
import tools.zach.app.pixelspeed.settings.ColorMode
import tools.zach.app.pixelspeed.settings.FontFamilyChoice
import tools.zach.app.pixelspeed.settings.FontWeightChoice
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsBounds

private const val MEGABIT_BYTES_PER_SECOND = SettingsBounds.BYTES_PER_MEGABIT
private const val PERCENT: Float = 100f

private val CORNER_RADIUS_PRESETS = listOf(0, 8, 16, SettingsBounds.CORNER_RADIUS_DP.last)

@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  AppearanceContent(
    settings = settings,
    onBack = onBack,
    onUpdateProfile = viewModel::updateProfile,
  )
}

@Composable
fun AppearanceContent(
  settings: Settings?,
  onBack: () -> Unit,
  onUpdateProfile: ((Profile) -> Profile) -> Unit,
) {
  val resources = LocalResources.current
  val advanced = settings?.advancedMode ?: false
  val appearance = settings?.activeProfile?.appearance ?: AppearanceSettings()
  val limit = obscuringLimit()
  // A fit other than Auto can move the readout off the bar, and Custom colors have their pickers
  // behind advanced mode, so each stays reachable in regular mode while it is in effect.
  val profileId = settings?.activeProfileId
  val showStatusBarFit =
    rememberRevealed(advanced, appearance.statusBarFit != StatusBarFit.AUTO, profileId)
  val showRibbonFallback = rememberRevealed(advanced, appearance.ribbonFallback, profileId)
  val showCustomColors =
    rememberRevealed(advanced, appearance.colorMode == ColorMode.CUSTOM, profileId)
  // The scaffold and its Back button show while the store's first read is still on its way.
  val firstCardTitle =
    settings?.let {
      stringResource(R.string.settings_appearance_card_look_profile, it.activeProfile.name)
    } ?: stringResource(R.string.settings_appearance_card_look)
  SettingsScreen(
    title = stringResource(R.string.settings_title_appearance),
    onBack = onBack,
  ) {
    if (settings == null) return@SettingsScreen

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
      onUpdateProfile { it.copy(appearance = transform(it.appearance)) }
    }

    settingsCard(title = firstCardTitle) {
      row { shapes ->
        ChoiceRow(
          shapes = shapes,
          title = stringResource(R.string.settings_appearance_style_title),
          options = OverlayStyle.entries,
          selected = appearance.style,
          label = { overlayStyleLabel(resources, it) },
          onSelected = { value -> updateAppearance { it.copy(style = value) } },
          description = stringResource(R.string.settings_appearance_style_subtitle),
        )
      }
      if (showStatusBarFit) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_status_bar_fit_title),
            options = StatusBarFit.entries,
            selected = appearance.statusBarFit,
            label = { statusBarFitLabel(resources, it) },
            onSelected = { value -> updateAppearance { it.copy(statusBarFit = value) } },
            subtitle = stringResource(R.string.settings_appearance_status_bar_fit_subtitle),
          )
        }
      }
      if (showRibbonFallback && appearance.statusBarFit == StatusBarFit.AUTO) {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_ribbon_fallback_title),
            checked = appearance.ribbonFallback,
            onCheckedChange = { value -> updateAppearance { it.copy(ribbonFallback = value) } },
            subtitle = stringResource(R.string.settings_appearance_ribbon_fallback_subtitle),
          )
        }
      }
      row { shapes ->
        val opacityPercent = (appearance.windowOpacity * PERCENT).roundToInt()
        // The platform compares the float, so the warning does too; the percent is for display.
        val opacityWarning =
          if (appearance.windowOpacity > limit) {
            resources.getString(
              R.string.settings_appearance_window_opacity_warning,
              obscuringLimitPercent(limit),
            )
          } else null
        if (advanced) {
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_window_opacity_title),
            value = opacityPercent,
            range = SettingsBounds.WINDOW_OPACITY_PERCENT,
            format = { resources.getString(R.string.settings_appearance_window_opacity_value, it) },
            onValueChange = { value ->
              updateAppearance { it.copy(windowOpacity = value / PERCENT) }
            },
            warning = opacityWarning,
          )
        } else {
          val opacityPresets: List<Preset<Int>> =
            listOf(
              // The consequence of Opaque is heard before it is chosen.
              Preset(
                label = stringResource(R.string.settings_appearance_window_opacity_opaque),
                value = 100,
                description =
                  if (1f > limit) {
                    resources.getString(
                      R.string.settings_appearance_window_opacity_warning,
                      obscuringLimitPercent(limit),
                    )
                  } else null,
              ),
              Preset(stringResource(R.string.settings_appearance_window_opacity_translucent), 80),
              Preset(stringResource(R.string.settings_appearance_window_opacity_subtle), 50),
            )
          PresetRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_window_opacity_title),
            presets = opacityPresets,
            value = opacityPercent,
            valueLabel = {
              resources.getString(R.string.settings_appearance_window_opacity_value, it)
            },
            onSelected = { value -> updateAppearance { it.copy(windowOpacity = value / PERCENT) } },
            warning = opacityWarning,
          )
        }
      }
      if (advanced) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_corner_radius_title),
            options = CORNER_RADIUS_PRESETS,
            selected = appearance.cornerRadiusDp,
            label = { cornerRadiusLabel(resources, it) },
            onSelected = { value -> updateAppearance { it.copy(cornerRadiusDp = value) } },
            subtitle = stringResource(R.string.settings_appearance_corner_radius_subtitle),
          )
        }
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_blur_behind_title),
            checked = appearance.blurBehind,
            onCheckedChange = { value -> updateAppearance { it.copy(blurBehind = value) } },
            subtitle = stringResource(R.string.settings_appearance_blur_behind_subtitle),
          )
        }
      }
    }

    settingsCard(titleRes = R.string.settings_appearance_card_color) {
      row { shapes ->
        val colorModes: List<ColorMode> =
          ColorMode.entries.filter { showCustomColors || it != ColorMode.CUSTOM }
        SegmentedRow(
          shapes = shapes,
          title = stringResource(R.string.settings_appearance_color_mode_title),
          options = colorModes,
          selected = appearance.colorMode,
          label = { colorModeLabel(resources, it) },
          onSelected = { value -> updateAppearance { it.copy(colorMode = value) } },
          subtitle = stringResource(R.string.settings_appearance_color_mode_subtitle),
        )
      }
      if (showCustomColors) {
        row { shapes ->
          ColorSettingRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_background_color_title),
            descriptionSubtitle =
              stringResource(R.string.settings_appearance_background_color_subtitle),
            value = appearance.backgroundColor,
            fallback = MaterialTheme.colorScheme.primary,
            enabled = appearance.colorMode == ColorMode.CUSTOM,
            onPick = { value -> updateAppearance { it.copy(backgroundColor = value) } },
          )
        }
        row { shapes ->
          ColorSettingRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_text_color_title),
            descriptionSubtitle = stringResource(R.string.settings_appearance_text_color_subtitle),
            value = appearance.textColor,
            fallback = MaterialTheme.colorScheme.onPrimary,
            enabled = appearance.colorMode == ColorMode.CUSTOM,
            onPick = { value -> updateAppearance { it.copy(textColor = value) } },
          )
        }
      }
      if (advanced) {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_color_by_direction_title),
            checked = appearance.colorByDirection,
            onCheckedChange = { value -> updateAppearance { it.copy(colorByDirection = value) } },
            subtitle = stringResource(R.string.settings_appearance_color_by_direction_subtitle),
          )
        }
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_threshold_highlight_title),
            checked = appearance.thresholdHighlight,
            onCheckedChange = { value -> updateAppearance { it.copy(thresholdHighlight = value) } },
            subtitle = stringResource(R.string.settings_appearance_threshold_highlight_subtitle),
          )
        }
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_threshold_value_title),
            value =
              (appearance.thresholdBytesPerSecond / MEGABIT_BYTES_PER_SECOND)
                .toInt()
                .coerceIn(SettingsBounds.THRESHOLD_MBPS),
            range = SettingsBounds.THRESHOLD_MBPS,
            format = {
              resources.getString(R.string.settings_appearance_threshold_value_format, it)
            },
            onValueChange = { value ->
              updateAppearance {
                it.copy(thresholdBytesPerSecond = value * MEGABIT_BYTES_PER_SECOND)
              }
            },
            enabled = appearance.thresholdHighlight,
          )
        }
      }
    }

    if (advanced) {
      settingsCard(titleRes = R.string.settings_appearance_card_type) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_font_family_title),
            options = FontFamilyChoice.entries,
            selected = appearance.fontFamily,
            label = { fontFamilyLabel(resources, it) },
            onSelected = { value -> updateAppearance { it.copy(fontFamily = value) } },
            subtitle = stringResource(R.string.settings_appearance_font_family_subtitle),
          )
        }
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_appearance_font_weight_title),
            options = FontWeightChoice.entries,
            selected = appearance.fontWeight,
            label = { fontWeightLabel(resources, it) },
            onSelected = { value -> updateAppearance { it.copy(fontWeight = value) } },
            subtitle = stringResource(R.string.settings_appearance_font_weight_subtitle),
          )
        }
      }
    }
  }
}

/** A [ColorRow] that opens [ColorSheet] and shows the theme fallback when [value] is null. */
@Composable
private fun ColorSettingRow(
  shapes: ListItemShapes,
  title: String,
  descriptionSubtitle: String,
  value: Int?,
  fallback: Color,
  enabled: Boolean,
  onPick: (Int?) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  ColorRow(
    shapes = shapes,
    title = title,
    color = value?.let { Color(it) } ?: fallback,
    subtitle =
      if (value == null) stringResource(R.string.settings_appearance_from_theme)
      else descriptionSubtitle,
    enabled = enabled,
    onClick = { open = true },
  )
  if (open) {
    ColorSheet(
      title = title,
      current = value,
      onPick = onPick,
      onDismiss = { open = false },
    )
  }
}

private fun overlayStyleLabel(resources: Resources, style: OverlayStyle): String =
  resources.getString(
    when (style) {
      OverlayStyle.CHIP -> R.string.settings_appearance_style_chip
      OverlayStyle.COMPACT -> R.string.settings_appearance_style_compact
      OverlayStyle.COMPACT12 -> R.string.settings_appearance_style_compact12
      OverlayStyle.STACK -> R.string.settings_appearance_style_stack
      OverlayStyle.STACK_NO_UNIT -> R.string.settings_appearance_style_stack_no_unit
      OverlayStyle.SINGLE -> R.string.settings_appearance_style_single
      OverlayStyle.SINGLE_NO_UNIT -> R.string.settings_appearance_style_single_no_unit
      OverlayStyle.HALO -> R.string.settings_appearance_style_halo
      OverlayStyle.GLYPHS -> R.string.settings_appearance_style_glyphs
    }
  )

private fun statusBarFitLabel(resources: Resources, fit: StatusBarFit): String =
  resources.getString(
    when (fit) {
      StatusBarFit.AUTO -> R.string.settings_appearance_status_bar_fit_auto
      StatusBarFit.MANUAL -> R.string.settings_appearance_status_bar_fit_manual
      StatusBarFit.SLIM -> R.string.settings_appearance_status_bar_fit_slim
    }
  )

private fun cornerRadiusLabel(resources: Resources, radiusDp: Int): String =
  resources.getString(
    when (radiusDp) {
      0 -> R.string.settings_appearance_corner_radius_square
      8 -> R.string.settings_appearance_corner_radius_rounded
      16 -> R.string.settings_appearance_corner_radius_round
      else -> R.string.settings_appearance_corner_radius_pill
    }
  )

private fun colorModeLabel(resources: Resources, mode: ColorMode): String =
  resources.getString(
    when (mode) {
      ColorMode.DYNAMIC -> R.string.settings_appearance_color_mode_dynamic
      ColorMode.CUSTOM -> R.string.settings_appearance_color_mode_custom
      ColorMode.MONOCHROME_LIGHT -> R.string.settings_appearance_color_mode_light
      ColorMode.MONOCHROME_DARK -> R.string.settings_appearance_color_mode_dark
    }
  )

private fun fontFamilyLabel(resources: Resources, choice: FontFamilyChoice): String =
  resources.getString(
    when (choice) {
      FontFamilyChoice.SYSTEM -> R.string.settings_appearance_font_family_system
      FontFamilyChoice.MONOSPACE -> R.string.settings_appearance_font_family_monospace
    }
  )

private fun fontWeightLabel(resources: Resources, choice: FontWeightChoice): String =
  resources.getString(
    when (choice) {
      FontWeightChoice.REGULAR -> R.string.settings_appearance_font_weight_regular
      FontWeightChoice.MEDIUM -> R.string.settings_appearance_font_weight_medium
      FontWeightChoice.BOLD -> R.string.settings_appearance_font_weight_bold
    }
  )
