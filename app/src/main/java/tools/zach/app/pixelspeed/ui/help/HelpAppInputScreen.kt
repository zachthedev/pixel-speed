package tools.zach.app.pixelspeed.ui.help

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen

@Composable
fun HelpAppInputScreen(onBack: () -> Unit) {
  SettingsScreen(title = stringResource(R.string.help_input_screen_title), onBack = onBack) {
    helpSection(
      titleRes = R.string.help_card_what_is_happening,
      entries =
        listOf(
          HelpEntry(R.string.help_input_blocks_title, R.string.help_input_blocks_subtitle),
          HelpEntry(
            R.string.help_input_cannot_bypass_title,
            R.string.help_input_cannot_bypass_subtitle,
          ),
          HelpEntry(R.string.help_input_opacity_title, R.string.help_input_opacity_subtitle),
        ),
    )
    helpSection(
      titleRes = R.string.help_card_ways_to_pause,
      entries =
        listOf(
          HelpEntry(R.string.help_input_home_title, R.string.help_input_home_subtitle),
          HelpEntry(R.string.help_input_tile_title, R.string.help_input_tile_subtitle),
          HelpEntry(
            R.string.help_input_notification_title,
            R.string.help_input_notification_subtitle,
          ),
          HelpEntry(R.string.help_input_shortcut_title, R.string.help_input_shortcut_subtitle),
          HelpEntry(
            R.string.help_input_automation_title,
            R.string.help_input_automation_subtitle,
          ),
        ),
    )
  }
}
