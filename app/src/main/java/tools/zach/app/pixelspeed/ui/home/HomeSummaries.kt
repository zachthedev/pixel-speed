package tools.zach.app.pixelspeed.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import java.util.Locale
import kotlin.math.roundToInt
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
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.format.forStackAndHalves
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.settings.ColorMode
import tools.zach.app.pixelspeed.settings.Layout
import tools.zach.app.pixelspeed.settings.ReadoutSurface
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.ui.overlay.styleForLayout
import tools.zach.app.pixelspeed.ui.settings.PauseDurationLabels
import tools.zach.app.pixelspeed.ui.settings.anchorLabel
import tools.zach.app.pixelspeed.ui.settings.intervalLabel
import tools.zach.app.pixelspeed.ui.settings.obscuringLimit
import tools.zach.app.pixelspeed.ui.settings.styleLabelRes

// Each row's line is decided by a pure function over Settings and formatted by a composable that
// only maps the decision to strings, so every branch is testable on the JVM.

/** Which directions the readout shows. */
enum class ShownDirections {
  BOTH,
  DOWNLOAD_ONLY,
  UPLOAD_ONLY,
  NONE,
}

/** The parts of the Content row's line; [unit] is empty when the unit label is off. */
data class ContentSummaryData(val directions: ShownDirections, val layout: Layout, val unit: String)

fun contentSummaryData(settings: Settings, locale: Locale): ContentSummaryData {
  val content = settings.activeProfile.content
  val directions =
    when {
      content.showDownload && content.showUpload -> ShownDirections.BOTH
      content.showDownload -> ShownDirections.DOWNLOAD_ONLY
      content.showUpload -> ShownDirections.UPLOAD_ONLY
      else -> ShownDirections.NONE
    }
  return ContentSummaryData(
    directions = directions,
    layout = content.layout,
    // The line names the unit the resolved look draws: the two-line stack drops it under the Auto
    // label.
    unit =
      SpeedFormatter(locale)
        .widestUnit(
          if (
            styleForLayout(settings.activeProfile.appearance.style, content.layout) ==
              OverlayStyle.STACK
          ) {
            content.format.forStackAndHalves()
          } else {
            content.format
          }
        ),
  )
}

/** The parts of the Position and size row's line. */
data class PositionSummaryData(val anchor: Anchor)

fun positionSummaryData(settings: Settings): PositionSummaryData =
  PositionSummaryData(settings.activeProfile.position.anchor)

/**
 * The parts of the Appearance row's line. [windowOpacity] travels with its rounded percent because
 * whether it blocks taps is decided against the platform's limit, a float.
 */
data class AppearanceSummaryData(
  val style: OverlayStyle,
  val colorMode: ColorMode,
  val opacityPercent: Int,
  val windowOpacity: Float,
)

fun appearanceSummaryData(settings: Settings): AppearanceSummaryData {
  val appearance = settings.activeProfile.appearance
  return AppearanceSummaryData(
    style = appearance.style,
    colorMode = appearance.colorMode,
    opacityPercent = (appearance.windowOpacity * PERCENT).roundToInt(),
    windowOpacity = appearance.windowOpacity,
  )
}

/** An auto-hide rule the user can turn on. */
enum class HideRule {
  IDLE,
  FULLSCREEN,
  LANDSCAPE,
  BATTERY_SAVER,
}

/** The parts of the Behavior row's line: the rules that are on, in screen order, and the rate. */
data class BehaviorSummaryData(val activeRules: List<HideRule>, val updateIntervalMs: Int)

fun behaviorSummaryData(settings: Settings): BehaviorSummaryData {
  val behavior = settings.activeProfile.behavior
  val rules = buildList {
    if (behavior.hideWhenIdle) add(HideRule.IDLE)
    if (behavior.hideInFullscreen) add(HideRule.FULLSCREEN)
    if (behavior.hideInLandscape) add(HideRule.LANDSCAPE)
    if (behavior.pauseOnBatterySaver) add(HideRule.BATTERY_SAVER)
  }
  return BehaviorSummaryData(activeRules = rules, updateIntervalMs = behavior.updateIntervalMs)
}

