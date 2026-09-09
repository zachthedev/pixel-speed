package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.sampling.Smoothing
import tools.zach.app.pixelspeed.settings.BehaviorSettings
import tools.zach.app.pixelspeed.settings.OfflineMode
import tools.zach.app.pixelspeed.settings.SettingsBounds
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.SuppressionReason

@Composable
fun BehaviorScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val resources = LocalResources.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val monitor by viewModel.monitor.collectAsStateWithLifecycle()
  val samplingCardTitle =
    settings?.let {
      stringResource(R.string.settings_behavior_card_sampling_profile, it.activeProfile.name)
    } ?: stringResource(R.string.settings_behavior_card_sampling)
  val hideWhenCardTitle = stringResource(R.string.settings_behavior_card_hide_when)
  val advanced = settings?.advancedMode ?: false
  val behavior = settings?.activeProfile?.behavior ?: BehaviorSettings()
  val defaults = BehaviorSettings()
  // An idle rule tuned away from its defaults and an offline rule that hides the overlay stay
  // reachable in regular mode while they are in effect.
  val profileId = settings?.activeProfileId
  val showIdleTuning =
    rememberRevealed(
      advanced,
      behavior.idleThresholdBytesPerSecond != defaults.idleThresholdBytesPerSecond ||
        behavior.idleDelayMs != defaults.idleDelayMs,
      profileId,
    )
  val showOfflineMode =
    rememberRevealed(advanced, behavior.offlineMode == OfflineMode.HIDE, profileId)
  SettingsScreen(
    title = stringResource(R.string.settings_title_behavior),
    onBack = onBack,
  ) {
    if (settings == null) return@SettingsScreen

    fun updateBehavior(transform: (BehaviorSettings) -> BehaviorSettings) {
      viewModel.updateProfile { it.copy(behavior = transform(it.behavior)) }
    }

    val intervalRange =
      SettingsBounds.updateIntervalFloorMs(Build.VERSION.SDK_INT)..SettingsBounds.UPDATE_INTERVAL_MS
          .last
    val intervalPresets: List<Preset<Int>> =
      listOf(
        Preset(resources.getString(R.string.settings_unit_seconds, 1), 1000),
        Preset(resources.getString(R.string.settings_unit_seconds, 2), 2000),
        Preset(resources.getString(R.string.settings_unit_seconds, 5), 5000),
      )

    settingsCard(title = samplingCardTitle) {
      row { shapes ->
        if (advanced) {
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_behavior_update_interval_title),
            value = behavior.updateIntervalMs,
            range = intervalRange,
            step = 500,
            format = { resources.getString(R.string.settings_unit_seconds_decimal, it / 1000.0) },
            onValueChange = { value -> updateBehavior { it.copy(updateIntervalMs = value) } },
          )
        } else {
          PresetRow(
            shapes = shapes,
            title = stringResource(R.string.settings_behavior_update_interval_title),
            presets = intervalPresets,
            value = behavior.updateIntervalMs,
            valueLabel = {
              resources.getString(R.string.settings_unit_seconds_decimal, it / 1000.0)
            },
            onSelected = { value -> updateBehavior { it.copy(updateIntervalMs = value) } },
          )
        }
      }
      if (advanced) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_behavior_smoothing_title),
            options = Smoothing.entries,
            selected = behavior.smoothing,
            label = { smoothingLabel(resources, it) },
            onSelected = { smoothing -> updateBehavior { it.copy(smoothing = smoothing) } },
            subtitle = stringResource(R.string.settings_behavior_smoothing_subtitle),
          )
        }
      }
    }

    settingsCard(title = hideWhenCardTitle) {
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_behavior_hide_idle_title),
          checked = behavior.hideWhenIdle,
          onCheckedChange = { hide -> updateBehavior { it.copy(hideWhenIdle = hide) } },
          subtitle =
            hideSubtitle(
              R.string.settings_behavior_hide_idle_subtitle,
              monitor,
              SuppressionReason.IDLE,
            ),
        )
      }
      if (showIdleTuning) {
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_behavior_idle_threshold_title),
            value = (behavior.idleThresholdBytesPerSecond / BYTES_PER_KB).toInt(),
            range = SettingsBounds.IDLE_THRESHOLD_KBPS,
            format = { resources.getString(R.string.settings_unit_kbps, it) },
            onValueChange = { value ->
              updateBehavior {
                it.copy(idleThresholdBytesPerSecond = value.toLong() * BYTES_PER_KB)
              }
            },
            enabled = behavior.hideWhenIdle,
          )
        }
        row { shapes ->
          SliderRow(
            shapes = shapes,
            title = stringResource(R.string.settings_behavior_idle_delay_title),
            value = behavior.idleDelayMs / MILLIS_PER_SECOND,
            range = SettingsBounds.IDLE_DELAY_SECONDS,
            format = { resources.getString(R.string.settings_unit_seconds, it) },
            onValueChange = { value ->
              updateBehavior { it.copy(idleDelayMs = value * MILLIS_PER_SECOND) }
            },
            enabled = behavior.hideWhenIdle,
          )
        }
      }
      if (showOfflineMode) {
        row { shapes ->
          SegmentedRow(
            shapes = shapes,
            title = stringResource(R.string.settings_behavior_offline_mode_title),
            options = OfflineMode.entries,
            selected = behavior.offlineMode,
            label = { offlineModeLabel(resources, it) },
            onSelected = { mode -> updateBehavior { it.copy(offlineMode = mode) } },
            subtitle =
              hideSubtitle(
                R.string.settings_behavior_offline_mode_subtitle,
                monitor,
                SuppressionReason.OFFLINE,
              ),
          )
        }
      }
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_behavior_hide_fullscreen_title),
          checked = behavior.hideInFullscreen,
          onCheckedChange = { hide -> updateBehavior { it.copy(hideInFullscreen = hide) } },
          subtitle =
            hideSubtitle(
              R.string.settings_behavior_hide_fullscreen_subtitle,
              monitor,
              SuppressionReason.FULLSCREEN,
            ),
        )
      }
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_behavior_hide_landscape_title),
          checked = behavior.hideInLandscape,
          onCheckedChange = { hide -> updateBehavior { it.copy(hideInLandscape = hide) } },
          subtitle =
            hideSubtitle(
              R.string.settings_behavior_hide_landscape_subtitle,
              monitor,
              SuppressionReason.LANDSCAPE,
            ),
        )
      }
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_behavior_pause_battery_saver_title),
          checked = behavior.pauseOnBatterySaver,
          onCheckedChange = { pause -> updateBehavior { it.copy(pauseOnBatterySaver = pause) } },
          subtitle =
            hideSubtitle(
              R.string.settings_behavior_pause_battery_saver_subtitle,
              monitor,
              SuppressionReason.BATTERY_SAVER,
            ),
        )
      }
    }
  }
}

@Composable
private fun hideSubtitle(baseRes: Int, monitor: MonitorState, reason: SuppressionReason): String {
  val base = stringResource(baseRes)
  val active = monitor is MonitorState.Running && reason in monitor.suppressions
  return if (active) "$base\n${stringResource(R.string.settings_behavior_active_now)}" else base
}

private fun smoothingLabel(resources: Resources, smoothing: Smoothing): String =
  resources.getString(
    when (smoothing) {
      Smoothing.NONE -> R.string.settings_behavior_smoothing_none
      Smoothing.LIGHT -> R.string.settings_behavior_smoothing_light
      Smoothing.HEAVY -> R.string.settings_behavior_smoothing_heavy
    }
  )

private fun offlineModeLabel(resources: Resources, mode: OfflineMode): String =
  resources.getString(
    when (mode) {
      OfflineMode.DASHES -> R.string.settings_behavior_offline_mode_dashes
      OfflineMode.HIDE -> R.string.settings_behavior_offline_mode_hide
    }
  )

private const val BYTES_PER_KB: Long = SettingsBounds.BYTES_PER_KB
private const val MILLIS_PER_SECOND: Int = SettingsBounds.MILLIS_PER_SECOND
