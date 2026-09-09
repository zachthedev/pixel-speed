package tools.zach.app.pixelspeed.ui.about

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.ui.settings.InfoRow
import tools.zach.app.pixelspeed.ui.settings.SettingsCardTitle
import tools.zach.app.pixelspeed.ui.settings.SettingsRowMinHeight
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen
import tools.zach.app.pixelspeed.ui.settings.settingsCard
import tools.zach.app.pixelspeed.ui.settings.settingsRowColors

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  // Null until the asset is read, so the empty state never flashes before the real policy.
  val sections: List<PolicySection>? by
    produceState<List<PolicySection>?>(initialValue = null) {
      value = withContext(Dispatchers.IO) { loadPolicy(context) }
    }
  PrivacyContent(sections = sections, onBack = onBack)
}

/** The rendering half of [PrivacyScreen], separated so it can be shown with fixed data. */
@Composable
fun PrivacyContent(sections: List<PolicySection>?, onBack: () -> Unit) {
  SettingsScreen(title = stringResource(R.string.about_privacy_title), onBack = onBack) {
    val loaded = sections ?: return@SettingsScreen
    if (loaded.isEmpty()) {
      settingsCard {
        row { shapes ->
          InfoRow(shapes = shapes, title = stringResource(R.string.about_privacy_empty_title))
        }
      }
      return@SettingsScreen
    }
    loaded.forEach { section -> policySection(section) }
  }
}

private fun LazyListScope.policySection(section: PolicySection) {
  section.title?.let { title -> item { SettingsCardTitle(title) } }
  section.blocks.forEach { block ->
    when (block) {
      is PolicyBlock.Paragraph -> item { PolicyParagraph(block.text) }
      is PolicyBlock.Items ->
        settingsCard {
          block.items.forEachIndexed { index, policyItem ->
            row { shapes -> PolicyItemRow(shapes = shapes, number = index + 1, item = policyItem) }
          }
        }
    }
  }
}

@Composable
private fun PolicyParagraph(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  )
}

/**
 * A permission row numbered like an onboarding step. The kit's [InfoRow] has no slot for a
 * constant, so this builds the row directly to show one as a small line after the detail.
 */
@Composable
private fun PolicyItemRow(shapes: ListItemShapes, number: Int, item: PolicyItem) {
  SegmentedListItem(
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    leadingContent = { PolicyBadge(number) },
    supportingContent = policySupporting(item),
    colors = settingsRowColors(),
  ) {
    Text(item.title)
  }
}

@Composable
private fun policySupporting(item: PolicyItem): (@Composable () -> Unit)? {
  if (item.detail == null && item.note == null) return null
  return {
    Text(
      buildAnnotatedString {
        item.detail?.let(::append)
        item.note?.let { note ->
          if (item.detail != null) append(" ")
          withStyle(
            SpanStyle(
              fontSize = MaterialTheme.typography.labelSmall.fontSize,
              color = MaterialTheme.colorScheme.outline,
            )
          ) {
            append(note)
          }
        }
      }
    )
  }
}

@Composable
private fun PolicyBadge(number: Int) {
  Box(
    modifier =
      Modifier.size(BADGE_SIZE).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = number.toString(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

private val BADGE_SIZE: Dp = 32.dp

/** Reads the policy asset for the current locale off the caller's thread, parsed. */
private fun loadPolicy(context: Context): List<PolicySection> {
  val tag: String = Locale.getDefault().toLanguageTag()
  for (name in policyAssetNames(tag)) {
    val text: String =
      try {
        context.assets.open(name).bufferedReader().use { it.readText() }
      } catch (e: FileNotFoundException) {
        continue
      } catch (e: IOException) {
        AppLog.e("Cannot read the $name asset", e)
        return emptyList()
      }
    return PrivacyPolicy.parse(text)
  }
  AppLog.i("No privacy policy asset yet; this is a development build")
  return emptyList()
}
