package tools.zach.app.pixelspeed.ui.onboarding

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.overlay.displayContext
import tools.zach.app.pixelspeed.overlay.displayGeometry
import tools.zach.app.pixelspeed.service.SpeedTileRequest
import tools.zach.app.pixelspeed.service.accessibilitySettingsIntent
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.ui.settings.contentWidthCap
import tools.zach.app.pixelspeed.ui.settings.settingsRowColors
import tools.zach.app.pixelspeed.ui.settings.settingsRowShapes

/**
 * The first-run flow: one page per [OnboardingStep], with the index persisted so a killed process
 * resumes on the same page. [forceKeepAlive] shows the vendor battery step on any device.
 */
@Composable
fun OnboardingScreen(
  forceKeepAlive: Boolean,
  onFinished: () -> Unit,
  viewModel: OnboardingViewModel = hiltViewModel(),
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle()
  val batteryExempt by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
  val sideFlipEnabled by viewModel.sideFlipEnabled.collectAsStateWithLifecycle()
  val measuredBar by viewModel.measuredBar.collectAsStateWithLifecycle()
  val loaded = settings ?: return
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val geometry: DisplayGeometry =
    remember(context, configuration) {
      val display = displayContext(context)
      displayGeometry(display, display.getSystemService(WindowManager::class.java))
    }
  val hasCenteredHole: Boolean =
    remember(geometry) { hasCenteredTopHole(geometry.cutoutRects, geometry.widthDp) }
  val steps =
    remember(forceKeepAlive, hasCenteredHole) {
      OnboardingSteps.forDevice(
        Build.VERSION.SDK_INT,
        Build.MANUFACTURER,
        forceKeepAlive,
        deviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure,
        hasCenteredHole = hasCenteredHole,
      )
    }
  var index by rememberSaveable {
    mutableIntStateOf(OnboardingSteps.resumeIndex(loaded.onboardingStep, steps))
  }
  // The sideload hint needs to know the user left for the settings page and came back empty-handed.
  var openedSystemPage by rememberSaveable { mutableStateOf(false) }
  var returnedWithoutGrant by rememberSaveable { mutableStateOf(false) }
  var tileAdded by rememberSaveable { mutableStateOf(false) }
  var tileDeclined by rememberSaveable { mutableStateOf(false) }
  // A device with no overlay settings page still has to let the user through to Home.
  var settingsPageMissing by rememberSaveable { mutableStateOf(false) }
  val packageSource = remember { installPackageSource(context) }

  val goTo: (Int) -> Unit = { target ->
    index = target
    viewModel.saveStep(target)
  }
  val advance: () -> Unit = { if (index < steps.lastIndex) goTo(index + 1) }

  LifecycleResumeEffect(Unit) {
    viewModel.refreshGrants()
    if (openedSystemPage) {
      openedSystemPage = false
      val granted = viewModel.hasOverlayPermission.value
      returnedWithoutGrant = !granted
      // The grant is the step's whole job, so a return with it in hand moves on the way the
      // notification and tile prompts do once they succeed.
      if (granted && steps.getOrNull(index) == OnboardingStep.OVERLAY) advance()
      if (viewModel.sideFlipEnabled.value && steps.getOrNull(index) == OnboardingStep.SIDE_FLIP) {
        viewModel.enableSideFlip()
        advance()
      }
    }
    onPauseOrDispose {}
  }
  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { advance() }
  val openSystemPage: (Intent) -> Unit = { intent ->
    openedSystemPage = true
    try {
      context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      AppLog.w("No activity for ${intent.action}", e)
      openedSystemPage = false
      settingsPageMissing = true
    }
  }

  OnboardingContent(
    steps = steps,
    index = index,
    geometry = geometry,
    profile = loaded.activeProfile,
    hasOverlayPermission = hasOverlayPermission,
    batteryExempt = batteryExempt,
    sideFlipEnabled = sideFlipEnabled,
    measuredBar = measuredBar.takeIf { sideFlipEnabled },
    tileAdded = tileAdded,
    tileDeclined = tileDeclined,
    settingsPageMissing = settingsPageMissing,
    showRestrictedSettingsCard =
      RestrictedSettingsHint.applies(
        sdkInt = Build.VERSION.SDK_INT,
        packageSource = packageSource,
        granted = false,
        returnedFromSettings = returnedWithoutGrant,
      ),
    actions =
      OnboardingActions(
        onBack = { goTo(index - 1) },
        onSkip = advance,
        onNext = advance,
        onOpenOverlaySettings = { openSystemPage(overlaySettingsIntent(context)) },
        onRequestNotifications = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            advance()
          }
        },
        onRequestTile = {
          val prompted =
            SpeedTileRequest.request(context) { added ->
              tileAdded = added
              tileDeclined = !added
              if (added) advance()
            }
          if (!prompted) advance()
        },
        onRequestBatteryExemption = { openSystemPage(batteryExemptionIntent(context)) },
        onOpenSideFlipSettings = { openSystemPage(accessibilitySettingsIntent()) },
        onSideFlipNext = {
          viewModel.enableSideFlip()
          advance()
        },
        onStart = { viewModel.start(onFinished) },
      ),
  )
}

