package tools.zach.app.pixelspeed.ui.help

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen

/**
 * The platform's "displaying over other apps" notice, which returns after every unlock while an
 * overlay window exists, and the shade steps that turn its per-app category off for good.
 */
@Composable
fun HelpSystemNoticeScreen(onBack: () -> Unit) {
  SettingsScreen(
    title = stringResource(R.string.help_link_system_notice_title),
    onBack = onBack,
  ) {
    helpSection(
      titleRes = R.string.help_card_what_is_happening,
      entries =
        listOf(
          HelpEntry(R.string.help_notice_android_title, R.string.help_notice_android_subtitle),
          HelpEntry(R.string.help_notice_swipe_title, R.string.help_notice_swipe_subtitle),
        ),
    )
    helpSection(
      titleRes = R.string.help_card_silence_it,
      entries =
        listOf(
          HelpEntry(R.string.help_notice_step_1_title, R.string.help_notice_step_1_subtitle),
          HelpEntry(R.string.help_notice_step_2_title, R.string.help_notice_step_2_subtitle),
          HelpEntry(R.string.help_notice_step_3_title, R.string.help_notice_step_3_subtitle),
        ),
    )
  }
}
