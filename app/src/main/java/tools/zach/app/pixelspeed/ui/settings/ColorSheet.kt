package tools.zach.app.pixelspeed.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import tools.zach.app.pixelspeed.R

/**
 * A bottom sheet that picks an ARGB color: dynamic-theme swatches, a custom HSV picker, or the
 * theme default via [onPick] with `null`.
 */
@Composable
fun ColorSheet(title: String, current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
  val sheetState = rememberHiddenSheetState(fullHeight = true)
  val scheme = MaterialTheme.colorScheme
  val swatches =
    listOf(
      stringResource(R.string.settings_color_sheet_swatch_primary) to scheme.primary,
      stringResource(R.string.settings_color_sheet_swatch_secondary) to scheme.secondary,
      stringResource(R.string.settings_color_sheet_swatch_tertiary) to scheme.tertiary,
      stringResource(R.string.settings_color_sheet_swatch_surface) to scheme.surface,
      stringResource(R.string.settings_color_sheet_swatch_on_surface) to scheme.onSurface,
      stringResource(R.string.settings_color_sheet_swatch_primary_container) to
        scheme.primaryContainer,
      stringResource(R.string.settings_color_sheet_swatch_black) to Color.Black,
      stringResource(R.string.settings_color_sheet_swatch_white) to Color.White,
    )
  val controller = rememberColorPickerController()
  var customColorArgb by remember { mutableIntStateOf(current ?: scheme.primary.toArgb()) }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    // The picker scrolls under a short viewport; the two actions stay pinned below it.
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        swatches.forEach { (label, color) ->
          val selected = current == color.toArgb()
          // A 48 dp target around a 40 dp swatch. Every swatch has an outline so the ones near
          // the sheet's own color keep an edge; the selected one wears a gap ring, an outer line
          // with a gap of the sheet behind it, which reads on a swatch of any color.
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .selectable(selected = selected, role = Role.RadioButton) {
                  onPick(color.toArgb())
                  onDismiss()
                }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier =
                Modifier.size(40.dp)
                  .then(
                    if (selected) {
                      Modifier.border(2.dp, scheme.onSurface, CircleShape).padding(4.dp)
                    } else {
                      Modifier
                    }
                  )
                  .clip(CircleShape)
                  .background(color)
                  .border(1.dp, scheme.outline, CircleShape)
            )
          }
        }
      }
      Text(
        text = stringResource(R.string.settings_color_sheet_custom_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      HsvColorPicker(
        modifier = Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 24.dp),
        controller = controller,
        initialColor = Color(customColorArgb),
        onColorChanged = { envelope ->
          if (envelope.fromUser) customColorArgb = envelope.color.toArgb()
        },
      )
      BrightnessSlider(
        modifier =
          Modifier.fillMaxWidth().height(35.dp).padding(horizontal = 24.dp, vertical = 8.dp),
        controller = controller,
      )
      AlphaTile(
        modifier =
          Modifier.fillMaxWidth()
            .height(35.dp)
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(6.dp)),
        controller = controller,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      TextButton(
        onClick = {
          onPick(null)
          onDismiss()
        }
      ) {
        Text(stringResource(R.string.settings_appearance_from_theme))
      }
      Button(
        onClick = {
          onPick(customColorArgb)
          onDismiss()
        }
      ) {
        Text(stringResource(R.string.settings_color_sheet_use_this_color))
      }
    }
    Spacer(Modifier.padding(bottom = 24.dp))
  }
}
