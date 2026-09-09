package tools.zach.app.pixelspeed.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import tools.zach.app.pixelspeed.R

/**
 * The shared shape of every settings screen: the Pixel Settings sub-page bar and a lazy column of
 * [settingsCard] groups on the page surface Pixel Settings uses.
 */
@Composable
fun SettingsScreen(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = { SettingsTopBar(title, onBack, scrollBehavior) },
  ) { innerPadding ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        modifier = Modifier.contentWidthCap().fillMaxSize().padding(horizontal = SCREEN_PADDING),
        content = content,
      )
    }
  }
}

/**
 * The Pixel Settings sub-page bar: the title beside a circular tonal Back, pinned while the list
 * scrolls. Beside a visible list pane the bar shows no Back, since the list itself is the way back.
 */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit, scrollBehavior: TopAppBarScrollBehavior) {
  val showBack = !LocalTwoPane.current
  TopAppBar(
    title = {
      Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Pixel Settings keeps 20 dp between the Back circle and the title; without Back the bar's
        // own 16 dp inset is the Pixel margin already.
        modifier = Modifier.padding(start = if (showBack) TOP_BAR_TITLE_GAP else 0.dp),
      )
    },
    navigationIcon = {
      if (showBack) Box(Modifier.padding(start = TOP_BAR_BACK_INSET)) { BackButton(onBack) }
    },
    colors = settingsTopBarColors(),
    scrollBehavior = scrollBehavior,
  )
}

/** One selectable group for a screen reader, named by [title] or a silent merge boundary. */
fun Modifier.connectedGroup(title: String?): Modifier =
  selectableGroup()
    .then(
      if (title != null) Modifier.semantics { contentDescription = title }
      else Modifier.semantics(mergeDescendants = true) {}
    )

/**
 * A modal sheet's state, starting hidden, the way every sheet in the app opens. [fullHeight] skips
 * the half-open stop, for a sheet whose actions sit at the bottom of content taller than half the
 * screen.
 */
@Composable
fun rememberHiddenSheetState(fullHeight: Boolean = false): SheetState =
  rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues =
      if (fullHeight) setOf(SheetValue.Hidden, SheetValue.Expanded)
      else setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
  )

/** The Pixel Settings Back: the arrow in a tonal circle on the page surface. */
@Composable
fun BackButton(onBack: () -> Unit) {
  FilledTonalIconButton(
    onClick = onBack,
    colors =
      IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
      ),
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_arrow_back),
      contentDescription = stringResource(R.string.settings_action_back),
    )
  }
}

/**
 * Whether the navigation host lays list and detail side by side. The host provides it from the one
 * directive its scene uses, so a hidden Back always means the list pane is on screen.
 */
val LocalTwoPane: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Content on a wide single pane stays within the expanded-class reading width, centered. */
fun Modifier.contentWidthCap(): Modifier = widthIn(max = CONTENT_MAX_WIDTH)

/** The bar shares the page surface, expanded or collapsed, so no band shows above the cards. */
@Composable
fun settingsTopBarColors(): TopAppBarColors =
  TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
  )

/** Collects the rows of one card so each can receive its segmented shape by position. */
class SettingsCardScope {
  internal val rows: MutableList<@Composable (ListItemShapes) -> Unit> = mutableListOf()

  fun row(content: @Composable (shapes: ListItemShapes) -> Unit) {
    rows += content
  }
}

/** One grouped card: 20 dp outer corners, 4 dp inner corners, 2 dp gaps between rows. */
fun LazyListScope.settingsCard(
  title: String? = null,
  titleRes: Int? = null,
  build: SettingsCardScope.() -> Unit,
) {
  val scope = SettingsCardScope().apply(build)
  val rows = scope.rows.toList()
  if (title != null || titleRes != null) {
    item { SettingsCardTitle(title ?: stringResource(titleRes ?: 0)) }
  }
  item {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
      rows.forEachIndexed { index, row -> row(settingsRowShapes(index = index, count = rows.size)) }
    }
  }
}

/**
 * The row shapes by position in a card: the library's segmented shape joins the rows with its small
 * inner corner, and the card's outer edges take the Pixel Settings 20 dp corner. Every interaction
 * state draws that one position shape. A state shape of its own (the library's selected shape is a
 * pill with 50 percent corners) would round a joined edge, so none is kept.
 */