/** The callbacks the bottom bar and [PrimaryAction] fire. */
data class OnboardingActions(
  val onBack: () -> Unit,
  val onSkip: () -> Unit,
  val onNext: () -> Unit,
  val onOpenOverlaySettings: () -> Unit,
  val onRequestNotifications: () -> Unit,
  val onRequestTile: () -> Unit,
  val onRequestBatteryExemption: () -> Unit,
  val onOpenSideFlipSettings: () -> Unit,
  /** Next on the side flip step with its service already on: the intent is recorded too. */
  val onSideFlipNext: () -> Unit,
  val onStart: () -> Unit,
) {
  companion object {
    /** Every action as a no-op, for previews that never interact. */
    val NoOps: OnboardingActions =
      OnboardingActions(
        onBack = {},
        onSkip = {},
        onNext = {},
        onOpenOverlaySettings = {},
        onRequestNotifications = {},
        onRequestTile = {},
        onRequestBatteryExemption = {},
        onOpenSideFlipSettings = {},
        onSideFlipNext = {},
        onStart = {},
      )
  }
}

@Composable
fun OnboardingContent(
  steps: List<OnboardingStep>,
  index: Int,
  geometry: DisplayGeometry,
  profile: Profile,
  hasOverlayPermission: Boolean,
  batteryExempt: Boolean,
  sideFlipEnabled: Boolean,
  tileAdded: Boolean,
  tileDeclined: Boolean,
  settingsPageMissing: Boolean,
  measuredBar: MeasuredBar? = null,
  showRestrictedSettingsCard: Boolean,
  actions: OnboardingActions,
) {
  require(steps.isNotEmpty()) { "Onboarding needs at least one step" }
  val safeIndex = index.coerceIn(0, steps.lastIndex)
  val step = steps[safeIndex]
  Scaffold(
    bottomBar = {
      // The cap sets the row's width; the fill after it spreads the buttons across that width.
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .wrapContentWidth()
            .contentWidthCap()
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (safeIndex > 0) {
          TextButton(onClick = actions.onBack) {
            Text(stringResource(R.string.onboarding_action_back))
          }
        }
        Spacer(Modifier.weight(1f))
        if (OnboardingSteps.isSkippable(step)) {
          TextButton(onClick = actions.onSkip) {
            Text(stringResource(R.string.onboarding_action_skip))
          }
          Spacer(Modifier.width(8.dp))
        }
        PrimaryAction(
          step = step,
          hasOverlayPermission = hasOverlayPermission || settingsPageMissing,
          batteryExempt = batteryExempt,
          sideFlipEnabled = sideFlipEnabled,
          onNext = actions.onNext,
          onOpenOverlaySettings = actions.onOpenOverlaySettings,
          onRequestNotifications = actions.onRequestNotifications,
          onRequestTile = actions.onRequestTile,
          onRequestBatteryExemption = actions.onRequestBatteryExemption,
          onOpenSideFlipSettings = actions.onOpenSideFlipSettings,
          onSideFlipNext = actions.onSideFlipNext,
          onStart = actions.onStart,
        )
      }
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .wrapContentWidth()
          .contentWidthCap()
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      StepHeader(index = safeIndex, count = steps.size)
      val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
      val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
      val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
      AnimatedContent(
        targetState = safeIndex,
        transitionSpec = {
          if (reduceMotion) {
            EnterTransition.None togetherWith ExitTransition.None
          } else {
            val forward = targetState > initialState
            val enter =
              slideInHorizontally(spatialSpec) { width -> if (forward) width else -width } +
                fadeIn(effectsSpec)
            val exit =
              slideOutHorizontally(spatialSpec) { width -> if (forward) -width else width } +
                fadeOut(effectsSpec)
            enter togetherWith exit
          }
        },
        label = "onboarding-step",
      ) { targetIndex ->
        StepPage(
          step = steps[targetIndex],
          geometry = geometry,
          profile = profile,
          hasOverlayPermission = hasOverlayPermission,
          batteryExempt = batteryExempt,
          sideFlipEnabled = sideFlipEnabled,
          measuredBar = measuredBar,
          tileAdded = tileAdded,
          tileDeclined = tileDeclined,
          settingsPageMissing = settingsPageMissing,
          showRestrictedSettingsCard = showRestrictedSettingsCard,
        )
      }
    }
  }
}

