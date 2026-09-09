package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.settings.NotificationSettings
import tools.zach.app.pixelspeed.settings.ReadoutSurface

@Composable
fun NotificationScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val resources = LocalResources.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val readoutSurfaceCardTitle =
    settings?.let {
      stringResource(
        R.string.settings_notification_card_readout_surface_profile,
        it.activeProfile.name,
      )
    } ?: stringResource(R.string.settings_notification_card_readout_surface)
  SettingsScreen(title = stringResource(R.string.settings_title_notification), onBack = onBack) {
    val current = settings ?: return@SettingsScreen
    val notification = current.activeProfile.notification

    fun updateNotification(transform: (NotificationSettings) -> NotificationSettings) {
      viewModel.updateProfile { it.copy(notification = transform(it.notification)) }
    }

    settingsCard(title = readoutSurfaceCardTitle) {
      row { shapes ->
        SegmentedRow(
          shapes = shapes,
          title = stringResource(R.string.settings_notification_surface_title),
          options = ReadoutSurface.entries,
          selected = notification.surface,
          label = { readoutSurfaceLabel(resources, it) },
          onSelected = { surface -> updateNotification { it.copy(surface = surface) } },
          subtitle = stringResource(R.string.settings_notification_surface_subtitle),
        )
      }
      row { shapes ->
        SwitchRow(
          shapes = shapes,
          title = stringResource(R.string.settings_notification_shows_speed_title),
          checked = notification.notificationShowsSpeed,
          onCheckedChange = { shows ->
            updateNotification { it.copy(notificationShowsSpeed = shows) }
          },
          subtitle = stringResource(R.string.settings_notification_shows_speed_subtitle),
        )
      }
    }
  }
}

private fun readoutSurfaceLabel(resources: Resources, surface: ReadoutSurface): String =
  resources.getString(
    when (surface) {
      ReadoutSurface.OVERLAY -> R.string.settings_notification_surface_overlay
      ReadoutSurface.LIVE_UPDATE -> R.string.settings_notification_surface_live_update
      ReadoutSurface.BOTH -> R.string.settings_notification_surface_both
    }
  )