@Composable
fun settingsRowShapes(index: Int, count: Int): ListItemShapes {
  val segmented: ListItemShapes =
    ListItemDefaults.segmentedShapes(index, count, ListItemDefaults.shapes())
  return remember(index, count, segmented) {
    val outer = CornerSize(CARD_CORNER)
    val position: CornerBasedShape? = segmented.shape as? CornerBasedShape
    val shape: Shape =
      when {
        position == null -> segmented.shape
        count == 1 -> position.copy(outer, outer, outer, outer)
        index == 0 -> position.copy(topStart = outer, topEnd = outer)
        index == count - 1 -> position.copy(bottomStart = outer, bottomEnd = outer)
        else -> position
      }
    segmented.copy(
      shape = shape,
      selectedShape = shape,
      pressedShape = shape,
      focusedShape = shape,
      hoveredShape = shape,
      draggedShape = shape,
    )
  }
}

@Composable
fun SettingsCardTitle(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
  )
}

/**
 * The whole row toggles; the switch itself only displays, with a check mark in the thumb when on.
 */
@Composable
fun SwitchRow(
  shapes: ListItemShapes,
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  subtitle: String? = null,
  icon: ImageVector? = null,
  enabled: Boolean = true,
) {
  SegmentedListItem(
    checked = checked,
    onCheckedChange = onCheckedChange,
    shapes = shapes,
    // The row shows a switch, so a screen reader calls it one rather than the library's check box.
    modifier = Modifier.heightIn(min = SettingsRowMinHeight).semantics { role = Role.Switch },
    enabled = enabled,
    // The library top-aligns the switch once a subtitle wraps; Pixel Settings centers it on every
    // row height, so rows with one and two subtitle lines read alike.
    verticalAlignment = Alignment.CenterVertically,
    leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
    supportingContent = subtitle?.let { { Text(it) } },
    trailingContent = {
      Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent =
          if (checked) {
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
    },
    colors = settingsRowColors(),
  ) {
    Text(title)
  }
}

/**
 * Two to four values as a connected button group inside a row that is not itself tappable. More
 * values than [connectedOptionLimit] allows, or a label that would wrap inside its option, open a
 * sheet instead, since a group either fits on one row with every label whole or is not a group.
 */
@Composable
fun <T> SegmentedRow(
  shapes: ListItemShapes,
  title: String,
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelected: (T) -> Unit,
  subtitle: String? = null,
  enabled: Boolean = true,
) {
  BoxWithConstraints {
    val optionsWidth = maxWidth - LIST_ITEM_INSETS
    val connected =
      options.size <= connectedOptionLimit(maxWidth, LIST_ITEM_INSETS) &&
        connectedLabelsFit(options.map(label), optionsWidth)
    if (!connected) {
      ChoiceRow(
        shapes = shapes,
        title = title,
        options = options,
        selected = selected,
        label = label,
        onSelected = onSelected,
        description = subtitle,
        enabled = enabled,
      )
    } else {
      SegmentedListItem(
        shapes = shapes,
        modifier = Modifier.heightIn(min = SettingsRowMinHeight),
        enabled = enabled,
        supportingContent = {
          Column {
            if (subtitle != null) Text(subtitle)
            ConnectedChoice(
              title = null,
              options = options,
              selected = selected,
              label = label,
              onSelected = onSelected,
              modifier = Modifier.padding(top = CONTROL_GAP),
              enabled = enabled,
            )
          }
        },
        colors = settingsRowColors(),
      ) {
        Text(title)
      }
    }
  }
}

/**
 * A single-select connected button group: one [ToggleButton] per option with the leading, middle,
 * and trailing connected shapes, each sharing the width equally and all as tall as the tallest. A
 * long label wraps to a second line, then ellipsizes. [title] names the group for a screen reader;
 * a group inside a list row or a card passes null, since the host merges its descendants and would
 * read every group name as part of its own.
 */
@Composable
fun <T> ConnectedChoice(
  title: String?,
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  description: ((T) -> String)? = null,
  icon: ((T) -> Int)? = null,
  grouped: Boolean = true,
) {
  Row(
    modifier =
      // A named group stays a plain collection, which is what a screen reader names its list
      // from. An unnamed one becomes a merge boundary with nothing to say, so the row or card that
      // holds it neither announces a list per group nor gains a stop. A caller that chunks one
      // group over several rows carries the group itself and passes [grouped] false.
      modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Max)
        .then(if (grouped) Modifier.connectedGroup(title) else Modifier),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    options.forEachIndexed { index, option ->
      ConnectedToggle(
        checked = option == selected,
        onChecked = { onSelected(option) },
        shapes = connectedShapes(index = index, count = options.size),
        enabled = enabled,
        // Every option carries its name itself: a screen reader that lands on an option scrolled
        // half out of view still hears it, and an icon option is named without a visible label.
        description = description?.invoke(option) ?: label(option),
      ) {
        if (icon != null) {
          Icon(painter = painterResource(icon(option)), contentDescription = null)
        } else {
          // The option node names itself, so the label is not read a second time.
          Text(
            text = label(option),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
          )
        }
      }
    }
  }
}

