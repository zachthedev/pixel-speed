package tools.zach.app.pixelspeed.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.sampling.CounterSourceKind
import tools.zach.app.pixelspeed.service.SpeedTileRequest
import tools.zach.app.pixelspeed.settings.Settings

/**
 * What describes this install rather than a configuration: the switch and its pause, the onboarding
 * record, and the dismissed notice. Both import and reset keep these. [keepOnImport] also keeps the
 * two switches that open surfaces to other apps, since a file the user picked must not flip them;
 * reset returns those to their defaults.
 */
private fun Settings.keepOnReset(current: Settings): Settings =
  copy(
    enabled = current.enabled,
    paused = current.paused,
    pausedUntilEpochMillis = current.pausedUntilEpochMillis,
    onboardingCompleted = current.onboardingCompleted,
    onboardingStep = current.onboardingStep,
    autoRevokeNoticeDismissed = current.autoRevokeNoticeDismissed,
  )

private fun Settings.keepOnImport(current: Settings): Settings =
  keepOnReset(current)
    .copy(startOnBoot = current.startOnBoot, automationIntents = current.automationIntents)

@Composable
fun SystemScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings: Settings? by viewModel.settings.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val limit = obscuringLimit()
  val opacityWarning =
    stringResource(
      R.string.settings_appearance_window_opacity_warning,
      obscuringLimitPercent(limit),
    )
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var resetRequested by remember { mutableStateOf(false) }

  val showMessage: (String) -> Unit = { message ->
    scope.launch { snackbarHostState.showSnackbar(message) }
  }
  val exportLauncher =
    rememberSettingsExportLauncher(
      settingsProvider = { settings ?: Settings() },
      onFailure = showMessage,
    )
  val importLauncher =
    rememberSettingsImportLauncher(
      onImported = { imported ->
        viewModel.replaceSettings { current -> imported.keepOnImport(current) }
        // A file sets the opacity with no slider in front of the user, so the consequence the
        // slider would show is said here instead.
        if (imported.profiles.any { it.appearance.windowOpacity > limit }) {
          showMessage(opacityWarning)
        }
      },
      onFailure = showMessage,
    )

  val advanced = settings?.advancedMode ?: false
  // A source other than the physical interfaces can read zero on the current network, and an
  // exported entry point that is open must always be closable, so each stays reachable in
  // regular mode while it is in effect.
  val showCounters =
    rememberRevealed(
      advanced,
      settings?.activeProfile?.counterSource?.let { it != CounterSourceKind.PHYSICAL_INTERFACES }
        ?: false,
      settings?.activeProfileId,
    )
  val showAutomation = rememberRevealed(advanced, settings?.automationIntents ?: false)
  val countersTitle =
    settings?.let {
      stringResource(R.string.settings_system_title_counters_profile, it.activeProfile.name)
    } ?: stringResource(R.string.settings_system_title_counters)
  val advancedModeTitle = stringResource(R.string.settings_system_advanced_mode)
  val advancedModeSubtitle = stringResource(R.string.settings_system_advanced_mode_subtitle)
  val automationTitle = stringResource(R.string.settings_system_title_automation)
  val startupTitle = stringResource(R.string.settings_system_title_startup)
  val backupTitle = stringResource(R.string.settings_system_title_backup)
  val resetSectionTitle = stringResource(R.string.settings_system_title_reset)
  val counterSourceTitle = stringResource(R.string.settings_system_counter_source)
  val counterSourceLabels =
    CounterSourceKind.entries.associateWith { kind -> counterSourceLabel(kind) }
  val automationIntentsTitle = stringResource(R.string.settings_system_automation_intents)
  val automationIntentsSubtitle =
    stringResource(R.string.settings_system_automation_intents_subtitle)
  val startOnBootTitle = stringResource(R.string.settings_system_start_on_boot)
  val startOnBootSubtitle = stringResource(R.string.settings_system_start_on_boot_subtitle)
  val addTileTitle = stringResource(R.string.settings_notification_add_tile_title)
  val addTileSubtitle =
    stringResource(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        R.string.settings_notification_add_tile_subtitle
      } else {
        R.string.settings_notification_add_tile_manual_subtitle
      }
    )
  val tileAdded = stringResource(R.string.settings_system_tile_added)
  val tileNotAdded = stringResource(R.string.settings_system_tile_not_added)
  val exportTitle = stringResource(R.string.settings_system_export)
  val exportSubtitle = stringResource(R.string.settings_system_export_subtitle)
  val importTitle = stringResource(R.string.settings_system_import)
  val importSubtitle = stringResource(R.string.settings_system_import_subtitle)
  val resetTitle = stringResource(R.string.settings_system_reset)
  val resetSubtitle = stringResource(R.string.settings_system_reset_subtitle)

  Box(modifier = Modifier.fillMaxSize()) {
    SettingsScreen(title = stringResource(R.string.settings_title_system), onBack = onBack) {
      val current = settings
      if (current != null) {
        settingsCard {
          row { shapes ->
            SwitchRow(
              shapes = shapes,
              title = advancedModeTitle,
              checked = current.advancedMode,
              onCheckedChange = { checked ->
                viewModel.updateSettings { it.copy(advancedMode = checked) }
              },
              subtitle = advancedModeSubtitle,
            )
          }
        }
        if (showCounters) {
          settingsCard(title = countersTitle) {
            row { shapes ->
              ChoiceRow(
                shapes = shapes,
                title = counterSourceTitle,
                options = CounterSourceKind.entries,
                selected = current.activeProfile.counterSource,
                label = { counterSourceLabels.getValue(it) },
                onSelected = { kind -> viewModel.updateProfile { it.copy(counterSource = kind) } },
              )
            }
          }
        }
        if (showAutomation) {
          settingsCard(title = automationTitle) {
            row { shapes ->
              SwitchRow(
                shapes = shapes,
                title = automationIntentsTitle,
                checked = current.automationIntents,
                onCheckedChange = { checked ->
                  viewModel.updateSettings { it.copy(automationIntents = checked) }
                },
                subtitle = automationIntentsSubtitle,
              )
            }
          }
        }
        settingsCard(title = startupTitle) {
          row { shapes ->
            SwitchRow(
              shapes = shapes,
              title = startOnBootTitle,
              checked = current.startOnBoot,
              onCheckedChange = { checked ->
                viewModel.updateSettings { it.copy(startOnBoot = checked) }
              },
              subtitle = startOnBootSubtitle,
            )
          }
          row { shapes ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              LinkRow(
                shapes = shapes,
                title = addTileTitle,
                subtitle = addTileSubtitle,
                onClick = {
                  val prompted =
                    SpeedTileRequest.request(context) { added ->
                      showMessage(if (added) tileAdded else tileNotAdded)
                    }
                  if (!prompted) showMessage(tileNotAdded)
                },
              )
            } else {
              // No prompt exists before API 33, so the row only carries the manual steps.
              InfoRow(shapes = shapes, title = addTileTitle, subtitle = addTileSubtitle)
            }
          }
        }
        settingsCard(title = backupTitle) {
          row { shapes ->
            LinkRow(
              shapes = shapes,
              title = exportTitle,
              subtitle = exportSubtitle,
              onClick = exportLauncher,
            )
          }
          row { shapes ->
            LinkRow(
              shapes = shapes,
              title = importTitle,
              subtitle = importSubtitle,
              onClick = importLauncher,
            )
          }
        }
        settingsCard(title = resetSectionTitle) {
          row { shapes ->
            LinkRow(
              shapes = shapes,
              title = resetTitle,
              subtitle = resetSubtitle,
              onClick = { resetRequested = true },
            )
          }
        }
      }
    }
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
  }

  if (resetRequested) {
    AlertDialog(
      onDismissRequest = { resetRequested = false },
      title = { Text(stringResource(R.string.settings_system_reset_title)) },
      text = { Text(stringResource(R.string.settings_system_reset_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.replaceSettings { Settings().keepOnReset(it) }
            resetRequested = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.settings_shared_action_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { resetRequested = false }) {
          Text(stringResource(R.string.settings_shared_action_cancel))
        }
      },
    )
  }
}

/** The counter source label shown in its own row and reused by the Home summary line. */
@Composable
internal fun counterSourceLabel(kind: CounterSourceKind): String =
  stringResource(
    when (kind) {
      CounterSourceKind.PHYSICAL_INTERFACES ->
        R.string.settings_system_counter_source_physical_interfaces
      CounterSourceKind.DEFAULT_NETWORK -> R.string.settings_system_counter_source_default_network
      CounterSourceKind.ALL_INTERFACES -> R.string.settings_system_counter_source_all_interfaces
      CounterSourceKind.MOBILE_ONLY -> R.string.settings_system_counter_source_mobile_only
      CounterSourceKind.NON_MOBILE_ONLY -> R.string.settings_system_counter_source_non_mobile_only
    }
  )
