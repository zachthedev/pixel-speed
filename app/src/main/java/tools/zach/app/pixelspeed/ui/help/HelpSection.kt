package tools.zach.app.pixelspeed.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.zach.app.pixelspeed.ui.settings.SettingsCardTitle

/** One help topic: a question or a name, and its one-sentence answer. */
data class HelpEntry(@param:StringRes val titleRes: Int, @param:StringRes val bodyRes: Int)

/**
 * A titled run of help topics as prose on the page surface. Nothing here is tappable, so nothing
 * here sits on a row container, which is what the eye reads as a link.
 */
fun LazyListScope.helpSection(@StringRes titleRes: Int, entries: List<HelpEntry>) {
  item { SettingsCardTitle(stringResource(titleRes)) }
  item {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(ENTRY_GAP),
    ) {
      entries.forEach { entry -> HelpTopic(entry) }
    }
  }
}

@Composable
private fun HelpTopic(entry: HelpEntry) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = stringResource(entry.titleRes),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(entry.bodyRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private val ENTRY_GAP = 16.dp
