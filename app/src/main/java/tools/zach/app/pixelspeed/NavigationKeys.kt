package tools.zach.app.pixelspeed

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object ContentSettings : NavKey

@Serializable data object PositionSettings : NavKey

@Serializable data object AppearanceSettings : NavKey

@Serializable data object BehaviorSettings : NavKey

@Serializable data object PauseSettings : NavKey

@Serializable data object ProfilesSettings : NavKey

@Serializable data object NotificationSettings : NavKey

@Serializable data object SystemSettings : NavKey

@Serializable data object Onboarding : NavKey

@Serializable data object Help : NavKey

@Serializable data object HelpOverlayHidden : NavKey

@Serializable data object HelpAppInput : NavKey

@Serializable data object HelpSystemNotice : NavKey

@Serializable data object HelpReportProblem : NavKey

@Serializable data object About : NavKey

@Serializable data object OpenSourceLicenses : NavKey

@Serializable data object WhatsNew : NavKey

@Serializable data object Privacy : NavKey
