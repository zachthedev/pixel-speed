package tools.zach.app.pixelspeed.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import java.time.ZoneId
import tools.zach.app.pixelspeed.About
import tools.zach.app.pixelspeed.AppearanceSettings
import tools.zach.app.pixelspeed.BehaviorSettings
import tools.zach.app.pixelspeed.ContentSettings
import tools.zach.app.pixelspeed.Help
import tools.zach.app.pixelspeed.NotificationSettings
import tools.zach.app.pixelspeed.PauseSettings
import tools.zach.app.pixelspeed.PositionSettings
import tools.zach.app.pixelspeed.ProfilesSettings
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.SystemSettings
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.StatusBarFit
import tools.zach.app.pixelspeed.service.DeadlineFormatter
import tools.zach.app.pixelspeed.settings.ReadoutSurface
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsDefaults
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.SuppressionReason
import tools.zach.app.pixelspeed.ui.rememberHour24
import tools.zach.app.pixelspeed.ui.settings.ChoiceRow
import tools.zach.app.pixelspeed.ui.settings.ConnectedChoice
import tools.zach.app.pixelspeed.ui.settings.LinkRow
import tools.zach.app.pixelspeed.ui.settings.PauseDurationLabels
import tools.zach.app.pixelspeed.ui.settings.SettingsCardTitle
import tools.zach.app.pixelspeed.ui.settings.connectedLabelsFit
import tools.zach.app.pixelspeed.ui.settings.connectedOptionLimit
import tools.zach.app.pixelspeed.ui.settings.contentWidthCap
import tools.zach.app.pixelspeed.ui.settings.rememberRevealed
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes
import tools.zach.app.pixelspeed.ui.settings.settingsTopBarColors

@Composable
fun HomeScreen(
  initialSettings: Settings,
  onNavigate: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  LifecycleResumeEffect(Unit) {
    viewModel.onResumed()
    onPauseOrDispose {}
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.permissionGrants.collect { granted -> viewModel.onPermissionChanged(granted) }
    }
  }
  // The notification is optional (the service runs without it), so the answer is not awaited.
  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val onEnabledChange: (Boolean) -> Unit = { enabled ->
    if (enabled && !hasNotificationPermission(context)) {
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    viewModel.setEnabled(enabled)
  }
  HomeContent(
    state = uiState ?: viewModel.initialState(initialSettings),
    onEnabledChange = onEnabledChange,
    onDismissAutoRevoke = viewModel::dismissAutoRevokeNotice,
    onPause = viewModel::pause,
    onResume = viewModel::resume,
    onSelectProfile = viewModel::selectProfile,
    onNavigate = onNavigate,
    modifier = modifier,
  )
}

