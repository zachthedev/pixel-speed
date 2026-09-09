package tools.zach.app.pixelspeed.ui.settings

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.overlay.LandscapeAnchorMode
import tools.zach.app.pixelspeed.service.accessibilitySettingsIntent
import tools.zach.app.pixelspeed.service.sideFlipServiceEnabled
import tools.zach.app.pixelspeed.settings.PositionSettings
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsBounds
import tools.zach.app.pixelspeed.state.MonitorState

@Composable
fun PositionScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val monitor by viewModel.monitor.collectAsStateWithLifecycle()
  PositionContent(
    settings = settings,
    monitor = monitor,
    onBack = onBack,
    onUpdateProfile = viewModel::updateProfile,
    onStartEditMode = viewModel::startEditMode,
  )
}

/**
 * The first anchor row edits the orientation the window is in: with one anchor for both
 * orientations it edits that anchor, and with a separate landscape anchor it edits the portrait
 * anchor in portrait and the landscape anchor in landscape, its title naming which, while a second
 * row carries the other orientation's anchor so it stays reachable with auto-rotate off. The
 * orientation is the activity window's, which is the display's on a phone; a split or freeform
 * window can differ from the display, and then the first row follows the window. The real overlay
 * moves as the choice changes, so the picture is on the screen rather than in a preview.
 */
