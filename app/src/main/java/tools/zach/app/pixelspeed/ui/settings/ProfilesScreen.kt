package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.ProfileShortcuts
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsBounds
import tools.zach.app.pixelspeed.settings.SettingsDefaults
import tools.zach.app.pixelspeed.settings.isControlOrFormat

private val MAX_PROFILES: Int = SettingsBounds.PROFILE_COUNT.last
/** The bound the import path enforces on a name, typed or generated. */
internal val MAX_PROFILE_NAME_LENGTH: Int = SettingsBounds.TEXT_LENGTH.last

/** The first numbered default name no profile carries, counting up from the profile count. */
private fun nextProfileName(resources: Resources, taken: List<String>): String {
  var number = taken.size + 1
  while (true) {
    val candidate =
      resources
        .getString(R.string.settings_profiles_new_name, number)
        .cutTo(MAX_PROFILE_NAME_LENGTH)
    if (taken.none { it.equals(candidate, ignoreCase = true) }) return candidate
    number++
  }
}

/** The first [count] chars, never ending on a high surrogate, so a cut name stays valid text. */
private fun String.cutTo(count: Int): String {
  val cut = take(count.coerceAtLeast(0))
  return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}

/**
 * The name for a duplicate: the original plus [suffix], numbered from 2 while a profile already
 * carries it (case-insensitive), and cut at the front so the whole name stays within the bound.
 */
internal fun copyName(original: String, taken: List<String>, suffix: String): String {
  val used = taken.map { it.lowercase() }.toSet()
  var attempt = 1
  while (true) {
    val tail = if (attempt == 1) suffix else "$suffix $attempt"
    val candidate = original.cutTo(MAX_PROFILE_NAME_LENGTH - tail.length) + tail
    if (candidate.lowercase() !in used) return candidate
    attempt++
  }
}

@Composable
fun ProfilesScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings: Settings? by viewModel.settings.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val resources = LocalResources.current
  var actionsFor: Profile? by remember { mutableStateOf(null) }
  var renameTarget: Profile? by remember { mutableStateOf(null) }
  var deleteTarget: Profile? by remember { mutableStateOf(null) }

  SettingsScreen(title = stringResource(R.string.settings_title_profiles), onBack = onBack) {
    val current = settings
    if (current != null) {
      settingsCard {
        current.profiles.forEach { profile ->
          row { shapes ->
            ProfileRow(
              shapes = shapes,
              title = profile.name,
              subtitle =
                if (profile.id == current.activeProfileId) {
                  stringResource(R.string.settings_profiles_active)
                } else {
                  profileSummary(profile)
                },
              onClick = { viewModel.updateSettings { it.copy(activeProfileId = profile.id) } },
              onMoreClick = { actionsFor = profile },
            )
          }
        }
      }
      settingsCard {
        row { shapes ->
          LinkRow(
            shapes = shapes,
            title = stringResource(R.string.settings_profiles_add),
            subtitle = stringResource(R.string.settings_profiles_add_subtitle),
            enabled = current.profiles.size < MAX_PROFILES,
            onClick = {
              viewModel.updateSettings { latest ->
                if (latest.profiles.size >= MAX_PROFILES) return@updateSettings latest
                val fresh =
                  SettingsDefaults.defaultProfile.copy(
                    id = UUID.randomUUID().toString(),
                    name = nextProfileName(resources, latest.profiles.map { it.name }),
                  )
                latest.copy(profiles = latest.profiles + fresh)
              }
            },
          )
        }
      }
    }
  }

  val sheetTarget = actionsFor
  val settingsForSheet = settings
  if (sheetTarget != null && settingsForSheet != null) {
    val profiles = settingsForSheet.profiles
    val index = profiles.indexOfFirst { it.id == sheetTarget.id }
    ProfileActionsSheet(
      profile = sheetTarget,
      canMoveUp = index > 0,
      canMoveDown = index in 0 until profiles.lastIndex,
      canDelete = profiles.size > 1,
      canDuplicate = profiles.size < MAX_PROFILES,
      onDismiss = { actionsFor = null },
      onRename = {
        renameTarget = sheetTarget
        actionsFor = null
      },
      onDuplicate = {
        val suffix = resources.getString(R.string.settings_profiles_copy_suffix)
        viewModel.updateSettings { current ->
          if (current.profiles.size >= MAX_PROFILES) return@updateSettings current
          val copy =
            sheetTarget.copy(
              id = UUID.randomUUID().toString(),
              name =
                copyName(
                  original = sheetTarget.name,
                  taken = current.profiles.map { it.name },
                  suffix = suffix,
                ),
            )
          current.copy(profiles = current.profiles + copy)
        }
        actionsFor = null
      },
      onMoveUp = {
        viewModel.updateSettings { it.copy(profiles = it.profiles.swapped(index, index - 1)) }
        actionsFor = null
      },
      onMoveDown = {
        viewModel.updateSettings { it.copy(profiles = it.profiles.swapped(index, index + 1)) }
        actionsFor = null
      },
      onDelete = {
        deleteTarget = sheetTarget
        actionsFor = null
      },
    )
  }

  val renamed = renameTarget
  val settingsForRename = settings
  if (renamed != null && settingsForRename != null) {
    RenameProfileDialog(
      profile = renamed,
      otherNames = settingsForRename.profiles.filter { it.id != renamed.id }.map { it.name },
      onConfirm = { newName ->
        viewModel.replaceSettings { current ->
          current.copy(
            profiles =
              current.profiles.map { if (it.id == renamed.id) it.copy(name = newName) else it }
          )
        }
        renameTarget = null
      },
      onDismiss = { renameTarget = null },
    )
  }

  val deleted = deleteTarget
  if (deleted != null) {
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text(stringResource(R.string.settings_profiles_delete_title)) },
      text = { Text(stringResource(R.string.settings_profiles_delete_message, deleted.name)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.replaceSettings { current ->
              if (current.profiles.size <= 1) return@replaceSettings current
              val remaining = current.profiles.filterNot { it.id == deleted.id }
              val activeId =
                if (current.activeProfileId == deleted.id) {
                  remaining.firstOrNull()?.id ?: deleted.id
                } else {
                  current.activeProfileId
                }
              current.copy(profiles = remaining, activeProfileId = activeId)
            }
            deleteTarget = null
          }
        ) {
          Text(stringResource(R.string.settings_shared_action_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) {
          Text(stringResource(R.string.settings_shared_action_cancel))
        }
      },
    )
  }
}