/** A radio inside the group: tapping the chosen option keeps it chosen, nothing unchecks. */
@Composable
private fun RowScope.ConnectedToggle(
  checked: Boolean,
  onChecked: () -> Unit,
  shapes: ToggleButtonShapes,
  enabled: Boolean,
  description: String?,
  content: @Composable RowScope.() -> Unit,
) {
  ToggleButton(
    checked = checked,
    onCheckedChange = { onChecked() },
    modifier =
      Modifier.weight(1f).fillMaxHeight().semantics {
        role = Role.RadioButton
        selected = checked
        if (description != null) contentDescription = description
      },
    enabled = enabled,
    shapes = shapes,
    // The library paints every disabled option alike, so the chosen one keeps a tinted container
    // and a disabled group still shows which value is in effect.
    colors =
      if (checked) {
        ToggleButtonDefaults.colors(
          disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA)
        )
      } else {
        ToggleButtonDefaults.colors()
      },
    contentPadding = PaddingValues(CONNECTED_OPTION_PADDING),
    content = content,
  )
}

@Composable
private fun connectedShapes(index: Int, count: Int): ToggleButtonShapes =
  when {
    count == 1 -> ToggleButtonDefaults.shapesFor(ToggleButtonSize.Small)
    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
  }

/**
 * Whether every label sits on one line inside its option when [labels] share [optionsWidth] as a
 * connected group. Measured with the toggle's own label style at the composition's font scale, so
 * the answer follows the font size setting and the language.
 */
@Composable
fun connectedLabelsFit(labels: List<String>, optionsWidth: Dp): Boolean {
  if (labels.isEmpty()) return true
  val measurer = rememberTextMeasurer()
  val style: TextStyle = MaterialTheme.typography.labelLarge
  val density = LocalDensity.current
  val gaps = ButtonGroupDefaults.ConnectedSpaceBetween * (labels.size - 1)
  val perOption = (optionsWidth - gaps) / labels.size - CONNECTED_OPTION_PADDING * 2
  val limitPx = with(density) { perOption.roundToPx() }
  return labels.all { measurer.measure(it, style, maxLines = 1).size.width <= limitPx }
}

/**
 * A row that shows its current value and opens a sheet of radio rows to change it, the Pixel
 * Settings pattern for a choice with more values than a group holds. [description] explains the
 * setting inside the sheet, where the choice is made, and [optionDescription] explains one option
 * under its name there. A [warning] is a consequence of the current value the user must know about,
 * shown under the value.
 */
@Composable
fun <T> ChoiceRow(
  shapes: ListItemShapes,
  title: String,
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelected: (T) -> Unit,
  description: String? = null,
  enabled: Boolean = true,
  disabledOptions: Set<T> = emptySet(),
  warning: String? = null,
  optionDescription: ((T) -> String?)? = null,
) {
  var open by remember { mutableStateOf(false) }
  SegmentedListItem(
    onClick = { open = true },
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    enabled = enabled,
    supportingContent = {
      Column {
        Text(label(selected))
        if (warning != null) WarningLine(warning, enabled)
      }
    },
    colors = settingsRowColors(),
  ) {
    Text(title)
  }
  if (open) {
    ChoiceSheet(
      title = title,
      options = options,
      selected = selected,
      label = label,
      onSelected = {
        onSelected(it)
        open = false
      },
      onDismiss = { open = false },
      description = description,
      disabledOptions = disabledOptions,
      optionDescription = optionDescription,
    )
  }
}