/**
 * What a row leads with: a tinted glyph for a fact, a number for an action to take, a check for one
 * taken.
 */
private sealed interface RowLead {
  data class Glyph(val res: Int) : RowLead

  data class Step(val number: Int) : RowLead

  data object Done : RowLead
}

private data class FactRow(val lead: RowLead, val title: String, val subtitle: String? = null)

/** One step's page under the header: the hero, the headline, then the step's rows and status. */
@Composable
private fun StepPage(
  step: OnboardingStep,
  geometry: DisplayGeometry,
  profile: Profile,
  hasOverlayPermission: Boolean,
  batteryExempt: Boolean,
  sideFlipEnabled: Boolean,
  tileAdded: Boolean,
  tileDeclined: Boolean,
  settingsPageMissing: Boolean,
  measuredBar: MeasuredBar? = null,
  showRestrictedSettingsCard: Boolean,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    StepHero(step.icon())
    Text(stringResource(step.titleRes()), style = MaterialTheme.typography.headlineLarge)
    when (step) {
      OnboardingStep.WELCOME -> {
        Body(stringResource(R.string.onboarding_welcome_body))
        FactCard(
          listOf(
            FactRow(
              RowLead.Glyph(R.drawable.ic_memory),
              stringResource(R.string.onboarding_welcome_fact_counters),
            ),
            FactRow(
              RowLead.Glyph(R.drawable.ic_wifi_off),
              stringResource(R.string.onboarding_welcome_fact_no_network),
            ),
            FactRow(
              RowLead.Glyph(R.drawable.ic_visibility_off),
              stringResource(R.string.onboarding_welcome_fact_private),
            ),
          )
        )
      }
      OnboardingStep.OVERLAY ->
        OverlayStepBody(hasOverlayPermission, settingsPageMissing, showRestrictedSettingsCard)
      OnboardingStep.NOTIFICATIONS -> {
        Body(stringResource(R.string.onboarding_notifications_body))
        FactCard(
          listOf(
            FactRow(
              RowLead.Glyph(R.drawable.ic_pause_circle),
              stringResource(R.string.onboarding_notifications_fact_controls),
            ),
            FactRow(
              RowLead.Glyph(R.drawable.ic_notifications_off),
              stringResource(R.string.onboarding_notifications_fact_silent),
            ),
            FactRow(
              RowLead.Glyph(R.drawable.ic_swipe),
              stringResource(R.string.onboarding_notifications_fact_swipe),
              stringResource(R.string.onboarding_notifications_fact_swipe_sub),
            ),
          )
        )
      }
      OnboardingStep.TILE -> TileStepBody(tileAdded, tileDeclined)
      OnboardingStep.KEEP_ALIVE -> {
        Body(
          stringResource(
            R.string.onboarding_keep_alive_body,
            Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
          )
        )
        FactCard(
          listOf(
            FactRow(
              RowLead.Glyph(R.drawable.ic_battery_alert),
              stringResource(R.string.onboarding_keep_alive_fact_allow),
            ),
            FactRow(
              RowLead.Glyph(R.drawable.ic_bolt),
              stringResource(R.string.onboarding_keep_alive_fact_cost),
              stringResource(R.string.onboarding_keep_alive_fact_cost_sub),
            ),
          )
        )
        if (batteryExempt) StatusChip(stringResource(R.string.onboarding_keep_alive_granted))
      }
      OnboardingStep.SYSTEM_NOTICE -> {
        NoticeCard()
        FactCard(
          listOf(
            FactRow(RowLead.Step(1), stringResource(R.string.onboarding_notice_step_1)),
            FactRow(
              RowLead.Step(2),
              stringResource(R.string.onboarding_notice_step_2),
              stringResource(R.string.onboarding_notice_step_2_sub),
            ),
            FactRow(RowLead.Step(3), stringResource(R.string.onboarding_notice_step_3)),
          )
        )
        InfoCard(stringResource(R.string.onboarding_notice_info))
      }
      OnboardingStep.SIDE_FLIP ->
        SideFlipStepBody(geometry = geometry, profile = profile, sideFlipEnabled = sideFlipEnabled)
      OnboardingStep.START -> {
        Body(stringResource(R.string.onboarding_start_body))
        StartPreviewCard(geometry = geometry, profile = profile, measuredBar = measuredBar)
      }
    }
  }
}

