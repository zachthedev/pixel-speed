package tools.zach.app.pixelspeed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import tools.zach.app.pixelspeed.ui.about.AboutScreen
import tools.zach.app.pixelspeed.ui.about.LicensesScreen
import tools.zach.app.pixelspeed.ui.about.PrivacyScreen
import tools.zach.app.pixelspeed.ui.about.WhatsNewScreen
import tools.zach.app.pixelspeed.ui.help.HelpAppInputScreen
import tools.zach.app.pixelspeed.ui.help.HelpOverlayHiddenScreen
import tools.zach.app.pixelspeed.ui.help.HelpReportProblemScreen
import tools.zach.app.pixelspeed.ui.help.HelpScreen
import tools.zach.app.pixelspeed.ui.help.HelpSystemNoticeScreen
import tools.zach.app.pixelspeed.ui.home.HomeScreen
import tools.zach.app.pixelspeed.ui.onboarding.OnboardingScreen
import tools.zach.app.pixelspeed.ui.settings.AppearanceScreen
import tools.zach.app.pixelspeed.ui.settings.BehaviorScreen
import tools.zach.app.pixelspeed.ui.settings.ContentScreen
import tools.zach.app.pixelspeed.ui.settings.LocalTwoPane
import tools.zach.app.pixelspeed.ui.settings.NotificationScreen
import tools.zach.app.pixelspeed.ui.settings.PauseScreen
import tools.zach.app.pixelspeed.ui.settings.PositionScreen
import tools.zach.app.pixelspeed.ui.settings.ProfilesScreen
import tools.zach.app.pixelspeed.ui.settings.SettingsViewModel
import tools.zach.app.pixelspeed.ui.settings.SystemScreen

/** [forceKeepAlive] is the debug-only switch that shows the vendor battery step on any device. */
@Composable
fun AppNavigation(
  forceKeepAlive: Boolean = false,
  editViewModel: SettingsViewModel = hiltViewModel(),
) {
  val settings by editViewModel.settings.collectAsStateWithLifecycle()
  // The splash screen stays up until the settings load, so nothing draws before this point.
  val loaded = settings ?: return
  val backStack = rememberNavBackStack(if (loaded.onboardingCompleted) Home else Onboarding)
  // One directive decides both the scene's pane count and whether screens show a Back button.
  val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
  val twoPane = directive.maxHorizontalPartitions > 1
  // Beside the list, the detail pane shows the Content settings until a choice, so Content is
  // never pushed there, and a first-level choice replaces the one on show; a page opened from
  // inside a detail (Help's pages, About's pages) stacks on its parent so Back returns to it.
  val navigate: (NavKey) -> Unit = { key ->
    val last = backStack.lastOrNull()
    when {
      last == key -> Unit
      twoPane && key == ContentSettings -> while (backStack.size > 1) backStack.removeLastOrNull()
      twoPane && last != null && last in listChoices && key in listChoices ->
        backStack[backStack.lastIndex] = key
      else -> backStack.add(key)
    }
  }
  val back: () -> Unit = { backStack.removeLastOrNull() }
  val editing by editViewModel.editMode.collectAsStateWithLifecycle()
  // Back pops one entry: a detail returns to the placeholder, and only Home leaves the app.
  val listDetail =
    rememberListDetailSceneStrategy<NavKey>(
      backNavigationBehavior = BackNavigationBehavior.PopLatest,
      directive = directive,
    )
  val detail = ListDetailSceneStrategy.detailPane()

  CompositionLocalProvider(LocalTwoPane provides twoPane) {
    Box(Modifier.fillMaxSize()) {
      NavDisplay(
        backStack = backStack,
        onBack = back,
        sceneStrategies = listOf(listDetail),
        entryProvider =
          entryProvider {
            entry<Onboarding> {
              OnboardingScreen(
                forceKeepAlive = forceKeepAlive,
                onFinished = {
                  backStack.clear()
                  backStack.add(Home)
                },
              )
            }
            // With nothing chosen, the detail pane shows the Content settings.
            entry<Home>(
              metadata =
                ListDetailSceneStrategy.listPane(
                  detailPlaceholder = { ContentScreen(onBack = back) }
                )
            ) {
              HomeScreen(initialSettings = loaded, onNavigate = navigate)
            }
            entry<ContentSettings>(metadata = detail) { ContentScreen(onBack = back) }
            entry<PositionSettings>(metadata = detail) { PositionScreen(onBack = back) }
            entry<AppearanceSettings>(metadata = detail) { AppearanceScreen(onBack = back) }
            entry<BehaviorSettings>(metadata = detail) { BehaviorScreen(onBack = back) }
            entry<PauseSettings>(metadata = detail) { PauseScreen(onBack = back) }
            entry<ProfilesSettings>(metadata = detail) { ProfilesScreen(onBack = back) }
            entry<NotificationSettings>(metadata = detail) { NotificationScreen(onBack = back) }
            entry<SystemSettings>(metadata = detail) { SystemScreen(onBack = back) }
            entry<Help>(metadata = detail) { HelpScreen(onBack = back, onNavigate = navigate) }
            entry<HelpOverlayHidden>(metadata = detail) { HelpOverlayHiddenScreen(onBack = back) }
            entry<HelpAppInput>(metadata = detail) { HelpAppInputScreen(onBack = back) }
            entry<HelpSystemNotice>(metadata = detail) { HelpSystemNoticeScreen(onBack = back) }
            entry<HelpReportProblem>(metadata = detail) { HelpReportProblemScreen(onBack = back) }
            entry<About>(metadata = detail) { AboutScreen(onBack = back, onNavigate = navigate) }
            entry<OpenSourceLicenses>(metadata = detail) { LicensesScreen(onBack = back) }
            entry<WhatsNew>(metadata = detail) { WhatsNewScreen(onBack = back) }
            entry<Privacy>(metadata = detail) { PrivacyScreen(onBack = back) }
          },
      )
      // The floating Done pill lives in the app, so the overlay stays a plain draggable pill.
      if (editing) {
        ExtendedFloatingActionButton(
          onClick = editViewModel::stopEditMode,
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
        ) {
          Text(stringResource(R.string.edit_mode_done))
        }
      }
    }
  }
}

/** The keys Home's list opens directly; each replaces the other beside the list. */
private val listChoices: Set<NavKey> =
  setOf(
    PositionSettings,
    AppearanceSettings,
    BehaviorSettings,
    PauseSettings,
    ProfilesSettings,
    NotificationSettings,
    SystemSettings,
    Help,
    About,
  )
