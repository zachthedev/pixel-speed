package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.format.AutoFloor
import tools.zach.app.pixelspeed.format.FormatOptions
import tools.zach.app.pixelspeed.format.PrecisionMode
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.format.UnitPrefix
import tools.zach.app.pixelspeed.format.UnitSystem
import tools.zach.app.pixelspeed.settings.ContentSettings
import tools.zach.app.pixelspeed.settings.DirectionMarker
import tools.zach.app.pixelspeed.settings.DirectionOrder
import tools.zach.app.pixelspeed.settings.Layout
import tools.zach.app.pixelspeed.settings.SettingsBounds

@Composable
fun ContentScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val resources = LocalResources.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val firstCardTitle =
    settings?.let {
      stringResource(R.string.settings_content_card_readout_profile, it.activeProfile.name)
    } ?: stringResource(R.string.settings_content_card_readout)
  // A pinned prefix changes what every number says, so it stays reachable in regular mode while
  // it is in effect.
  val showUnitPrefix =
    rememberRevealed(
      settings?.advancedMode ?: false,
      settings?.activeProfile?.content?.format?.unitPrefix?.let { it != FormatOptions().unitPrefix }
        ?: false,
      settings?.activeProfileId,
    )
  // A raised or lowered floor changes what a low rate says, so it stays reachable the same way.
  val showAutoFloor =
    rememberRevealed(
      settings?.advancedMode ?: false,
      settings?.activeProfile?.content?.format?.autoFloor?.let { it != FormatOptions().autoFloor }
        ?: false,
      settings?.activeProfileId,
    )
  // A precision away from the default widens every number, so it stays reachable the same way.
  val showPrecision =
    rememberRevealed(
      settings?.advancedMode ?: false,
      settings?.activeProfile?.content?.format?.let {
        val defaults = FormatOptions()
        it.precisionMode != defaults.precisionMode ||
          it.significantFigures != defaults.significantFigures ||
          it.decimals != defaults.decimals
      } ?: false,
      settings?.activeProfileId,
    )
  SettingsScreen(
    title = stringResource(R.string.settings_title_content),
    onBack = onBack,
  ) {
    val current = settings ?: return@SettingsScreen
    val content = current.activeProfile.content
    val format = content.format
    val advanced = current.advancedMode

    fun updateContent(transform: (ContentSettings) -> ContentSettings) {
      viewModel.updateProfile { it.copy(content = transform(it.content)) }
    }

    fun updateFormat(transform: (FormatOptions) -> FormatOptions) {
      updateContent { it.copy(format = transform(it.format)) }
    }

    settingsCard(title = firstCardTitle) {
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_content_show_download_title),
          checked = content.showDownload,
          onCheckedChange = { value -> updateContent { it.copy(showDownload = value) } },
          subtitle = stringResource(R.string.settings_content_show_download_subtitle),
        )
      }
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_content_show_upload_title),
          checked = content.showUpload,
          onCheckedChange = { value -> updateContent { it.copy(showUpload = value) } },
          subtitle = stringResource(R.string.settings_content_show_upload_subtitle),
        )
      }
      row { shapes ->
        SegmentedRow(
          shapes = shapes,
          title = stringResource(R.string.settings_content_layout_title),
          options = Layout.entries,
          selected = content.layout,
          label = { layoutLabel(resources, it) },
          onSelected = { value -> updateContent { it.copy(layout = value) } },
          subtitle = stringResource(R.string.settings_content_layout_subtitle),
        )
      }
      if (advanced) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_direction_order_title),
            options = DirectionOrder.entries,
            selected = content.directionOrder,
            label = { directionOrderLabel(resources, it) },
            onSelected = { value -> updateContent { it.copy(directionOrder = value) } },
            subtitle = stringResource(R.string.settings_content_direction_order_subtitle),
          )
        }
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_direction_marker_title),
            options = DirectionMarker.entries,
            selected = content.directionMarker,
            label = { directionMarkerLabel(resources, it) },
            onSelected = { value -> updateContent { it.copy(directionMarker = value) } },
            subtitle = stringResource(R.string.settings_content_direction_marker_subtitle),
          )
        }
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_network_glyph_title),
            checked = content.showNetworkGlyph,
            onCheckedChange = { value -> updateContent { it.copy(showNetworkGlyph = value) } },
            subtitle = stringResource(R.string.settings_content_network_glyph_subtitle),
          )
        }
      }
    }

    settingsCard(titleRes = R.string.settings_content_card_units) {
      row { shapes ->
        SegmentedRow(
          shapes = shapes,
          title = stringResource(R.string.settings_content_unit_system_title),
          options = UnitSystem.entries,
          selected = format.unitSystem,
          label = { unitSystemLabel(resources, it) },
          onSelected = { value -> updateFormat { it.copy(unitSystem = value) } },
          subtitle = stringResource(R.string.settings_content_unit_system_subtitle),
        )
      }
      if (showUnitPrefix) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_unit_prefix_title),
            options = UnitPrefix.entries,
            selected = format.unitPrefix,
            label = { unitPrefixLabel(resources, it, format) },
            onSelected = { value -> updateFormat { it.copy(unitPrefix = value) } },
            subtitle = stringResource(R.string.settings_content_unit_prefix_subtitle),
          )
        }
      }
      if (showAutoFloor && format.unitPrefix == UnitPrefix.AUTO) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_auto_floor_title),
            options = AutoFloor.entries,
            selected = format.autoFloor,
            label = { autoFloorLabel(resources, it, format) },
            onSelected = { value -> updateFormat { it.copy(autoFloor = value) } },
            subtitle = stringResource(R.string.settings_content_auto_floor_subtitle),
          )
        }
      }
      if (showPrecision) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_precision_mode_title),
            options = PrecisionMode.entries,
            selected = format.precisionMode,
            label = { precisionModeLabel(resources, it) },
            onSelected = { value -> updateFormat { it.copy(precisionMode = value) } },
            subtitle = stringResource(R.string.settings_content_precision_mode_subtitle),
          )
        }
        if (format.precisionMode == PrecisionMode.SIGNIFICANT) {
          row { shapes ->
            SliderRow(
              shapes = shapes,
              title = stringResource(R.string.settings_content_significant_figures_title),
              value = format.significantFigures,
              range = SettingsBounds.SIGNIFICANT_FIGURES,
              format = {
                resources.getString(R.string.settings_content_significant_figures_value, it)
              },
              onValueChange = { value -> updateFormat { it.copy(significantFigures = value) } },
            )
          }
        }
        if (format.precisionMode == PrecisionMode.FIXED) {
          row { shapes ->
            SliderRow(
              shapes = shapes,
              title = stringResource(R.string.settings_content_decimals_title),
              value = format.decimals,
              range = SettingsBounds.DECIMALS,
              format = { resources.getString(R.string.settings_content_decimals_value, it) },
              onValueChange = { value -> updateFormat { it.copy(decimals = value) } },
            )
          }
        }
      }
      row { shapes ->
        SegmentedRow(
          shapes = shapes,
          title = stringResource(R.string.settings_content_unit_label_title),
          options = UnitLabel.entries,
          selected = format.unitLabel,
          label = { unitLabelLabel(resources, it, format) },
          onSelected = { value -> updateFormat { it.copy(unitLabel = value) } },
          subtitle = stringResource(R.string.settings_content_unit_label_subtitle),
        )
      }
      if (advanced) {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_fixed_width_digits_title),
            checked = format.fixedWidthDigits,
            onCheckedChange = { value -> updateFormat { it.copy(fixedWidthDigits = value) } },
            subtitle = stringResource(R.string.settings_content_fixed_width_digits_subtitle),
          )
        }
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_content_binary_prefixes_title),
            checked = format.binaryPrefixes,
            onCheckedChange = { value -> updateFormat { it.copy(binaryPrefixes = value) } },
            subtitle = stringResource(R.string.settings_content_binary_prefixes_subtitle),
            enabled = format.unitSystem == UnitSystem.BYTES_PER_SECOND,
          )
        }
      }
    }
  }
}

