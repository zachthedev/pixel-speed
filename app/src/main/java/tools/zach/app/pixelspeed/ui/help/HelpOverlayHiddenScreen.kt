package tools.zach.app.pixelspeed.ui.help

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.state.SuppressionReason
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen

@Composable
fun HelpOverlayHiddenScreen(onBack: () -> Unit) {
  SettingsScreen(
    title = stringResource(R.string.help_link_overlay_hidden_title),
    onBack = onBack,
  ) {
    helpSection(
      titleRes = R.string.help_card_rules_you_can_turn_off,
      entries = SuppressionReason.entries.map(::reasonEntry),
    )
    helpSection(
      titleRes = R.string.help_card_android_hides_it,
      entries =
        listOf(
          HelpEntry(R.string.help_android_system_title, R.string.help_android_system_subtitle),
          HelpEntry(R.string.help_android_secure_title, R.string.help_android_secure_subtitle),
        ),
    )
    helpSection(
      titleRes = R.string.help_card_app_off_or_paused,
      entries =
        listOf(
          HelpEntry(R.string.help_paused_title, R.string.help_paused_subtitle),
          HelpEntry(
            R.string.help_permission_removed_title,
            R.string.help_permission_removed_subtitle,
          ),
        ),
    )
  }
}

/** Each suppression reason's name and one-sentence explanation. */
private fun reasonEntry(reason: SuppressionReason): HelpEntry =
  when (reason) {
    SuppressionReason.SCREEN_OFF ->
      HelpEntry(R.string.help_reason_screen_off_title, R.string.help_reason_screen_off_subtitle)
    SuppressionReason.KEYGUARD ->
      HelpEntry(R.string.help_reason_keyguard_title, R.string.help_reason_keyguard_subtitle)
    SuppressionReason.OFFLINE ->
      HelpEntry(R.string.help_reason_offline_title, R.string.help_reason_offline_subtitle)
    SuppressionReason.IDLE ->
      HelpEntry(R.string.help_reason_idle_title, R.string.help_reason_idle_subtitle)
    SuppressionReason.FULLSCREEN ->
      HelpEntry(R.string.help_reason_fullscreen_title, R.string.help_reason_fullscreen_subtitle)
    SuppressionReason.LANDSCAPE ->
      HelpEntry(R.string.help_reason_landscape_title, R.string.help_reason_landscape_subtitle)
    SuppressionReason.BATTERY_SAVER ->
      HelpEntry(
        R.string.help_reason_battery_saver_title,
        R.string.help_reason_battery_saver_subtitle,
      )
  }