/** The parts of the Pause row's line. */
data class PauseSummaryData(val durationsMinutes: List<Int>)

fun pauseSummaryData(settings: Settings): PauseSummaryData =
  PauseSummaryData(settings.activeProfile.pause.pauseDurationsMinutes)

/** The parts of the Profiles row's line; [activeName] is null while there is nothing to switch. */
data class ProfilesSummaryData(val count: Int, val activeName: String?)

fun profilesSummaryData(settings: Settings): ProfilesSummaryData {
  val count = settings.profiles.size
  return ProfilesSummaryData(
    count = count,
    activeName = settings.activeProfile.name.takeIf { count > 1 },
  )
}

/** The parts of the Notification and tile row's line. */
data class NotificationSummaryData(val surface: ReadoutSurface)

fun notificationSummaryData(settings: Settings): NotificationSummaryData =
  NotificationSummaryData(settings.activeProfile.notification.surface)

/** The parts of the System row's line. */
data class SystemSummaryData(val startOnBoot: Boolean, val advancedMode: Boolean)

fun systemSummaryData(settings: Settings): SystemSummaryData =
  SystemSummaryData(startOnBoot = settings.startOnBoot, advancedMode = settings.advancedMode)

/** The parts of a line that carry text, in order; a null or blank part is left out. */
fun summaryParts(vararg parts: String?): List<String> =
  parts.filterNotNull().filter { it.isNotBlank() }

/**
 * The one-line value summary a Home settings link shows under its title, or null for a destination
 * with nothing to summarize.
 */
@Composable
fun homeSummary(destination: NavKey, settings: Settings): String? =
  when (destination) {
    ContentSettings ->
      contentSummary(contentSummaryData(settings, LocalConfiguration.current.locales[0]))
    PositionSettings -> anchorLabel(LocalResources.current, positionSummaryData(settings).anchor)
    AppearanceSettings -> appearanceSummary(appearanceSummaryData(settings))
    BehaviorSettings -> behaviorSummary(behaviorSummaryData(settings))
    PauseSettings -> pauseSummary(pauseSummaryData(settings))
    ProfilesSettings -> profilesSummary(profilesSummaryData(settings))
    NotificationSettings -> notificationSummary(notificationSummaryData(settings))
    SystemSettings -> systemSummary(systemSummaryData(settings))
    Help -> stringResource(R.string.home_summary_help)
    About -> stringResource(R.string.home_summary_about)
    else -> null
  }

/** [parts] joined with the language's list pattern; one part stands alone. */
@Composable
private fun summaryLine(parts: List<String>): String =
  when (parts.size) {
    0 -> ""
    1 -> parts[0]
    2 -> stringResource(R.string.home_summary_two_part, parts[0], parts[1])
    else -> stringResource(R.string.home_summary_three_part, parts[0], parts[1], parts[2])
  }

@Composable
private fun contentSummary(data: ContentSummaryData): String {
  val direction =
    stringResource(
      when (data.directions) {
        ShownDirections.BOTH -> R.string.home_summary_content_both
        ShownDirections.DOWNLOAD_ONLY -> R.string.home_summary_content_download_only
        ShownDirections.UPLOAD_ONLY -> R.string.home_summary_content_upload_only
        ShownDirections.NONE -> R.string.home_summary_content_none
      }
    )
  val layout = stringResource(layoutLabelRes(data.layout))
  return summaryLine(summaryParts(direction, layout, data.unit))
}

private fun layoutLabelRes(layout: Layout): Int =
  when (layout) {
    Layout.ONE_LINE -> R.string.settings_content_layout_one_line
    Layout.TWO_LINES -> R.string.settings_content_layout_two_lines
    Layout.DOWNLOAD_ONLY -> R.string.settings_content_layout_download_only
    Layout.COMBINED -> R.string.settings_content_layout_combined
  }