private fun layoutLabel(resources: Resources, layout: Layout): String =
  resources.getString(
    when (layout) {
      Layout.ONE_LINE -> R.string.settings_content_layout_one_line
      Layout.TWO_LINES -> R.string.settings_content_layout_two_lines
      Layout.DOWNLOAD_ONLY -> R.string.settings_content_layout_download_only
      Layout.COMBINED -> R.string.settings_content_layout_combined
    }
  )

private fun directionOrderLabel(resources: Resources, order: DirectionOrder): String =
  resources.getString(
    when (order) {
      DirectionOrder.DOWNLOAD_FIRST -> R.string.settings_content_direction_order_download_first
      DirectionOrder.UPLOAD_FIRST -> R.string.settings_content_direction_order_upload_first
    }
  )

private fun directionMarkerLabel(resources: Resources, marker: DirectionMarker): String =
  resources.getString(
    when (marker) {
      DirectionMarker.ARROWS -> R.string.settings_content_direction_marker_arrows
      DirectionMarker.LETTERS -> R.string.settings_content_direction_marker_letters
      DirectionMarker.NONE -> R.string.settings_content_direction_marker_none
    }
  )

private fun unitSystemLabel(resources: Resources, system: UnitSystem): String =
  resources.getString(
    when (system) {
      UnitSystem.BITS_PER_SECOND -> R.string.settings_content_unit_system_bits
      UnitSystem.BYTES_PER_SECOND -> R.string.settings_content_unit_system_bytes
    }
  )