@Composable
private fun SideFlipStepBody(
  geometry: DisplayGeometry,
  profile: Profile,
  sideFlipEnabled: Boolean,
) {
  Body(stringResource(R.string.onboarding_side_flip_body))
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(SKETCH_CARD_INSET),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SideFlipFrame(
      geometry = geometry,
      profile = profile,
      notificationIcons = 1,
      measuredBar = null,
      caption = stringResource(R.string.onboarding_side_flip_frame_one),
    )
    SideFlipFrame(
      geometry = geometry,
      profile = profile,
      notificationIcons = 4,
      measuredBar = measuredBarPastHole(geometry, sketchEndIconsDp()),
      caption = stringResource(R.string.onboarding_side_flip_frame_two),
    )
  }
  FactCard(
    listOf(
      FactRow(
        RowLead.Glyph(R.drawable.ic_visibility),
        stringResource(R.string.onboarding_side_flip_fact_reads),
        stringResource(R.string.onboarding_side_flip_fact_reads_sub),
      ),
      FactRow(
        RowLead.Glyph(R.drawable.ic_block),
        stringResource(R.string.onboarding_side_flip_fact_local),
      ),
      FactRow(
        RowLead.Glyph(R.drawable.ic_smartphone),
        stringResource(R.string.onboarding_side_flip_fact_off),
      ),
    )
  )
  InfoCard(stringResource(R.string.onboarding_side_flip_info))
  if (sideFlipEnabled) StatusChip(stringResource(R.string.onboarding_side_flip_on))
}