@Composable
fun HomeContent(
  state: HomeUiState,
  onEnabledChange: (Boolean) -> Unit,
  onDismissAutoRevoke: () -> Unit,
  onPause: (Int?) -> Unit,
  onResume: () -> Unit,
  onSelectProfile: (String) -> Unit,
  onNavigate: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  Scaffold(
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = settingsTopBarColors(),
        scrollBehavior = scrollBehavior,
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(innerPadding)
          .fillMaxWidth()
          .wrapContentWidth()
          .contentWidthCap()
          .padding(horizontal = 16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      StateCard(
        monitor = state.monitor,
        enabled = state.settings.enabled,
        hasPermission = state.hasOverlayPermission,
        // The hint offers the Slim fit, so it has nothing to say once Slim is the fit in use.
        overruns =
          !state.overlayFits &&
            state.settings.activeProfile.appearance.statusBarFit != StatusBarFit.SLIM,
        onEnabledChange = onEnabledChange,
      )
      if (!state.autoRevokeWhitelisted && !state.settings.autoRevokeNoticeDismissed) {
        AutoRevokeCard(onDismiss = onDismissAutoRevoke)
      }
      if (state.settings.enabled && state.hasOverlayPermission) {
        PauseCard(
          monitor = state.monitor,
          durations = state.settings.activeProfile.pause.pauseDurationsMinutes,
          onPause = onPause,
          onResume = onResume,
        )
      }
      ProfileSwitcher(
        settings = state.settings,
        onSelect = onSelectProfile,
        onManage = { onNavigate(ProfilesSettings) },
      )
      SettingsLinks(state.settings, onNavigate)
    }
  }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
  Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

@Composable
private fun StateCard(
  monitor: MonitorState,
  enabled: Boolean,
  hasPermission: Boolean,
  overruns: Boolean,
  onEnabledChange: (Boolean) -> Unit,
) {
  val context = LocalContext.current
  val needsPermission = !hasPermission && (enabled || monitor is MonitorState.NeedsPermission)
  val containerColor =
    when {
      needsPermission -> MaterialTheme.colorScheme.errorContainer
      monitor is MonitorState.Running && monitor.visible ->
        MaterialTheme.colorScheme.primaryContainer
      monitor is MonitorState.Running || monitor is MonitorState.Paused ->
        MaterialTheme.colorScheme.tertiaryContainer
      else -> MaterialTheme.colorScheme.surfaceBright
    }
  Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // The main-switch row of a Pixel Settings page: the whole row is the toggle, so its title
      // and state line together are the switch's accessible name.
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.home_state_title),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = stateLabel(monitor, needsPermission),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Switch(
          checked = enabled,
          onCheckedChange = null,
          thumbContent =
            if (enabled) {
              {
                Icon(
                  painter = painterResource(R.drawable.ic_check),
                  contentDescription = null,
                  modifier = Modifier.size(SwitchDefaults.IconSize),
                )
              }
            } else {
              null
            },
        )
      }
      if (
        monitor is MonitorState.Running && !monitor.visible && monitor.suppressions.isNotEmpty()
      ) {
        Text(
          text = suppressionLine(monitor.suppressions),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      if (monitor is MonitorState.Running && monitor.visible && overruns) {
        Text(
          text = stringResource(R.string.home_fit_hint),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      if (needsPermission) {
        Text(
          text = stringResource(R.string.home_permission_hint),
          style = MaterialTheme.typography.bodyMedium,
        )
        Button(
          onClick = {
            openOwnSettingsPage(context, AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION)
          }
        ) {
          Text(stringResource(R.string.home_action_grant_permission))
        }
      }
    }
  }
}

/**
 * Android strips permissions from apps unused for months, and a foreground service alone does not
 * count as use. The card leads to the settings page of the app, where the user turns that off.
 */
@Composable
private fun AutoRevokeCard(onDismiss: () -> Unit) {
  val context = LocalContext.current
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = stringResource(R.string.home_auto_revoke_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = stringResource(R.string.home_auto_revoke_body),
        style = MaterialTheme.typography.bodyMedium,
      )
      // Two buttons that wrap onto a second line when a font scale leaves no room for both.
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = { openOwnSettingsPage(context, Intent.ACTION_AUTO_REVOKE_PERMISSIONS) }
        ) {
          Text(stringResource(R.string.home_action_app_settings))
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_action_dismiss)) }
      }
    }
  }
}

/**
 * Opens the system page for [action] on this package. Some builds ship no page for it, and the app
 * details page still reaches the same toggles, so that is the fallback.
 */
private fun openOwnSettingsPage(context: android.content.Context, action: String) {
  val packageUri = "package:${context.packageName}".toUri()
  try {
    context.startActivity(Intent(action, packageUri))
  } catch (e: ActivityNotFoundException) {
    AppLog.w("No activity for $action, opening app details", e)
    try {
      context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    } catch (details: ActivityNotFoundException) {
      AppLog.e("No app details page either", details)
    }
  }
}

/** The active reasons, named, so the user never wonders why the overlay is gone. */
@Composable
private fun suppressionLine(reasons: Set<SuppressionReason>): String {
  val names = reasons.map { reason ->
    stringResource(
      when (reason) {
        SuppressionReason.SCREEN_OFF -> R.string.notification_reason_screen_off
        SuppressionReason.KEYGUARD -> R.string.notification_reason_keyguard
        SuppressionReason.OFFLINE -> R.string.notification_reason_offline
        SuppressionReason.IDLE -> R.string.notification_reason_idle
        SuppressionReason.FULLSCREEN -> R.string.notification_reason_fullscreen
        SuppressionReason.LANDSCAPE -> R.string.notification_reason_landscape
        SuppressionReason.BATTERY_SAVER -> R.string.notification_reason_battery_saver
      }
    )
  }
  return stringResource(R.string.home_hidden_reasons, names.joinToString(", "))
}