@Composable
private fun appearanceSummary(data: AppearanceSummaryData): String {
  val style = stringResource(styleLabelRes(data.style))
  val colorMode = stringResource(colorModeRes(data.colorMode))
  val percent =
    stringResource(R.string.settings_appearance_window_opacity_value, data.opacityPercent)
  // An opacity past the touch-obscuring limit is a dead spot on screen wherever it was set, so
  // the line says so even when the Appearance screen never showed the warning.
  val opacity =
    stringResource(
      if (data.windowOpacity > obscuringLimit())
        R.string.home_summary_appearance_opacity_blocks_taps
      else R.string.home_summary_appearance_opacity,
      percent,
    )
  return summaryLine(summaryParts(style, colorMode, opacity))
}

private fun colorModeRes(mode: ColorMode): Int =
  when (mode) {
    ColorMode.DYNAMIC -> R.string.settings_appearance_color_mode_dynamic
    ColorMode.CUSTOM -> R.string.settings_appearance_color_mode_custom
    ColorMode.MONOCHROME_LIGHT -> R.string.settings_appearance_color_mode_light
    ColorMode.MONOCHROME_DARK -> R.string.settings_appearance_color_mode_dark
  }

@Composable
private fun behaviorSummary(data: BehaviorSummaryData): String {
  val rules =
    if (data.activeRules.isEmpty()) {
      stringResource(R.string.home_summary_behavior_no_rules)
    } else {
      data.activeRules.map { rule -> stringResource(hideRuleRes(rule)) }.joinToString(", ")
    }
  val updates =
    stringResource(R.string.home_summary_behavior_updates, intervalLabel(data.updateIntervalMs))
  return summaryLine(summaryParts(rules, updates))
}

private fun hideRuleRes(rule: HideRule): Int =
  when (rule) {
    HideRule.IDLE -> R.string.home_summary_behavior_rule_idle
    HideRule.FULLSCREEN -> R.string.home_summary_behavior_rule_fullscreen
    HideRule.LANDSCAPE -> R.string.home_summary_behavior_rule_landscape
    HideRule.BATTERY_SAVER -> R.string.home_summary_behavior_rule_battery_saver
  }

@Composable
private fun pauseSummary(data: PauseSummaryData): String =
  data.durationsMinutes.map { minutes -> pauseDurationLabel(minutes) }.joinToString(", ")

@Composable
private fun pauseDurationLabel(minutes: Int): String {
  val resource = PauseDurationLabels.resourceFor(minutes)
  val pluralsRes =
    when (resource.unit) {
      PauseDurationLabels.PauseUnit.MINUTES -> R.plurals.home_duration_minutes
      PauseDurationLabels.PauseUnit.HOURS -> R.plurals.home_duration_hours
      PauseDurationLabels.PauseUnit.DAYS -> R.plurals.home_duration_days
    }
  return pluralStringResource(pluralsRes, resource.quantity, resource.quantity)
}

@Composable
private fun profilesSummary(data: ProfilesSummaryData): String {
  val count = pluralStringResource(R.plurals.home_summary_profiles_count, data.count, data.count)
  val active = data.activeName?.let { stringResource(R.string.home_summary_profiles_active, it) }
  return summaryLine(summaryParts(count, active))
}

@Composable
private fun notificationSummary(data: NotificationSummaryData): String =
  stringResource(
    when (data.surface) {
      ReadoutSurface.OVERLAY -> R.string.settings_notification_surface_overlay
      ReadoutSurface.LIVE_UPDATE -> R.string.settings_notification_surface_live_update
      ReadoutSurface.BOTH -> R.string.settings_notification_surface_both
    }
  )

@Composable
private fun systemSummary(data: SystemSummaryData): String {
  val start =
    stringResource(
      if (data.startOnBoot) R.string.home_summary_system_starts_on_boot
      else R.string.home_summary_system_manual_start
    )
  val mode =
    if (data.advancedMode) stringResource(R.string.home_summary_system_advanced_mode) else null
  return summaryLine(summaryParts(start, mode))
}

private const val PERCENT: Float = 100f