private fun <T> List<T>.swapped(from: Int, to: Int): List<T> {
  if (from == to || from !in indices || to !in indices) return this
  val mutable = toMutableList()
  val item = mutable.removeAt(from)
  mutable.add(to, item)
  return mutable
}

/** A [LinkRow] with a trailing overflow button. */
@Composable
private fun ProfileRow(
  shapes: ListItemShapes,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  onMoreClick: () -> Unit,
) {
  LinkRow(
    shapes = shapes,
    title = title,
    onClick = onClick,
    subtitle = subtitle,
    trailing = {
      IconButton(onClick = onMoreClick) {
        Icon(
          painter = painterResource(R.drawable.ic_more_vert),
          contentDescription = stringResource(R.string.settings_profiles_action_more),
        )
      }
    },
  )
}

@Composable
private fun profileSummary(profile: Profile): String {
  val style = stringResource(styleLabelRes(profile.appearance.style))
  val anchor = stringResource(anchorLabelRes(profile.position.anchor))
  val interval = intervalLabel(profile.behavior.updateIntervalMs)
  return stringResource(R.string.settings_profiles_summary, style, anchor, interval)
}

/** The update interval as seconds, whole where it is whole: "1 s", "1.5 s". */
@Composable
internal fun intervalLabel(updateIntervalMs: Int): String {
  val seconds = updateIntervalMs / 1000f
  val locale = LocalConfiguration.current.locales[0]
  val secondsText =
    if (seconds == seconds.toInt().toFloat()) "${seconds.toInt()}"
    else String.format(locale, "%.1f", seconds)
  return stringResource(R.string.settings_profiles_interval_seconds, secondsText)
}

internal fun styleLabelRes(style: OverlayStyle): Int =
  when (style) {
    OverlayStyle.CHIP -> R.string.settings_profiles_style_chip
    OverlayStyle.COMPACT -> R.string.settings_profiles_style_compact
    OverlayStyle.COMPACT12 -> R.string.settings_profiles_style_compact12
    OverlayStyle.STACK -> R.string.settings_profiles_style_stack
    OverlayStyle.STACK_NO_UNIT -> R.string.settings_profiles_style_stack_no_unit
    OverlayStyle.SINGLE -> R.string.settings_profiles_style_single
    OverlayStyle.SINGLE_NO_UNIT -> R.string.settings_profiles_style_single_no_unit
    OverlayStyle.HALO -> R.string.settings_profiles_style_halo
    OverlayStyle.GLYPHS -> R.string.settings_profiles_style_glyphs
  }