@Composable
private fun SideFlipFrame(
  geometry: DisplayGeometry,
  profile: Profile,
  notificationIcons: Int,
  measuredBar: MeasuredBar?,
  caption: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    StatusBarSketch(
      geometry = geometry,
      profile = profile,
      notificationIcons = notificationIcons,
      measuredBar = measuredBar,
      contentDescription = stringResource(R.string.onboarding_start_preview),
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      caption,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun OverlayStepBody(
  granted: Boolean,
  settingsPageMissing: Boolean,
  showRestrictedSettingsCard: Boolean,
) {
  if (granted) {
    StatusChip(stringResource(R.string.onboarding_overlay_granted))
    FactCard(
      listOf(
        FactRow(RowLead.Done, stringResource(R.string.onboarding_overlay_done_1)),
        FactRow(RowLead.Done, stringResource(R.string.onboarding_overlay_done_2)),
        FactRow(RowLead.Done, stringResource(R.string.onboarding_overlay_done_3)),
      )
    )
    return
  }
  if (settingsPageMissing) {
    // The primary action reads Next here, so the rows that name Open settings would mislead.
    StatusLine(stringResource(R.string.onboarding_overlay_no_page))
    return
  }
  FactCard(
    listOf(
      FactRow(RowLead.Step(1), stringResource(R.string.onboarding_overlay_step_1)),
      FactRow(
        RowLead.Step(2),
        stringResource(R.string.onboarding_overlay_step_2),
        stringResource(R.string.onboarding_overlay_step_2_samsung),
      ),
      FactRow(RowLead.Step(3), stringResource(R.string.onboarding_overlay_step_3)),
    )
  )
  if (showRestrictedSettingsCard) RestrictedSettingsCard()
}

@Composable
private fun TileStepBody(tileAdded: Boolean, tileDeclined: Boolean) {
  TilePairHero()
  FactCard(
    listOf(
      FactRow(
        RowLead.Glyph(R.drawable.ic_touch_app),
        stringResource(R.string.onboarding_tile_fact_tap),
      ),
      FactRow(
        RowLead.Glyph(R.drawable.ic_account_box),
        stringResource(R.string.onboarding_tile_fact_profiles),
      ),
    )
  )
  when {
    tileAdded -> StatusChip(stringResource(R.string.onboarding_tile_added))
    tileDeclined -> InfoCard(stringResource(R.string.onboarding_tile_not_added))
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
      Text(
        stringResource(R.string.onboarding_tile_prompt_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    else -> Body(stringResource(R.string.onboarding_tile_body_manual))
  }
}

@Composable
private fun Body(text: String) {
  Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** The step's icon on an Expressive shape, the one large element on every page. */
@Composable
private fun StepHero(iconRes: Int) {
  Box(
    modifier =
      Modifier.size(HERO_SIZE)
        .clip(MaterialShapes.Cookie4Sided.toShape())
        .background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.size(HERO_ICON_SIZE),
    )
  }
}

/**
 * The settings screens' segmented rows on a container, so a page reads like the rest of the app.
 */
@Composable
private fun FactCard(rows: List<FactRow>) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(CARD_INSET),
    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
  ) {
    rows.forEachIndexed { index, row ->
      SegmentedListItem(
        shapes = settingsRowShapes(index = index, count = rows.size),
        colors = settingsRowColors(),
        leadingContent = { RowLeading(row.lead) },
        supportingContent = row.subtitle?.let { { Text(it) } },
      ) {
        Text(row.title)
      }
    }
  }
}

@Composable
private fun RowLeading(lead: RowLead) {
  when (lead) {
    is RowLead.Glyph ->
      Icon(
        painter = painterResource(lead.res),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    is RowLead.Step ->
      LeadBadge { Text(lead.number.toString(), style = MaterialTheme.typography.labelLarge) }
    RowLead.Done ->
      LeadBadge {
        Icon(
          painter = painterResource(R.drawable.ic_check),
          contentDescription = null,
          modifier = Modifier.size(BADGE_ICON_SIZE),
        )
      }
  }
}

@Composable
private fun LeadBadge(content: @Composable () -> Unit) {
  Box(
    modifier =
      Modifier.size(BADGE_SIZE)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(
      LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer,
      content = content,
    )
  }
}

/** A granted permission or an added tile, as one pill under the headline. */
@Composable
private fun StatusChip(text: String) {
  Row(
    modifier =
      Modifier.clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer)
        .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_check),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.size(BADGE_ICON_SIZE),
    )
    Text(
      text,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

/** A note beside the rows, quieter than a row and never tappable. */
@Composable
private fun InfoCard(text: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_info),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Two drawn Quick Settings tiles, so the user knows what to look for; one spoken node. */
@Composable
private fun TilePairHero() {
  val description = stringResource(R.string.onboarding_tile_hero_description)
  FlowRow(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(20.dp)
        .clearAndSetSemantics { contentDescription = description },
    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DrawnTile(R.drawable.ic_wifi, stringResource(R.string.overlay_transport_wifi), active = false)
    DrawnTile(R.drawable.ic_readout, stringResource(R.string.app_name), active = true)
  }
}

@Composable
private fun DrawnTile(iconRes: Int, label: String, active: Boolean) {
  val container =
    if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceBright
  val content =
    if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
  // The tile grows with its label, so a large font scale widens it instead of clipping the name.
  Row(
    modifier =
      Modifier.widthIn(min = TILE_WIDTH)
        .heightIn(min = TILE_HEIGHT)
        .clip(CircleShape)
        .background(container)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(22.dp),
    )
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      color = content,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * A drawn copy of the platform's notice under a caption that says so, worded as the platform words
 * it in this locale, so the user knows what to long-press once it appears. The framework strings
 * come off the main thread; the app's copy draws until they land, and stands in for good under the
 * preview renderer, which has neither a package manager nor framework strings.
 */
@Composable
private fun NoticeCard() {
  val context = LocalContext.current
  val inspection = LocalInspectionMode.current
  val fallbackTitle =
    stringResource(R.string.onboarding_notice_card_title, stringResource(R.string.app_name))
  val fallbackSource = stringResource(R.string.onboarding_notice_card_source)
  val title by
    produceState(fallbackTitle, context, inspection) {
      if (!inspection) value = withContext(Dispatchers.IO) { systemNoticeTitle(context) }
    }
  val source by
    produceState(fallbackSource, context, inspection) {
      if (!inspection) value = withContext(Dispatchers.IO) { systemAppLabel(context) }
    }
  val description = stringResource(R.string.onboarding_notice_card_description, title, source)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(R.string.onboarding_notice_card_caption),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .padding(16.dp)
          .clearAndSetSemantics { contentDescription = description },
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(NOTICE_ICON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceBright),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_settings),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
      Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(
          source,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * The framework's own title for its notice, resolved by name because the platform publishes no id
 * for it. A vendor build can rename the resource or reshape its placeholders, so the app's copy
 * stands in whenever the lookup or the format fails.
 */
@SuppressLint("DiscouragedApi")
private fun systemNoticeTitle(context: Context): String {
  val appName = context.getString(R.string.app_name)
  val fallback = context.getString(R.string.onboarding_notice_card_title, appName)
  val id = context.resources.getIdentifier("alert_windows_notification_title", "string", "android")
  if (id == 0) return fallback
  return try {
    context.getString(id, appName)
  } catch (e: RuntimeException) {
    AppLog.w("The framework's notice title does not take the app name", e)
    fallback
  }
}

/** The framework package's label, "Android System" in English, as the shade prints it. */
private fun systemAppLabel(context: Context): String {
  val fallback = context.getString(R.string.onboarding_notice_card_source)
  return try {
    val packageManager = context.packageManager
    packageManager.getApplicationLabel(packageManager.getApplicationInfo("android", 0)).toString()
  } catch (e: PackageManager.NameNotFoundException) {
    AppLog.w("No label for the framework package", e)
    fallback
  } catch (e: Resources.NotFoundException) {
    AppLog.w("No label for the framework package", e)
    fallback
  }
}

private val HERO_SIZE: Dp = 132.dp
private val HERO_ICON_SIZE: Dp = 56.dp
private val CARD_INSET: Dp = 8.dp
/** A drawn bar sits in from its card so a caption never runs to the card's edge. */
private val SKETCH_CARD_INSET: Dp = 16.dp
private val BADGE_SIZE: Dp = 32.dp
private val BADGE_ICON_SIZE: Dp = 18.dp
private val TILE_WIDTH: Dp = 156.dp
private val TILE_HEIGHT: Dp = 64.dp
private val NOTICE_ICON_SIZE: Dp = 36.dp

@Composable
private fun StepHeader(index: Int, count: Int) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stringResource(R.string.onboarding_step_counter, index + 1, count),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearWavyProgressIndicator(
      progress = { (index + 1).toFloat() / count },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** The one filled button at the end of the bar; its label and action follow the step's state. */
@Composable
private fun PrimaryAction(
  step: OnboardingStep,
  hasOverlayPermission: Boolean,
  batteryExempt: Boolean,
  sideFlipEnabled: Boolean,
  onNext: () -> Unit,
  onOpenOverlaySettings: () -> Unit,
  onRequestNotifications: () -> Unit,
  onRequestTile: () -> Unit,
  onRequestBatteryExemption: () -> Unit,
  onOpenSideFlipSettings: () -> Unit,
  onSideFlipNext: () -> Unit,
  onStart: () -> Unit,
) {
  val hasTilePrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
  val (labelRes: Int, action: () -> Unit) =
    when (step) {
      OnboardingStep.WELCOME -> R.string.onboarding_action_next to onNext
      OnboardingStep.OVERLAY ->
        if (hasOverlayPermission) R.string.onboarding_action_next to onNext
        else R.string.onboarding_action_open_settings to onOpenOverlaySettings
      OnboardingStep.NOTIFICATIONS -> R.string.onboarding_action_allow to onRequestNotifications
      OnboardingStep.TILE ->
        if (hasTilePrompt) R.string.onboarding_action_add_tile to onRequestTile
        else R.string.onboarding_action_next to onNext
      OnboardingStep.KEEP_ALIVE ->
        if (batteryExempt) R.string.onboarding_action_next to onNext
        else R.string.onboarding_action_allow to onRequestBatteryExemption
      OnboardingStep.SYSTEM_NOTICE -> R.string.onboarding_action_next to onNext
      OnboardingStep.SIDE_FLIP ->
        if (sideFlipEnabled) R.string.onboarding_action_next to onSideFlipNext
        else R.string.onboarding_action_turn_on to onOpenSideFlipSettings
      OnboardingStep.START -> R.string.onboarding_action_start to onStart
    }
  Button(onClick = action) { Text(stringResource(labelRes)) }
}

@Composable
private fun StatusLine(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
private fun RestrictedSettingsCard() {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(R.string.onboarding_overlay_blocked_title),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        stringResource(R.string.onboarding_overlay_blocked_body),
        style = MaterialTheme.typography.bodyMedium,
      )
      val stepTexts =
        listOf(
          stringResource(R.string.onboarding_overlay_blocked_step_1),
          stringResource(R.string.onboarding_overlay_blocked_step_2),
          stringResource(R.string.onboarding_overlay_blocked_step_3),
        )
      stepTexts.forEachIndexed { number, text ->
        Text("${number + 1}. $text", style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

/**
 * This phone's drawn status bar with the readout placed on it, on a card that names what it shows.
 * [measuredBar] is the bar's live reading once the side flip service runs, so the card shows what
 * the bar will get rather than what the estimates would give it.
 */
@Composable
private fun StartPreviewCard(
  geometry: DisplayGeometry,
  profile: Profile,
  measuredBar: MeasuredBar? = null,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(SKETCH_CARD_INSET),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      stringResource(R.string.onboarding_start_here_title),
      style = MaterialTheme.typography.titleMedium,
    )
    StatusBarSketch(
      geometry = geometry,
      profile = profile,
      notificationIcons = 1,
      contentDescription = stringResource(R.string.onboarding_start_preview),
      modifier = Modifier.fillMaxWidth(),
      measuredBar = measuredBar,
    )
  }
}

private fun OnboardingStep.titleRes(): Int =
  when (this) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
    OnboardingStep.OVERLAY -> R.string.onboarding_overlay_title
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
    OnboardingStep.TILE -> R.string.onboarding_tile_title
    OnboardingStep.KEEP_ALIVE -> R.string.onboarding_keep_alive_title
    OnboardingStep.SYSTEM_NOTICE -> R.string.onboarding_notice_title
    OnboardingStep.SIDE_FLIP -> R.string.onboarding_side_flip_title
    OnboardingStep.START -> R.string.onboarding_start_title
  }

private fun OnboardingStep.icon(): Int =
  when (this) {
    OnboardingStep.WELCOME -> R.drawable.ic_readout
    OnboardingStep.OVERLAY -> R.drawable.ic_picture_in_picture_alt
    OnboardingStep.NOTIFICATIONS -> R.drawable.ic_notifications
    OnboardingStep.TILE -> R.drawable.ic_dashboard_customize
    OnboardingStep.KEEP_ALIVE -> R.drawable.ic_battery_saver
    OnboardingStep.SYSTEM_NOTICE -> R.drawable.ic_notifications_off
    OnboardingStep.SIDE_FLIP -> R.drawable.ic_swap_vert
    OnboardingStep.START -> R.drawable.ic_rocket_launch
  }

/** The per-app overlay page cannot be deep-linked on Android 11+, so the list page opens. */
private fun overlaySettingsIntent(context: Context): Intent =
  Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

/**
 * The exemption dialog is the step's whole purpose on vendors whose battery managers stop
 * foreground services, which is why lint's policy warning does not apply here.
 */
@SuppressLint("BatteryLife")
private fun batteryExemptionIntent(context: Context): Intent =
  Intent(
    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    "package:${context.packageName}".toUri(),
  )

/** The install source category, readable from API 33; older versions report unspecified. */
private fun installPackageSource(context: Context): Int {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    return NO_PACKAGE_SOURCE
  }
  return try {
    context.packageManager.getInstallSourceInfo(context.packageName).packageSource
  } catch (e: PackageManager.NameNotFoundException) {
    AppLog.w("Own package missing from the package manager", e)
    NO_PACKAGE_SOURCE
  }
}

/**
 * The value of PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED, a constant that only exists from
 * API 33.
 */
private const val NO_PACKAGE_SOURCE: Int = 0