/** Duration chooser plus the pause button, or the deadline plus resume while paused. */
@Composable
private fun PauseCard(
  monitor: MonitorState,
  durations: List<Int>,
  onPause: (Int?) -> Unit,
  onResume: () -> Unit,
) {
  val haptics = LocalHapticFeedback.current
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(stringResource(R.string.home_pause_title), style = MaterialTheme.typography.titleMedium)
      if (monitor is MonitorState.Paused) {
        val until = monitor.until
        val hour24: Boolean = rememberHour24()
        // Pausing replaces the card body, so the new state is announced rather than left to focus.
        Text(
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          text =
            if (until == null) stringResource(R.string.home_state_paused)
            else
              stringResource(
                R.string.tile_speed_paused_until,
                DeadlineFormatter.format(
                  until,
                  System.currentTimeMillis(),
                  LocalConfiguration.current.locales[0],
                  ZoneId.systemDefault(),
                  hour24,
                ),
              ),
          style = MaterialTheme.typography.bodyLarge,
        )
        CardAction(
          label = stringResource(R.string.notification_action_resume),
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onResume()
          },
        )
      } else {
        // Value 0 is the "until I resume" choice, appended after the configured durations.
        val options: List<Int> = durations.distinct() + listOf(0)
        val labels = options.associateWith { minutes ->
          if (minutes == 0) stringResource(R.string.home_pause_until_resumed)
          else durationLabel(minutes)
        }
        // Nothing is chosen until the user chooses, so a highlighted option never reads as the
        // overlay's state; a restored value from another profile's durations counts as nothing.
        var chosen: Int? by rememberSaveable(options) { mutableStateOf(null) }
        val selected: Int? = chosen?.takeIf { it in options }
        val title = stringResource(R.string.home_pause_duration)
        val unchosen = stringResource(R.string.home_pause_choose)
        BoxWithConstraints {
          val connected =
            options.size <= connectedOptionLimit(maxWidth) &&
              connectedLabelsFit(options.map { labels.getValue(it) }, maxWidth)
          if (!connected) {
            ChoiceRow(
              shapes = settingsRowShapes(index = 0, count = 1),
              title = title,
              options = options,
              selected = selected,
              label = { minutes -> minutes?.let(labels::getValue) ?: unchosen },
              onSelected = { chosen = it },
            )
          } else {
            ConnectedChoice(
              title = title,
              options = options,
              selected = selected,
              label = { minutes -> minutes?.let(labels::getValue) ?: unchosen },
              onSelected = { chosen = it },
            )
          }
        }
        CardAction(
          label = stringResource(R.string.notification_action_pause),
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onPause(selected?.takeIf { it > 0 })
          },
          enabled = monitor is MonitorState.Running && selected != null,
        )
      }
    }
  }
}

/** A card's one action: a filled button at the end of its own line, where Pixel cards put it. */
@Composable
private fun CardAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    Button(onClick = onClick, enabled = enabled) { Text(label) }
  }
}

@Composable
private fun durationLabel(minutes: Int): String {
  val resource = PauseDurationLabels.resourceFor(minutes)
  val pluralsRes =
    when (resource.unit) {
      PauseDurationLabels.PauseUnit.MINUTES -> R.plurals.home_duration_minutes
      PauseDurationLabels.PauseUnit.HOURS -> R.plurals.home_duration_hours
      PauseDurationLabels.PauseUnit.DAYS -> R.plurals.home_duration_days
    }
  return pluralStringResource(pluralsRes, resource.quantity, resource.quantity)
}

/**
 * The switcher exists once there is something to switch between; the Profiles row opens the rest.
 */
@Composable
private fun ProfileSwitcher(settings: Settings, onSelect: (String) -> Unit, onManage: () -> Unit) {
  if (settings.profiles.size < 2) return
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SettingsCardTitle(stringResource(R.string.settings_title_profiles))
      TextButton(onClick = onManage) { Text(stringResource(R.string.home_profiles_manage)) }
    }
    val ids = settings.profiles.map { it.id }
    val nameOf: (String) -> String = { id -> settings.profiles.first { it.id == id }.name }
    BoxWithConstraints {
      when {
        ids.size > connectedOptionLimit(maxWidth) ->
          ChoiceRow(
            shapes = settingsRowShapes(index = 0, count = 1),
            title = stringResource(R.string.home_profile_active),
            options = ids,
            selected = settings.activeProfile.id,
            label = nameOf,
            onSelected = onSelect,
          )
        else ->
          ConnectedChoice(
            title = stringResource(R.string.home_profile_active),
            options = ids,
            selected = settings.activeProfile.id,
            label = nameOf,
            onSelected = onSelect,
          )
      }
    }
  }
}