private fun anchorLabelRes(anchor: Anchor): Int =
  when (anchor) {
    Anchor.STATUS_BAR_CUTOUT -> R.string.settings_profiles_anchor_status_bar_cutout
    Anchor.STATUS_BAR_START -> R.string.settings_profiles_anchor_status_bar_start
    Anchor.STATUS_BAR_BEFORE_CUTOUT -> R.string.settings_profiles_anchor_status_bar_before_cutout
    Anchor.STATUS_BAR_AFTER_CUTOUT -> R.string.settings_profiles_anchor_status_bar_after_cutout
    Anchor.STATUS_BAR_END -> R.string.settings_profiles_anchor_status_bar_end
    Anchor.TOP_START -> R.string.settings_profiles_anchor_top_start
    Anchor.TOP_END -> R.string.settings_profiles_anchor_top_end
    Anchor.BOTTOM_START -> R.string.settings_profiles_anchor_bottom_start
    Anchor.BOTTOM_END -> R.string.settings_profiles_anchor_bottom_end
    Anchor.CUSTOM -> R.string.settings_profiles_anchor_custom
  }

@Composable
private fun ProfileActionsSheet(
  profile: Profile,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  canDelete: Boolean,
  canDuplicate: Boolean,
  onDismiss: () -> Unit,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onDelete: () -> Unit,
) {
  val context = LocalContext.current
  var shortcutOn by
    remember(profile.id) { mutableStateOf(ProfileShortcuts.isPublished(context, profile.id)) }
  val sheetState = rememberHiddenSheetState()
  val actionRowCount = 5
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Text(
      text = profile.name,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    Column(
      modifier = Modifier.padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
      SheetActionRow(
        shapes = settingsRowShapes(index = 0, count = actionRowCount),
        iconRes = R.drawable.ic_edit,
        label = stringResource(R.string.settings_profiles_sheet_rename),
        onClick = onRename,
      )
      SheetActionRow(
        shapes = settingsRowShapes(index = 1, count = actionRowCount),
        iconRes = R.drawable.ic_content_copy,
        label = stringResource(R.string.settings_profiles_sheet_duplicate),
        enabled = canDuplicate,
        onClick = onDuplicate,
      )
      SheetActionRow(
        shapes = settingsRowShapes(index = 2, count = actionRowCount),
        iconRes = R.drawable.ic_arrow_upward,
        label = stringResource(R.string.settings_profiles_sheet_move_up),
        enabled = canMoveUp,
        onClick = onMoveUp,
      )
      SheetActionRow(
        shapes = settingsRowShapes(index = 3, count = actionRowCount),
        iconRes = R.drawable.ic_arrow_downward,
        label = stringResource(R.string.settings_profiles_sheet_move_down),
        enabled = canMoveDown,
        onClick = onMoveDown,
      )
      SheetActionRow(
        shapes = settingsRowShapes(index = 4, count = actionRowCount),
        iconRes = R.drawable.ic_delete,
        label = stringResource(R.string.settings_profiles_sheet_delete),
        enabled = canDelete,
        onClick = onDelete,
      )
    }
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
      SwitchRow(
        shapes = settingsRowShapes(index = 0, count = 1),
        title = stringResource(R.string.settings_profiles_sheet_shortcut),
        subtitle = stringResource(R.string.settings_profiles_sheet_shortcut_subtitle),
        checked = shortcutOn,
        onCheckedChange = { checked ->
          shortcutOn =
            if (checked) {
              ProfileShortcuts.publish(context, profile)
            } else {
              ProfileShortcuts.remove(context, profile.id)
              false
            }
        },
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun SheetActionRow(
  shapes: ListItemShapes,
  iconRes: Int,
  label: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  SegmentedListItem(
    onClick = onClick,
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    enabled = enabled,
    leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) },
    colors = settingsRowColors(),
  ) {
    Text(label)
  }
}

@Composable
private fun RenameProfileDialog(
  profile: Profile,
  otherNames: List<String>,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember(profile.id) { mutableStateOf(profile.name) }
  var error by remember(profile.id) { mutableStateOf<Int?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_profiles_rename_title)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it.filterNot(::isControlOrFormat).cutTo(MAX_PROFILE_NAME_LENGTH)
          error = null
        },
        label = { Text(stringResource(R.string.settings_profiles_rename_label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
      )
    },
    confirmButton = {
      TextButton(
        onClick = {
          val trimmed = name.trim()
          when {
            trimmed.isEmpty() -> error = R.string.settings_profiles_rename_error_empty
            otherNames.any { it.equals(trimmed, ignoreCase = true) } ->
              error = R.string.settings_profiles_rename_error_duplicate
            else -> onConfirm(trimmed)
          }
        }
      ) {
        Text(stringResource(R.string.settings_shared_action_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.settings_shared_action_cancel))
      }
    },
  )
}