private fun precisionModeLabel(resources: Resources, mode: PrecisionMode): String =
  resources.getString(
    when (mode) {
      PrecisionMode.SIGNIFICANT -> R.string.settings_content_precision_mode_significant
      PrecisionMode.FIXED -> R.string.settings_content_precision_mode_fixed
    }
  )

private fun autoFloorLabel(resources: Resources, floor: AutoFloor, format: FormatOptions): String =
  resources.getString(
    when (floor) {
      AutoFloor.BARE ->
        if (format.unitSystem == UnitSystem.BITS_PER_SECOND) {
          R.string.settings_content_auto_floor_bare_bits
        } else {
          R.string.settings_content_auto_floor_bare_bytes
        }
      AutoFloor.KILO -> tierLabel(format, UnitPrefix.KILO)
      AutoFloor.MEGA -> tierLabel(format, UnitPrefix.MEGA)
    }
  )

/** The unit the readout draws at [prefix] under [format], as the pickers name it. */
private fun tierLabel(format: FormatOptions, prefix: UnitPrefix): Int {
  val bits = format.unitSystem == UnitSystem.BITS_PER_SECOND
  return when (prefix) {
    UnitPrefix.AUTO -> R.string.settings_content_unit_prefix_auto
    UnitPrefix.KILO ->
      when {
        bits -> R.string.settings_content_unit_prefix_kilo_bits
        format.binaryPrefixes -> R.string.settings_content_unit_prefix_kilo_binary
        else -> R.string.settings_content_unit_prefix_kilo_bytes
      }
    UnitPrefix.MEGA ->
      when {
        bits -> R.string.settings_content_unit_prefix_mega_bits
        format.binaryPrefixes -> R.string.settings_content_unit_prefix_mega_binary
        else -> R.string.settings_content_unit_prefix_mega_bytes
      }
    UnitPrefix.GIGA ->
      when {
        bits -> R.string.settings_content_unit_prefix_giga_bits
        format.binaryPrefixes -> R.string.settings_content_unit_prefix_giga_binary
        else -> R.string.settings_content_unit_prefix_giga_bytes
      }
  }
}

private fun unitLabelLabel(
  resources: Resources,
  unitLabel: UnitLabel,
  format: FormatOptions,
): String {
  val bits = format.unitSystem == UnitSystem.BITS_PER_SECOND
  return when (unitLabel) {
    UnitLabel.AUTO -> resources.getString(R.string.settings_content_unit_label_auto)
    UnitLabel.FULL ->
      resources.getString(
        when {
          bits -> R.string.settings_content_unit_label_full_bits
          format.binaryPrefixes -> R.string.settings_content_unit_label_full_binary
          else -> R.string.settings_content_unit_label_full_bytes
        }
      )
    UnitLabel.SHORT ->
      resources.getString(
        when {
          bits -> R.string.settings_content_unit_label_short_bits
          format.binaryPrefixes -> R.string.settings_content_unit_label_short_binary
          else -> R.string.settings_content_unit_label_short_bytes
        }
      )
    UnitLabel.NONE -> resources.getString(R.string.settings_content_unit_label_none)
  }
}

private fun unitPrefixLabel(
  resources: Resources,
  prefix: UnitPrefix,
  format: FormatOptions,
): String = resources.getString(tierLabel(format, prefix))
