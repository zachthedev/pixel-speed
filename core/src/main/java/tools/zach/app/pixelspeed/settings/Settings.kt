package tools.zach.app.pixelspeed.settings

import kotlinx.serialization.Serializable
import tools.zach.app.pixelspeed.format.FormatOptions
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.overlay.LandscapeAnchorMode
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.overlay.StatusBarFit
import tools.zach.app.pixelspeed.sampling.CounterSourceKind
import tools.zach.app.pixelspeed.sampling.Smoothing

/** What the overlay shows and how it reads. */
@Serializable
data class ContentSettings(
  val showDownload: Boolean = true,
  val showUpload: Boolean = true,
  val layout: Layout = Layout.ONE_LINE,
  val directionOrder: DirectionOrder = DirectionOrder.DOWNLOAD_FIRST,
  val directionMarker: DirectionMarker = DirectionMarker.ARROWS,
  val format: FormatOptions = FormatOptions(),
  val showNetworkGlyph: Boolean = false,
)

/** Where the window sits and how large it is. */
@Serializable
data class PositionSettings(
  val anchor: Anchor = Anchor.STATUS_BAR_CUTOUT,
  val offsetXdp: Int = 0,
  val offsetYdp: Int = 0,
  val customXdp: Int = 0,
  val customYdp: Int = 0,
  val landscapeAnchorMode: LandscapeAnchorMode = LandscapeAnchorMode.SAME,
  val landscapeAnchor: Anchor = Anchor.TOP_END,
  val avoidCutout: Boolean = true,
  val textSizeSp: Int = 14,
  val chipPaddingDp: Int = 12,
  val minWidthDp: Int = 0,
  /**
   * The pill follows the bar's real icons through the accessibility service. This is the user's
   * intent; the enabled service is the permission, and a disabled service leaves the placement to
   * the clock and icon estimates regardless of this flag.
   */
  val sideFlip: Boolean = false,
)

/** The overlay's look: style, color, and type. */
@Serializable
data class AppearanceSettings(
  val style: OverlayStyle = OverlayStyle.CHIP,
  val statusBarFit: StatusBarFit = StatusBarFit.AUTO,
  /** Under the Auto fit, the slim ribbon stands in when neither side of the hole holds a look. */
  val ribbonFallback: Boolean = false,
  val colorMode: ColorMode = ColorMode.DYNAMIC,
  val backgroundColor: Int? = null,
  val textColor: Int? = null,
  val windowOpacity: Float = 0.8f,
  val cornerRadiusDp: Int = 999,
  val fontFamily: FontFamilyChoice = FontFamilyChoice.SYSTEM,
  val fontWeight: FontWeightChoice = FontWeightChoice.MEDIUM,
  val colorByDirection: Boolean = false,
  val thresholdHighlight: Boolean = false,
  val thresholdBytesPerSecond: Long = 1_250_000,
  val blurBehind: Boolean = false,
)

/** How the meter samples and when the overlay hides itself. */
@Serializable
data class BehaviorSettings(
  val updateIntervalMs: Int = 1000,
  val smoothing: Smoothing = Smoothing.NONE,
  val hideWhenIdle: Boolean = false,
  val idleThresholdBytesPerSecond: Long = 1024,
  val idleDelayMs: Int = 5000,
  val offlineMode: OfflineMode = OfflineMode.DASHES,
  val hideInFullscreen: Boolean = false,
  val hideInLandscape: Boolean = false,
  val pauseOnBatterySaver: Boolean = false,
)

/** The pause durations offered from each pause surface. */
@Serializable
data class PauseSettings(
  val pauseDurationsMinutes: List<Int> = listOf(5, 15, 60),
  val tilePauseDurationMinutes: Int? = null,
  val notificationPauseDurationMinutes: Int? = 15,
)

/** Where a paused-state readout surfaces besides the overlay. */
@Serializable
data class NotificationSettings(
  val notificationShowsSpeed: Boolean = false,
  val surface: ReadoutSurface = ReadoutSurface.OVERLAY,
)

/** A named configuration of the overlay. Every profile is independently switchable. */
@Serializable
data class Profile(
  val id: String = "default",
  val name: String = "Default",
  val content: ContentSettings = ContentSettings(),
  val position: PositionSettings = PositionSettings(),
  val appearance: AppearanceSettings = AppearanceSettings(),
  val behavior: BehaviorSettings = BehaviorSettings(),
  val pause: PauseSettings = PauseSettings(),
  val notification: NotificationSettings = NotificationSettings(),
  val counterSource: CounterSourceKind = CounterSourceKind.PHYSICAL_INTERFACES,
)

/** The root schema persisted by the settings store. */
@Serializable
data class Settings(
  val schemaVersion: Int = SettingsMigrations.CURRENT_VERSION,
  val enabled: Boolean = false,
  val startOnBoot: Boolean = true,
  val paused: Boolean = false,
  val pausedUntilEpochMillis: Long? = null,
  val profiles: List<Profile> = listOf(SettingsDefaults.defaultProfile),
  val activeProfileId: String = SettingsDefaults.defaultProfile.id,
  val automationIntents: Boolean = false,
  val onboardingCompleted: Boolean = false,
  val onboardingStep: Int = 0,
  val autoRevokeNoticeDismissed: Boolean = false,
  val advancedMode: Boolean = false,
) {
  /**
   * The profile named by [activeProfileId], the first profile when nothing matches, or
   * [SettingsDefaults.defaultProfile] when [profiles] is empty.
   */
  val activeProfile: Profile
    get() =
      profiles.firstOrNull { it.id == activeProfileId }
        ?: profiles.firstOrNull()
        ?: SettingsDefaults.defaultProfile
}
