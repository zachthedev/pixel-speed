package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.settings.PauseSettings
import tools.zach.app.pixelspeed.settings.SettingsBounds

@Composable
fun PauseScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val resources = LocalResources.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val durationsCardTitle =
    settings?.let {
      stringResource(R.string.settings_pause_card_durations_profile, it.activeProfile.name)
    } ?: stringResource(R.string.settings_pause_card_durations)
  val defaultsCardTitle = stringResource(R.string.settings_pause_card_defaults)
  SettingsScreen(
    title = stringResource(R.string.settings_title_pause),
    onBack = onBack,
  ) {
    val current = settings ?: return@SettingsScreen
    val pause = current.activeProfile.pause

    fun updatePause(transform: (PauseSettings) -> PauseSettings) {
      viewModel.updateProfile { it.copy(pause = transform(it.pause)) }
    }

    settingsCard(title = durationsCardTitle) {
      pause.pauseDurationsMinutes.forEachIndexed { index, minutes ->
        row { shapes ->
          ChoiceRow(
            shapes = shapes,
            title = stringResource(R.string.settings_pause_duration_slot_title, index + 1),
            options = PRESET_MINUTES + REMOVE_SENTINEL,
            selected = minutes,
            disabledOptions =
              pause.pauseDurationsMinutes.filter { it != minutes }.toSet() +
                if (pause.pauseDurationsMinutes.size == 1) setOf(REMOVE_SENTINEL) else emptySet(),
            label = { value -> presetOrRemoveLabel(resources, value) },
            onSelected = { value ->
              updatePause { current ->
                if (value == REMOVE_SENTINEL) {
                  if (current.pauseDurationsMinutes.size > 1) {
                    current.copy(
                      pauseDurationsMinutes =
                        current.pauseDurationsMinutes.filterIndexed { i, _ -> i != index }
                    )
                  } else {
                    current
                  }
                } else if (value in current.pauseDurationsMinutes) {
                  current
                } else {
                  current.copy(
                    pauseDurationsMinutes =
                      current.pauseDurationsMinutes.mapIndexed { i, existing ->
                        if (i == index) value else existing
                      }
                  )
                }
              }
            },
          )
        }
      }
      // Every slot holds a distinct preset, so the row only adds while a free preset exists.
      val nextPreset = PRESET_MINUTES.firstOrNull { it !in pause.pauseDurationsMinutes }
      if (
        pause.pauseDurationsMinutes.size < SettingsBounds.PAUSE_DURATION_COUNT.last &&
          nextPreset != null
      ) {
        row { shapes ->
          LinkRow(
            shapes = shapes,
            title = stringResource(R.string.settings_pause_add_title),
            onClick = {
              updatePause { it.copy(pauseDurationsMinutes = it.pauseDurationsMinutes + nextPreset) }
            },
            subtitle = stringResource(R.string.settings_pause_add_subtitle),
          )
        }
      }
    }

    settingsCard(title = defaultsCardTitle) {
      row { shapes ->
        ChoiceRow(
          shapes = shapes,
          title = stringResource(R.string.settings_pause_tile_default_title),
          options = NULLABLE_PRESET_MINUTES,
          selected = pause.tilePauseDurationMinutes,
          label = { value -> nullablePresetLabel(resources, value) },
          onSelected = { value -> updatePause { it.copy(tilePauseDurationMinutes = value) } },
          description = stringResource(R.string.settings_pause_tile_default_subtitle),
        )
      }
      row { shapes ->
        ChoiceRow(
          shapes = shapes,
          title = stringResource(R.string.settings_pause_notification_default_title),
          options = NULLABLE_PRESET_MINUTES,
          selected = pause.notificationPauseDurationMinutes,
          label = { value -> nullablePresetLabel(resources, value) },
          onSelected = { value ->
            updatePause { it.copy(notificationPauseDurationMinutes = value) }
          },
          description = stringResource(R.string.settings_pause_notification_default_subtitle),
        )
      }
    }
  }
}

private fun pauseDurationLabel(resources: Resources, minutes: Int): String {
  val resource = PauseDurationLabels.resourceFor(minutes)
  val pluralsRes =
    when (resource.unit) {
      PauseDurationLabels.PauseUnit.MINUTES -> R.plurals.settings_pause_duration_minutes
      PauseDurationLabels.PauseUnit.HOURS -> R.plurals.settings_pause_duration_hours
      PauseDurationLabels.PauseUnit.DAYS -> R.plurals.settings_pause_duration_days
    }
  return resources.getQuantityString(pluralsRes, resource.quantity, resource.quantity)
}

private fun presetOrRemoveLabel(resources: Resources, minutes: Int): String =
  if (minutes == REMOVE_SENTINEL) resources.getString(R.string.settings_pause_remove)
  else pauseDurationLabel(resources, minutes)

private fun nullablePresetLabel(resources: Resources, minutes: Int?): String =
  if (minutes == null) resources.getString(R.string.settings_pause_until_resumed)
  else pauseDurationLabel(resources, minutes)

private val PRESET_MINUTES: List<Int> =
  listOf(1, 2, 5, 10, 15, 30, 60, 120, 240, 480, SettingsBounds.PAUSE_MINUTES.last)
private val NULLABLE_PRESET_MINUTES: List<Int?> = listOf(null) + PRESET_MINUTES
private const val REMOVE_SENTINEL: Int = -1