@Composable
fun <T> ChoiceSheet(
  title: String,
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelected: (T) -> Unit,
  onDismiss: () -> Unit,
  description: String? = null,
  disabledOptions: Set<T> = emptySet(),
  optionDescription: ((T) -> String?)? = null,
) {
  val sheetState = rememberHiddenSheetState()
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    if (description != null) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
      )
    }
    Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
      options.forEach { option ->
        val chosen = option == selected
        val available = option !in disabledOptions
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 56.dp)
              .selectable(selected = chosen, enabled = available, role = Role.RadioButton) {
                onSelected(option)
              }
              .padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = chosen, onClick = null, enabled = available)
          Spacer(Modifier.width(16.dp))
          val contentAlpha = if (available) 1f else DISABLED_ALPHA
          Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
              text = label(option),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            val explanation = optionDescription?.invoke(option)
            if (explanation != null) {
              Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

/**
 * One value a [PresetRow] offers, under the name the row shows for it. A [description] is what a
 * screen reader hears after the option's name, so a consequence of choosing it is heard before the
 * choice.
 */
data class Preset<T>(val label: String, val value: T, val description: String? = null)

/**
 * Whether a row that regular mode hides is shown on this visit of a screen. It shows while
 * [advanced] mode is on or while [inEffect], meaning its value is away from the default in a way
 * that must stay reachable, and once shown it stays until the screen is left, the mode changes, or
 * the [profileId] whose value it shows changes, so a control never vanishes under the finger that
 * just reset it.
 */
@Composable
fun rememberRevealed(advanced: Boolean, inEffect: Boolean, profileId: String? = null): Boolean {
  val revealed =
    rememberSaveable(advanced, profileId) { mutableStateOf(reveal(advanced, inEffect)) }
  SideEffect { revealed.value = reveal(revealed.value, inEffect) }
  return revealed.value
}

/**
 * The latch step of [rememberRevealed]: a row [shown] stays shown, and one that is not shows once
 * its value is [inEffect].
 */
internal fun reveal(shown: Boolean, inEffect: Boolean): Boolean = shown || inEffect

/**
 * A fixed set of named values as a connected group. The preset whose value equals [value] is
 * selected; a value set another way (a slider in advanced mode, an imported file) leaves the group
 * with nothing selected, since a preset that is not in effect must not claim to be, and the row
 * names that value through [valueLabel] instead. A [warning] is a consequence of the current value
 * the user must know about, shown under the group.
 */
@Composable
fun <T> PresetRow(
  shapes: ListItemShapes,
  title: String,
  presets: List<Preset<T>>,
  value: T,
  valueLabel: (T) -> String,
  onSelected: (T) -> Unit,
  subtitle: String? = null,
  warning: String? = null,
  enabled: Boolean = true,
) {
  // The option type is nullable so that no preset at all is a selectable state of the group.
  val selected: Preset<T>? = presets.firstOrNull { it.value == value }
  val options: List<Preset<T>?> = presets
  BoxWithConstraints {
    val optionsWidth = maxWidth - LIST_ITEM_INSETS
    val connected =
      presets.size <= connectedOptionLimit(maxWidth, LIST_ITEM_INSETS) &&
        connectedLabelsFit(presets.map { it.label }, optionsWidth)
    if (!connected) {
      ChoiceRow(
        shapes = shapes,
        title = title,
        options = options,
        selected = selected,
        label = { it?.label ?: valueLabel(value) },
        onSelected = { chosen -> chosen?.let { onSelected(it.value) } },
        description = subtitle,
        enabled = enabled,
        warning = warning,
        optionDescription = { it?.description },
      )
    } else {
      SegmentedListItem(
        shapes = shapes,
        modifier = Modifier.heightIn(min = SettingsRowMinHeight),
        enabled = enabled,
        supportingContent = {
          Column {
            if (subtitle != null) Text(subtitle)
            // An off-preset value is still the value in effect, so the row says what it is, as a
            // second supporting line under the subtitle.
            if (selected == null) Text(valueLabel(value))
            ConnectedChoice(
              title = null,
              options = options,
              selected = selected,
              label = { it?.label.orEmpty() },
              modifier = Modifier.padding(top = CONTROL_GAP),
              description = { preset ->
                val name = preset?.label.orEmpty()
                preset?.description?.let { "$name. $it" } ?: name
              },
              onSelected = { chosen -> chosen?.let { onSelected(it.value) } },
              enabled = enabled,
            )
            if (warning != null) WarningLine(warning, enabled)
          }
        },
        colors = settingsRowColors(),
      ) {
        Text(title)
      }
    }
  }
}

/** A consequence of the current value, in the error role so it reads apart from a subtitle. */
@Composable
private fun WarningLine(text: String, enabled: Boolean = true) {
  val color = MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
  Row(
    modifier = Modifier.padding(top = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    // The icon sits beside the first line, so a wrapped warning still starts at the triangle.
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_warning),
      contentDescription = null,
      tint = color,
      modifier = Modifier.padding(top = WARNING_ICON_INSET).size(WARNING_ICON_SIZE),
    )
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
  }
}