@Composable
fun PositionContent(
  settings: Settings?,
  monitor: MonitorState,
  onBack: () -> Unit,
  onUpdateProfile: ((Profile) -> Profile) -> Unit,
  onStartEditMode: () -> Unit,
) {
  val resources = LocalResources.current
  val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val advanced = settings?.advancedMode ?: false
  val position = settings?.activeProfile?.position ?: PositionSettings()
  val defaults = PositionSettings()
  // A readout allowed under the hole can read as missing, a nudge or a size away from the default
  // can move or resize it, so each stays reachable in regular mode while it is in effect.
  val profileId = settings?.activeProfileId
  val separate = position.landscapeAnchorMode == LandscapeAnchorMode.SEPARATE
  val showLandscapeMode = rememberRevealed(advanced, separate, profileId)
  val showAvoidCutout = rememberRevealed(advanced, !position.avoidCutout, profileId)
  val showNudge =
    rememberRevealed(advanced, position.offsetXdp != 0 || position.offsetYdp != 0, profileId)
  val showChipPadding =
    rememberRevealed(advanced, position.chipPaddingDp != defaults.chipPaddingDp, profileId)
  val showMinWidth =
    rememberRevealed(advanced, position.minWidthDp != defaults.minWidthDp, profileId)
  val context = LocalContext.current
  // Set only while the switch sent the user to grant the service, so a resume with the service
  // already on from an earlier grant does not silently flip a side flip the user turned off.
  var sideFlipPending by rememberSaveable { mutableStateOf(false) }
  // A binder call, so it runs on resume rather than on every recomposition.
  var sideFlipServiceOn by remember { mutableStateOf(sideFlipServiceEnabled(context)) }
  LifecycleResumeEffect(Unit) {
    sideFlipServiceOn = sideFlipServiceEnabled(context)
    if (sideFlipPending && sideFlipServiceOn) {
      onUpdateProfile { it.copy(position = it.position.copy(sideFlip = true)) }
    }
    // A return without the grant drops the intent too, so a later grant for another reason
    // never turns the row on by itself.
    sideFlipPending = false
    onPauseOrDispose {}
  }
  // The scaffold and its Back button show while the store's first read is still on its way.
  val anchorCardTitle =
    settings?.let {
      stringResource(R.string.settings_position_card_anchor_profile, it.activeProfile.name)
    } ?: stringResource(R.string.settings_position_card_anchor)
  val nudgeCardTitle = stringResource(R.string.settings_position_card_nudge)
  val sizeCardTitle = stringResource(R.string.settings_position_card_size)
  val textSizePresets: List<Preset<Int>> =
    listOf(
      Preset(stringResource(R.string.settings_position_text_size_small), TEXT_SIZE_SMALL_SP),
      Preset(stringResource(R.string.settings_position_text_size_default), TEXT_SIZE_DEFAULT_SP),
      Preset(stringResource(R.string.settings_position_text_size_large), TEXT_SIZE_LARGE_SP),
    )
  SettingsScreen(
    title = stringResource(R.string.settings_title_position),
    onBack = onBack,
  ) {
    if (settings == null) return@SettingsScreen
    val editsLandscape = landscape && separate

    fun updatePosition(transform: (PositionSettings) -> PositionSettings) {
      onUpdateProfile { it.copy(position = transform(it.position)) }
    }

    settingsCard(title = anchorCardTitle) {
      row { shapes ->
        val (titleRes, subtitleRes) =
          when {
            !separate ->
              R.string.settings_position_anchor_title to R.string.settings_position_anchor_subtitle
            editsLandscape ->
              R.string.settings_position_anchor_landscape_title to
                R.string.settings_position_anchor_landscape_subtitle
            else ->
              R.string.settings_position_anchor_portrait_title to
                R.string.settings_position_anchor_portrait_subtitle
          }
        AnchorRow(
          shapes = shapes,
          title = stringResource(titleRes),
          selected = if (editsLandscape) position.landscapeAnchor else position.anchor,
          onSelected = { anchor: Anchor ->
            updatePosition {
              if (editsLandscape) it.copy(landscapeAnchor = anchor) else it.copy(anchor = anchor)
            }
          },
          subtitle = stringResource(subtitleRes),
        )
      }
      if (separate) {
        row { shapes ->
          AnchorRow(
            shapes = shapes,
            title =
              stringResource(
                if (editsLandscape) R.string.settings_position_anchor_portrait_title
                else R.string.settings_position_anchor_landscape_title
              ),
            selected = if (editsLandscape) position.anchor else position.landscapeAnchor,
            onSelected = { anchor: Anchor ->
              updatePosition {
                if (editsLandscape) it.copy(anchor = anchor) else it.copy(landscapeAnchor = anchor)
              }
            },
            subtitle =
              stringResource(
                if (editsLandscape) R.string.settings_position_anchor_portrait_subtitle
                else R.string.settings_position_anchor_landscape_subtitle
              ),
          )
        }
      }
      row { shapes ->
        // Edit mode acts on the attached window, so the row waits for a visible overlay and says
        // so while it waits.
        val running = monitor
        val onScreen = running is MonitorState.Running && running.visible
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.settings_position_adjust_title),
          onClick = onStartEditMode,
          subtitle =
            stringResource(
              if (onScreen) R.string.settings_position_adjust_subtitle
              else R.string.settings_position_adjust_unavailable_subtitle
            ),
          enabled = onScreen,
        )
      }
      if (showLandscapeMode) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_landscape_mode_title),
            options = LandscapeAnchorMode.entries,
            selected = position.landscapeAnchorMode,
            label = { landscapeAnchorModeLabel(resources, it) },
            onSelected = { mode -> updatePosition { it.copy(landscapeAnchorMode = mode) } },
            subtitle = stringResource(R.string.settings_position_landscape_mode_subtitle),
          )
        }
      }
      if (showAvoidCutout) {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_avoid_cutout_title),
            checked = position.avoidCutout,
            onCheckedChange = { avoid -> updatePosition { it.copy(avoidCutout = avoid) } },
            subtitle = stringResource(R.string.settings_position_avoid_cutout_subtitle),
          )
        }
      }
      if (advanced) {
        row { shapes ->
          SwitchRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_side_flip_title),
            checked = position.sideFlip && sideFlipServiceOn,
            onCheckedChange = { checked ->
              if (!checked) {
                sideFlipPending = false
                updatePosition { it.copy(sideFlip = false) }
              } else if (sideFlipServiceOn) {
                updatePosition { it.copy(sideFlip = true) }
              } else {
                sideFlipPending = true
                try {
                  context.startActivity(accessibilitySettingsIntent())
                } catch (e: ActivityNotFoundException) {
                  AppLog.w("No accessibility settings activity", e)
                  sideFlipPending = false
                }
              }
            },
            subtitle = stringResource(R.string.settings_position_side_flip_subtitle),
          )
        }
      }
    }

    if (showNudge) {
      settingsCard(title = nudgeCardTitle) {
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_offset_x_title),
            value = position.offsetXdp,
            range = SettingsBounds.OFFSET_DP,
            step = 2,
            format = { resources.getString(R.string.settings_unit_dp, it) },
            onValueChange = { value -> updatePosition { it.copy(offsetXdp = value) } },
          )
        }
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_offset_y_title),
            value = position.offsetYdp,
            range = SettingsBounds.OFFSET_DP,
            step = 2,
            format = { resources.getString(R.string.settings_unit_dp, it) },
            onValueChange = { value -> updatePosition { it.copy(offsetYdp = value) } },
          )
        }
      }
    }

    settingsCard(title = sizeCardTitle) {
      row { shapes ->
        if (advanced) {
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_text_size_title),
            value = position.textSizeSp,
            range = SettingsBounds.TEXT_SIZE_SP,
            format = { resources.getString(R.string.settings_unit_sp, it) },
            onValueChange = { value -> updatePosition { it.copy(textSizeSp = value) } },
          )
        } else {
          PresetRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_text_size_title),
            presets = textSizePresets,
            value = position.textSizeSp,
            valueLabel = { resources.getString(R.string.settings_unit_sp, it) },
            onSelected = { value -> updatePosition { it.copy(textSizeSp = value) } },
          )
        }
      }
      if (showChipPadding) {
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_chip_padding_title),
            value = position.chipPaddingDp,
            range = SettingsBounds.CHIP_PADDING_DP,
            format = { resources.getString(R.string.settings_unit_dp, it) },
            onValueChange = { value -> updatePosition { it.copy(chipPaddingDp = value) } },
          )
        }
      }
      if (showMinWidth) {
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_position_min_width_title),
            value = position.minWidthDp,
            range = SettingsBounds.MIN_WIDTH_DP,
            step = 8,
            format = {
              if (it == 0) resources.getString(R.string.settings_position_min_width_fit_content)
              else resources.getString(R.string.settings_unit_dp, it)
            },
            onValueChange = { value -> updatePosition { it.copy(minWidthDp = value) } },
          )
        }
      }
    }
  }
}

private fun landscapeAnchorModeLabel(resources: Resources, mode: LandscapeAnchorMode): String =
  resources.getString(
    when (mode) {
      LandscapeAnchorMode.SAME -> R.string.settings_position_landscape_mode_same
      LandscapeAnchorMode.SEPARATE -> R.string.settings_position_landscape_mode_separate
    }
  )

/** The regular-mode text sizes around the profile's own default. */
private const val TEXT_SIZE_SMALL_SP: Int = 12
private val TEXT_SIZE_DEFAULT_SP: Int = PositionSettings().textSizeSp
private const val TEXT_SIZE_LARGE_SP: Int = 18
