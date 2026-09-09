package tools.zach.app.pixelspeed.ui.help

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import tools.zach.app.pixelspeed.HelpAppInput
import tools.zach.app.pixelspeed.HelpOverlayHidden
import tools.zach.app.pixelspeed.HelpReportProblem
import tools.zach.app.pixelspeed.HelpSystemNotice
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.ui.settings.LinkRow
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen
import tools.zach.app.pixelspeed.ui.settings.settingsCard

@Composable
fun HelpScreen(onBack: () -> Unit, onNavigate: (NavKey) -> Unit) {
  SettingsScreen(title = stringResource(R.string.settings_title_help), onBack = onBack) {
    settingsCard {
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.help_link_overlay_hidden_title),
          subtitle = stringResource(R.string.help_link_overlay_hidden_subtitle),
          onClick = { onNavigate(HelpOverlayHidden) },
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.help_link_app_input_title),
          subtitle = stringResource(R.string.help_link_app_input_subtitle),
          onClick = { onNavigate(HelpAppInput) },
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.help_link_system_notice_title),
          subtitle = stringResource(R.string.help_link_system_notice_subtitle),
          onClick = { onNavigate(HelpSystemNotice) },
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.help_link_report_problem_title),
          subtitle = stringResource(R.string.help_link_report_problem_subtitle),
          onClick = { onNavigate(HelpReportProblem) },
        )
      }
    }
  }
}