/**
 * An integer range with the value at the title line's end; the write lands when the drag ends. The
 * value snaps to [step], which is also the increment assistive input moves by. A [warning] is a
 * consequence of the current value the user must know about, shown under the slider. A range of at
 * most [TICKED_STOPS] stops shows them as tick marks, so each stop is a target rather than a point
 * on a line, while a longer range reads continuous like the Pixel brightness slider.
 */
@Composable
fun SliderRow(
  shapes: ListItemShapes,
  title: String,
  value: Int,
  range: IntRange,
  onValueChange: (Int) -> Unit,
  format: (Int) -> String = { it.toString() },
  step: Int = 1,
  subtitle: String? = null,
  warning: String? = null,
  enabled: Boolean = true,
) {
  var dragging by remember(value) { mutableIntStateOf(value) }
  val stops: Int = (range.last - range.first) / step + 1
  // The steps stay: they are the increment a screen reader and the arrow keys move by. Only the
  // tick marks go on a long range.
  val sliderState =
    remember(range, step) {
      SliderState(
        value.toFloat(),
        (stops - 2).coerceAtLeast(0),
        range.first.toFloat()..range.last.toFloat(),
      )
    }
  // The state outlives a committed value, so a keyboard or screen reader step keeps its focus and
  // drag state; the committed value is pushed in.
  LaunchedEffect(value) { sliderState.value = value.toFloat() }
  SegmentedListItem(
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    enabled = enabled,
    supportingContent = {
      Column {
        if (subtitle != null) Text(subtitle)
        Slider(
          state = sliderState,
          onValueChange = {
            val snapped: Int = ((it / step).roundToInt() * step).coerceIn(range)
            sliderState.value = snapped.toFloat()
            dragging = snapped
          },
          onValueChangeFinished = { onValueChange(dragging) },
          track = { state ->
            if (stops <= TICKED_STOPS) SliderDefaults.Track(sliderState = state, enabled = enabled)
            else
              SliderDefaults.Track(sliderState = state, enabled = enabled, drawTick = { _, _ -> })
          },
          enabled = enabled,
          // The row's header names the slider; a name on the slider itself lands on a node of its
          // own, which a screen reader then reads as a third stop.
          modifier = Modifier.semantics { stateDescription = format(dragging) },
        )
        if (warning != null) WarningLine(warning, enabled)
      }
    },
    colors = settingsRowColors(),
  ) {
    // The value sits at the title line's end, the Pixel Settings pattern, so no line is spent on
    // it.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(title, modifier = Modifier.weight(1f, fill = false))
      Text(
        text = format(dragging),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

/**
 * A tappable row with no chevron, like a Pixel Settings sub-page row. A [framed] icon sits in the
 * tinted circle the Pixel Settings root list gives its entries.
 */
@Composable
fun LinkRow(
  shapes: ListItemShapes,
  title: String,
  onClick: () -> Unit,
  subtitle: String? = null,
  icon: ImageVector? = null,
  framed: Boolean = false,
  enabled: Boolean = true,
  trailing: (@Composable () -> Unit)? = null,
) {
  SegmentedListItem(
    onClick = onClick,
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    enabled = enabled,
    leadingContent =
      icon?.let { { if (framed) FramedIcon(it) else Icon(it, contentDescription = null) } },
    supportingContent = subtitle?.let { { Text(it) } },
    trailingContent = trailing,
    colors = settingsRowColors(),
  ) {
    Text(title)
  }
}

@Composable
private fun FramedIcon(icon: ImageVector) {
  Box(
    modifier =
      Modifier.size(ICON_FRAME_SIZE)
        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

/** A read-only row: no ripple, no action for a screen reader, the same shape as a link row. */
@Composable
fun InfoRow(shapes: ListItemShapes, title: String, subtitle: String? = null) {
  SegmentedListItem(
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    supportingContent = subtitle?.let { { Text(it) } },
    colors = settingsRowColors(),
  ) {
    Text(title)
  }
}

/** A row with a color swatch; the caller opens its own picker on click. */
@Composable
fun ColorRow(
  shapes: ListItemShapes,
  title: String,
  color: Color,
  onClick: () -> Unit,
  subtitle: String? = null,
  enabled: Boolean = true,
) {
  SegmentedListItem(
    onClick = onClick,
    shapes = shapes,
    modifier = Modifier.heightIn(min = SettingsRowMinHeight),
    enabled = enabled,
    supportingContent = subtitle?.let { { Text(it) } },
    trailingContent = { Box(Modifier.size(SWATCH_SIZE).background(color, CircleShape)) },
    colors = settingsRowColors(),
  ) {
    Text(title)
  }
}

/**
 * Every row sits on the same bright container, checked or disabled or not; only the shapes tell
 * first from middle.
 */
@Composable
fun settingsRowColors(): ListItemColors =
  ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceBright,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
  )

/** The Pixel Settings row minimum, shared with rows built outside the kit. */
val SettingsRowMinHeight: Dp = 72.dp

/** More options than this cannot share one connected row at a readable, tappable size. */
const val MAX_CONNECTED_OPTIONS: Int = 4

/** A connected row keeps at least this many options before a sheet takes over. */
const val MIN_CONNECTED_OPTIONS: Int = 2

/** Width one option needs at font scale 1 to hold a word like "Download" on one line. */
const val MIN_CONNECTED_OPTION_WIDTH_DP: Int = 80

/** A list item's own horizontal insets, taken off the width a kit row is given. */
val LIST_ITEM_INSETS: Dp = 32.dp

/** The padding inside each connected option, on every side. */
private val CONNECTED_OPTION_PADDING: Dp = 8.dp

/**
 * How many options one connected row holds in [optionsWidthDp] of width for the options themselves
 * at the given font scale: each option needs [MIN_CONNECTED_OPTION_WIDTH_DP] scaled by the font, so
 * a row that holds four at scale 1 on a phone holds two at scale 2, and three in a narrow pane. The
 * floor of [MIN_CONNECTED_OPTIONS] overrides the width budget, since a one-option row is no choice
 * at all.
 */
fun maxConnectedOptions(fontScale: Float, optionsWidthDp: Int): Int {
  val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
  val perOption = MIN_CONNECTED_OPTION_WIDTH_DP * scale
  return (optionsWidthDp.coerceAtLeast(0) / perOption)
    .toInt()
    .coerceIn(MIN_CONNECTED_OPTIONS, MAX_CONNECTED_OPTIONS)
}

/**
 * [maxConnectedOptions] for the width the options get inside a layout [maxWidth] wide, less the
 * [insets] the row itself takes on both sides, at the composition's font scale.
 */
@Composable
fun connectedOptionLimit(maxWidth: Dp, insets: Dp = 0.dp): Int =
  maxConnectedOptions(LocalDensity.current.fontScale, (maxWidth - insets).value.toInt())

private val SCREEN_PADDING = 16.dp

/** Pixel Settings measures 24 dp from the edge to the Back circle; the bar's own inset gives 4. */
private val TOP_BAR_BACK_INSET = 20.dp
private val TOP_BAR_TITLE_GAP = 12.dp
private val CONTENT_MAX_WIDTH = 840.dp
private val CARD_CORNER = 20.dp

/**
 * Pixel Settings sets a control 16 dp under the text above it, measured on the Sound page. The
 * button groups take it as padding; a slider's own touch target already holds its track that far
 * below the line above.
 */
val CONTROL_GAP: Dp = 16.dp

/** The most stops a slider marks with ticks; the update interval's ten stops are the coarsest. */
private const val TICKED_STOPS: Int = 10
private val CARD_GAP = 16.dp
private val SWATCH_SIZE = 28.dp
private val ICON_FRAME_SIZE = 40.dp
private val WARNING_ICON_SIZE = 18.dp
/** Centers the icon on the first line of body text. */
private val WARNING_ICON_INSET = 1.dp
private const val DISABLED_ALPHA: Float = 0.38f