/**
 * The settings screens as one segmented card, and Help plus About as a second. Profiles and
 * Notification and tile are advanced-mode rows, and each also shows whenever what it manages is in
 * effect: a second profile needs somewhere to be managed, and a readout surface other than the
 * overlay needs somewhere to be changed back. Pause is an advanced-mode row, since the pause card
 * above the list already offers the durations, and it shows while the duration list or a tile or
 * notification default is off its factory value, since that screen is the only place to change them
 * back. The same rule runs through every screen: a row hidden in regular mode still shows while its
 * value can hide, move, or size the readout away from the default, or opens the app to other apps,
 * so nothing set in advanced mode is out of reach. Purely cosmetic rows (corner shape, blur, fonts,
 * format details) wait for advanced mode.
 */
@Composable
private fun SettingsLinks(settings: Settings, onNavigate: (NavKey) -> Unit) {
  val advanced = settings.advancedMode
  val showProfiles = rememberRevealed(advanced, settings.profiles.size > 1)
  val showNotification =
    rememberRevealed(
      advanced,
      settings.activeProfile.notification.surface != ReadoutSurface.OVERLAY,
      settings.activeProfileId,
    )
  val pause = settings.activeProfile.pause
  val defaults = SettingsDefaults.defaultProfile.pause
  val showPause =
    rememberRevealed(
      advanced,
      pause.pauseDurationsMinutes != defaults.pauseDurationsMinutes ||
        pause.tilePauseDurationMinutes != defaults.tilePauseDurationMinutes ||
        pause.notificationPauseDurationMinutes != defaults.notificationPauseDurationMinutes,
      settings.activeProfileId,
    )
  val settingsLinks: List<SettingsLink> = buildList {
    add(SettingsLink(R.string.settings_title_content, R.drawable.ic_notes, ContentSettings))
    add(SettingsLink(R.string.settings_title_position, R.drawable.ic_open_with, PositionSettings))
    add(SettingsLink(R.string.settings_title_appearance, R.drawable.ic_palette, AppearanceSettings))
    add(SettingsLink(R.string.settings_title_behavior, R.drawable.ic_visibility, BehaviorSettings))
    if (showPause) {
      add(SettingsLink(R.string.settings_title_pause, R.drawable.ic_pause_circle, PauseSettings))
    }
    if (showProfiles) {
      add(
        SettingsLink(R.string.settings_title_profiles, R.drawable.ic_account_box, ProfilesSettings)
      )
    }
    if (showNotification) {
      add(
        SettingsLink(
          R.string.settings_title_notification,
          R.drawable.ic_notifications,
          NotificationSettings,
        )
      )
    }
    add(SettingsLink(R.string.settings_title_system, R.drawable.ic_settings, SystemSettings))
  }
  val supportLinks: List<SettingsLink> =
    listOf(
      SettingsLink(R.string.settings_title_help, R.drawable.ic_help, Help),
      SettingsLink(R.string.settings_title_about, R.drawable.ic_info, About),
    )
  Column {
    SettingsCardTitle(stringResource(R.string.home_section_settings))
    LinkCard(settingsLinks, settings, onNavigate)
  }
  LinkCard(supportLinks, settings, onNavigate)
}

private data class SettingsLink(
  @param:StringRes val titleRes: Int,
  @param:DrawableRes val iconRes: Int,
  val destination: NavKey,
)

@Composable
private fun LinkCard(links: List<SettingsLink>, settings: Settings, onNavigate: (NavKey) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    links.forEachIndexed { index, link ->
      LinkRow(
        shapes = settingsRowShapes(index = index, count = links.size),
        title = stringResource(link.titleRes),
        subtitle = homeSummary(link.destination, settings),
        icon = ImageVector.vectorResource(link.iconRes),
        framed = true,
        onClick = { onNavigate(link.destination) },
      )
    }
  }
}

@Composable
private fun stateLabel(monitor: MonitorState, needsPermission: Boolean): String =
  when {
    needsPermission -> stringResource(R.string.home_state_needs_permission)
    monitor is MonitorState.Running && monitor.visible -> stringResource(R.string.home_state_on)
    monitor is MonitorState.Running -> stringResource(R.string.home_state_hidden)
    monitor is MonitorState.Paused -> stringResource(R.string.home_state_paused)
    else -> stringResource(R.string.home_state_off)
  }
