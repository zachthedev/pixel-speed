package tools.zach.app.pixelspeed.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.overlay.Anchor

/**
 * The anchor as two connected groups, the five status bar slots and the five places outside the
 * bar, each chunked into rows the width allows so every anchor keeps a full-size target. A phone
 * drawn small enough for a row cannot keep 48 dp between the two spots beside the hole, so the
 * picture lives in the status bar preview above and the control here is words. The visible labels
 * are short and the row title above them says what they place; a screen reader gets the full name
 * of each option, which carries its own context.
 */
@Composable
fun AnchorRow(
  shapes: ListItemShapes,
  title: String,
  selected: Anchor,
  onSelected: (Anchor) -> Unit,
  subtitle: String? = null,
  enabled: Boolean = true,
) {
  val resources = LocalResources.current
  val groups = listOf(STATUS_BAR_ANCHORS, FREE_ANCHORS)
  SegmentedListItem(
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    enabled = enabled,
    supportingContent = {
      Column {
        if (subtitle != null) Text(subtitle)
        BoxWithConstraints(modifier = Modifier.padding(top = CONTROL_GAP)) {
          val perRow = connectedOptionLimit(maxWidth)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { anchors ->
              // One group per five anchors, whatever the rows, so a screen reader counts five.
              Column(
                modifier = Modifier.connectedGroup(null),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                anchors.chunkedEvenly(perRow).forEach { row ->
                  ConnectedChoice(
                    title = null,
                    options = row,
                    selected = selected,
                    label = { anchorShortLabel(resources, it) },
                    description = { anchorLabel(resources, it) },
                    onSelected = onSelected,
                    enabled = enabled,
                    grouped = false,
                  )
                }
              }
            }
          }
        }
      }
    },
    colors = settingsRowColors(),
  ) {
    Text(title)
  }
}

/** Rows as even as the limit allows: five at a limit of four is three and two, not four and one. */
internal fun <T> List<T>.chunkedEvenly(limit: Int): List<List<T>> {
  if (isEmpty()) return emptyList()
  val rows = ceil(size / limit.toDouble()).toInt()
  val perRow = ceil(size / rows.toDouble()).toInt()
  return chunked(perRow)
}

private val STATUS_BAR_ANCHORS: List<Anchor> =
  listOf(
    Anchor.STATUS_BAR_START,
    Anchor.STATUS_BAR_BEFORE_CUTOUT,
    Anchor.STATUS_BAR_CUTOUT,
    Anchor.STATUS_BAR_AFTER_CUTOUT,
    Anchor.STATUS_BAR_END,
  )

private val FREE_ANCHORS: List<Anchor> =
  listOf(Anchor.TOP_START, Anchor.TOP_END, Anchor.BOTTOM_START, Anchor.BOTTOM_END, Anchor.CUSTOM)

private fun anchorShortLabel(resources: Resources, anchor: Anchor): String =
  resources.getString(
    when (anchor) {
      Anchor.STATUS_BAR_CUTOUT -> R.string.settings_position_anchor_short_cutout
      Anchor.STATUS_BAR_START -> R.string.settings_position_anchor_short_start
      Anchor.STATUS_BAR_BEFORE_CUTOUT -> R.string.settings_position_anchor_short_before_cutout
      Anchor.STATUS_BAR_AFTER_CUTOUT -> R.string.settings_position_anchor_short_after_cutout
      Anchor.STATUS_BAR_END -> R.string.settings_position_anchor_short_end
      Anchor.TOP_START -> R.string.settings_position_anchor_top_start
      Anchor.TOP_END -> R.string.settings_position_anchor_top_end
      Anchor.BOTTOM_START -> R.string.settings_position_anchor_bottom_start
      Anchor.BOTTOM_END -> R.string.settings_position_anchor_bottom_end
      Anchor.CUSTOM -> R.string.settings_position_anchor_short_custom
    }
  )

/** The full name of an anchor: what a screen reader announces, and what Home shows as the value. */
internal fun anchorLabel(resources: Resources, anchor: Anchor): String =
  resources.getString(
    when (anchor) {
      Anchor.STATUS_BAR_CUTOUT -> R.string.settings_position_anchor_status_bar_cutout
      Anchor.STATUS_BAR_START -> R.string.settings_position_anchor_status_bar_start
      Anchor.STATUS_BAR_BEFORE_CUTOUT -> R.string.settings_position_anchor_status_bar_before_cutout
      Anchor.STATUS_BAR_AFTER_CUTOUT -> R.string.settings_position_anchor_status_bar_after_cutout
      Anchor.STATUS_BAR_END -> R.string.settings_position_anchor_status_bar_end
      Anchor.TOP_START -> R.string.settings_position_anchor_top_start
      Anchor.TOP_END -> R.string.settings_position_anchor_top_end
      Anchor.BOTTOM_START -> R.string.settings_position_anchor_bottom_start
      Anchor.BOTTOM_END -> R.string.settings_position_anchor_bottom_end
      Anchor.CUSTOM -> R.string.settings_position_anchor_custom
    }
  )
