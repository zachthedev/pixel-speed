package tools.zach.app.pixelspeed.ui.tile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.service.TilePauseOptions
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.ui.settings.PauseDurationLabels

/**
 * The Quick Settings dialog for an app with several profiles: one radio row per profile, and on the
 * active one a split button that pauses for the tile duration, or resumes, with a menu of the
 * profile's other durations. Two taps from the shade to a pause. The list scrolls inside a bound,
 * so twelve profiles with long names still reach the active row on the shortest display.
 */
@Composable
fun TileProfileDialog(
  settings: Settings,
  monitor: MonitorState,
  onSelect: (String) -> Unit,
  onPause: (minutes: Int?) -> Unit,
  onResume: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.width(DIALOG_WIDTH),
  ) {
    Column(Modifier.padding(vertical = 24.dp)) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      Column(
        Modifier.heightIn(max = LIST_MAX_HEIGHT)
          .verticalScroll(rememberScrollState())
          .selectableGroup()
      ) {
        settings.profiles.forEach { profile ->
          val active = profile.id == settings.activeProfile.id
          ProfileRow(
            profile = profile,
            active = active,
            monitor = monitor,
            onSelect = { onSelect(profile.id) },
            onPause = onPause,
            onResume = onResume,
          )
        }
      }
    }
  }
}

@Composable
private fun ProfileRow(
  profile: Profile,
  active: Boolean,
  monitor: MonitorState,
  onSelect: () -> Unit,
  onPause: (minutes: Int?) -> Unit,
  onResume: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = ROW_HEIGHT)
        .selectable(selected = active, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = active, onClick = null)
    Spacer(Modifier.width(16.dp))
    // The pause control keeps its width, so a long name at a large font scale ends in an ellipsis
    // rather than breaking inside a word.
    Text(
      text = profile.name,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    // Off and needs-permission have nothing to pause, the same refusal a plain tile tap makes.
    if (active && monitor is MonitorState.Running) {
      PauseControl(profile = profile, onPause = onPause)
    }
    if (active && monitor is MonitorState.Paused) {
      // Resume stands alone, so it is a whole button rather than a split button's leading half.
      Button(onClick = onResume) { Text(stringResource(R.string.notification_action_resume)) }
    }
  }
}

/** The main segment acts at once; the arrow opens the other durations. */
@Composable
private fun PauseControl(profile: Profile, onPause: (minutes: Int?) -> Unit) {
  var menuOpen by remember { mutableStateOf(false) }
  val default = TilePauseOptions.default(profile)
  Box {
    SplitButtonLayout(
      leadingButton = {
        SplitButtonDefaults.LeadingButton(onClick = { onPause(default) }) {
          Text(pauseLabel(default))
        }
      },
      trailingButton = {
        SplitButtonDefaults.TrailingButton(
          checked = menuOpen,
          onCheckedChange = { menuOpen = it },
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_arrow_drop_down),
            contentDescription = stringResource(R.string.tile_dialog_durations),
          )
        }
      },
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      TilePauseOptions.options(profile).forEach { minutes ->
        DropdownMenuItem(
          text = { Text(durationLabel(minutes)) },
          onClick = {
            menuOpen = false
            onPause(minutes)
          },
        )
      }
    }
  }
}

@Composable
private fun pauseLabel(minutes: Int?): String =
  if (minutes == null) stringResource(R.string.notification_action_pause)
  else stringResource(R.string.tile_dialog_pause_for, durationLabel(minutes))

@Composable
private fun durationLabel(minutes: Int?): String {
  if (minutes == null) return stringResource(R.string.home_pause_until_resumed)
  val resource = PauseDurationLabels.resourceFor(minutes)
  val pluralsRes =
    when (resource.unit) {
      PauseDurationLabels.PauseUnit.MINUTES -> R.plurals.home_duration_minutes
      PauseDurationLabels.PauseUnit.HOURS -> R.plurals.home_duration_hours
      PauseDurationLabels.PauseUnit.DAYS -> R.plurals.home_duration_days
    }
  return pluralStringResource(pluralsRes, resource.quantity, resource.quantity)
}

private val DIALOG_WIDTH = 312.dp
private val ROW_HEIGHT = 56.dp
/** Six rows; the shortest catalog display shows the dialog whole at this bound. */
private val LIST_MAX_HEIGHT = ROW_HEIGHT * 6
