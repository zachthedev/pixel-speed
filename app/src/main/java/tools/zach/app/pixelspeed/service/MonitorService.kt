package tools.zach.app.pixelspeed.service

import android.animation.ValueAnimator
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.MeasuredBar
import tools.zach.app.pixelspeed.overlay.OverlayWindow
import tools.zach.app.pixelspeed.sampling.CounterSourceKind
import tools.zach.app.pixelspeed.sampling.Smoothing
import tools.zach.app.pixelspeed.sampling.SpeedSampler
import tools.zach.app.pixelspeed.sampling.TrafficStatsCounterSource
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.BehaviorSettings
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsBounds
import tools.zach.app.pixelspeed.settings.SettingsDefaults
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.signals.DefaultNetwork
import tools.zach.app.pixelspeed.signals.NetworkStateSource
import tools.zach.app.pixelspeed.signals.OverlayPermissionSource
import tools.zach.app.pixelspeed.signals.PowerSaveSource
import tools.zach.app.pixelspeed.signals.ScreenState
import tools.zach.app.pixelspeed.signals.ScreenStateSource
import tools.zach.app.pixelspeed.state.IdleTracker
import tools.zach.app.pixelspeed.state.MonitorInputs
import tools.zach.app.pixelspeed.state.MonitorReducer
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.MonitorStateHolder
import tools.zach.app.pixelspeed.state.Readout
import tools.zach.app.pixelspeed.state.ReadoutPublisher
import tools.zach.app.pixelspeed.state.ReadoutSink
import tools.zach.app.pixelspeed.state.SuppressionRules
import tools.zach.app.pixelspeed.state.SuppressionSignals
import tools.zach.app.pixelspeed.ui.overlay.OverlayRoot

/**
 * Owns the overlay window and the sampler for as long as the monitor is enabled. Every surface
 * changes settings through [MonitorController] or sends a [MonitorActions] intent; the service
 * derives [MonitorState] from settings and platform signals and writes it to [MonitorStateHolder].
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {

  @Inject lateinit var stateHolder: MonitorStateHolder
  @Inject lateinit var settingsStore: SettingsStore
  @Inject lateinit var controller: MonitorController
  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
  @Inject lateinit var networkSource: NetworkStateSource
  @Inject lateinit var screenSource: ScreenStateSource
  @Inject lateinit var powerSaveSource: PowerSaveSource
  @Inject lateinit var overlayPermission: OverlayPermissionSource
  @Inject lateinit var clock: Clock
  @Inject lateinit var uptime: Uptime
  @Inject lateinit var statusBarReadings: StatusBarReadings
  /** Push surfaces bound by other modules; the notification sink is added at start. */
  @Inject lateinit var boundSinks: Set<@JvmSuppressWildcards ReadoutSink>

  private lateinit var notification: MonitorNotification
  private lateinit var mapper: NotificationModelMapper
  private lateinit var window: OverlayWindow
  // Observable, so a rebuild after a configuration change restarts the fan-out with the new sinks.
  private val sinks: MutableStateFlow<List<ReadoutSink>> = MutableStateFlow(emptyList())
  private val publisher: ReadoutPublisher =
    ReadoutPublisher(Dispatchers.Default, SINK_TIMEOUT_MS) { sink, error ->
      AppLog.e("Sink ${sink::class.simpleName} failed to publish", error)
    }
  private lateinit var geometry: MutableStateFlow<DisplayGeometry>
  // The overlay draws from this one, so the side flip reading and setting apply before it does.
  private lateinit var effectiveGeometry: MutableStateFlow<DisplayGeometry>
  private var unregisterConfiguration: (() -> Unit)? = null
  private var clockReceiver: BroadcastReceiver? = null
  private var dismissReceiver: BroadcastReceiver? = null

  private val profile: MutableStateFlow<Profile> = MutableStateFlow(SettingsDefaults.defaultProfile)
  private val transport: MutableStateFlow<Transport> = MutableStateFlow(Transport.NONE)
  private val idle: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val fullscreen: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private var lastSettings: Settings = Settings()
  private var running: Boolean = false
  private var samplerJob: Job? = null
  private var collectorsJob: Job? = null
  private var fullscreenJob: Job? = null
  private var resumeJob: Job? = null
  // The readout animates out before the window hides, so the hide waits for the exit to finish.
  private val overlayShown: MutableStateFlow<Boolean> = MutableStateFlow(true)
  private var hideJob: Job? = null
  private var showJob: Job? = null
  /** True from a show until the next hide, so a repeated show never re-arms the wait. */
  private var showRequested: Boolean = false

  override fun onCreate() {
    super.onCreate()
    notification = MonitorNotification(this)
    notification.ensureChannel()
    mapper = buildMapper()
    window = OverlayWindow(applicationContext)
    geometry = MutableStateFlow(window.geometry())
    effectiveGeometry = MutableStateFlow(geometry.value)
    sinks.value = listOf(NotificationSink(notification, mapper)) + boundSinks
    AppLog.i("Service created; display ${geometry.value}")
  }

  /** Replaces the mapper and every sink built on it, restarting the fan-out with the new ones. */
  private fun rebuildSinks() {
    mapper = buildMapper()
    sinks.value = listOf(NotificationSink(notification, mapper)) + boundSinks
  }

  /** Reads the locale and zone fresh, so a rebuild after either changes follows them. */
  private fun buildMapper(): NotificationModelMapper =
    NotificationModelMapper(
      formatter = SpeedFormatter(Locale.getDefault()),
      locale = Locale.getDefault(),
      zone = ZoneId.systemDefault(),
      sdkInt = Build.VERSION.SDK_INT,
      promotedAvailable = PromotedChip.available(this),
      hour24 = { DateFormat.is24HourFormat(this) },
      clock = clock,
    )

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    val startedAt = uptime.millis()
    // The platform allows a few seconds between start and startForeground; nothing else runs first.
    val current =
      notification.build(
        mapper.map(
          stateHolder.state.value,
          stateHolder.latestSample.value,
          lastSettings.activeProfile,
        )
      )
    if (!promote(current)) {
      // The start allowance lapsed before the service ran; a sticky restart would only repeat it.
      // Only the user's own switch is rolled back, so it does not claim a service that is not
      // there. A start the app attempted on its own (boot, launch, a grant) leaves the switch on:
      // Home's state line reads off beside it, and the next launch or tile tap asks again.
      stateHolder.setState(MonitorState.Off)
      if (MonitorActions.userAsked(intent)) {
        applicationScope.launch { controller.clearEnabled(startedAt) }
      }
      stopSelf()
      return START_NOT_STICKY
    }
    val minutes =
      intent
        ?.takeIf { it.hasExtra(MonitorActions.EXTRA_MINUTES) }
        ?.getIntExtra(MonitorActions.EXTRA_MINUTES, 0)
    // A null action is the platform restarting a sticky service.
    when (intent?.action) {
      MonitorActions.START,
      null -> Unit
      MonitorActions.PAUSE -> lifecycleScope.launch { controller.pause(minutes) }
      MonitorActions.RESUME -> lifecycleScope.launch { controller.resume() }
      MonitorActions.TOGGLE -> lifecycleScope.launch { controller.togglePause(minutes) }
      else -> AppLog.w("Ignoring unknown action ${intent.action}")
    }
    // startForeground showed the notification, whatever the user did to the last one.
    notification.onStarted()
    ensureRunning()
    return START_STICKY
  }

  /** Service types exist from Android 14; earlier versions take the two-argument form. */
  private fun promote(current: Notification): Boolean =
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
          MonitorNotification.NOTIFICATION_ID,
          current,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
      } else {
        startForeground(MonitorNotification.NOTIFICATION_ID, current)
      }
      true
    } catch (e: ForegroundServiceStartNotAllowedException) {
      AppLog.w("Foreground promotion refused; the start allowance lapsed", e)
      false
    }

  private fun ensureRunning() {
    if (running) return
    running = true
    notification.clearNeedsPermission()
    var locales: LocaleList = resources.configuration.locales
    unregisterConfiguration = window.registerConfigurationListener { configuration ->
      geometry.value = window.geometry()
      // Every configuration delta arrives here; only a locale change needs new formatters.
      if (configuration.locales != locales) {
        locales = configuration.locales
        rebuildSinks()
      }
    }
    // The paused body carries a clock time, so a zone change or a 12/24-hour toggle (which the
    // platform announces as a time change) rebuilds the formatters and republishes.
    clockReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = rebuildSinks()
      }
    ContextCompat.registerReceiver(
      this,
      clockReceiver,
      IntentFilter(Intent.ACTION_TIMEZONE_CHANGED).apply { addAction(Intent.ACTION_TIME_CHANGED) },
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    dismissReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = notification.onDismissed()
      }
    ContextCompat.registerReceiver(
      this,
      dismissReceiver,
      IntentFilter(MonitorActions.NOTIFICATION_DISMISSED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    collectorsJob = lifecycleScope.launch {
      launch { collectState() }
      launch { collectSamplerInputs() }
      launch { collectReadouts() }

      launch { collectEditMode() }
      launch { collectEffectiveGeometry() }
      launch { collectReadBackstop() }
    }
  }

  /**
   * While the readout is shown and follows the icons, the bar is re-read on a timer as well as on
   * System UI's own events, so an icon change those events miss still reaches the pill. A read is
   * one window walk over the bar, a few milliseconds of IPC.
   */
  private suspend fun collectReadBackstop() {
    combine(overlayShown, profile.map { it.position.sideFlip }.distinctUntilChanged()) { shown, flip
        ->
        shown && flip
      }
      .distinctUntilChanged()
      .collectLatest { active ->
        while (active) {
          delay(READ_BACKSTOP_MS)
          statusBarReadings.requestRead()
        }
      }
  }

  /**
   * Feeds the overlay the bar's real measurement only while the side flip is on. A reading lands at
   * once while the readout is hidden or has none yet, so a readout waiting to show gets the bar as
   * soon as it is read; a change while the readout is shown must hold for
   * [MEASURED_BAR_DEBOUNCE_MS] before the pill moves, so the readings taken while the shade
   * animates over the bar or icons flicker never land. The spot a readout springs in at therefore
   * comes from one read, the fresh one its show asked for.
   */
  private suspend fun collectEffectiveGeometry() {
    val settledReadings: Flow<MeasuredBar?> = channelFlow {
      var held: MeasuredBar? = null
      statusBarReadings.readings.collectLatest { reading ->
        if (held != null && reading != null && overlayShown.value) delay(MEASURED_BAR_DEBOUNCE_MS)
        held = reading
        send(reading)
      }
    }
    combine(
        geometry,
        settledReadings,
        profile.map { it.position.sideFlip }.distinctUntilChanged(),
      ) { base, reading, sideFlip ->
        base.copy(measuredBar = if (sideFlip) reading else null)
      }
      .collect { effectiveGeometry.value = it }
  }

  /**
   * Reduces settings and signals to the state and applies it. Only this function touches
   * visibility.
   */
  private suspend fun collectState() {
    data class Local(
      val idle: Boolean,
      val fullscreen: Boolean,
      val landscape: Boolean,
      val permission: Boolean,
      val physical: Set<String>,
    )
    data class Signals(
      val settings: Settings,
      val screen: ScreenState,
      val default: DefaultNetwork,
      val powerSave: Boolean,
      val local: Local,
    )
    combine(
        settingsStore.settings,
        screenSource.state,
        networkSource.defaultNetwork,
        powerSaveSource.powerSaveOn,
        combine(
          idle,
          fullscreen,
          geometry.map { it.landscape }.distinctUntilChanged(),
          overlayPermission.granted,
          networkSource.physicalInterfaces,
        ) { i, f, l, p, n ->
          Local(i, f, l, p, n.names)
        },
      ) { settings, screen, default, powerSave, local ->
        Signals(settings, screen, default, powerSave, local)
      }
      .collect { signals ->
        lastSettings = signals.settings
        val active = signals.settings.activeProfile
        profile.value = active
        val suppressions =
          SuppressionRules.evaluate(
            active.behavior,
            SuppressionSignals(
              screenInteractive = signals.screen.interactive,
              keyguardLocked = signals.screen.locked,
              // Offline is the chosen counters having nothing to read, the same condition that
              // blanks the sample to dashes, so the two surfaces never disagree.
              online = !active.counterSource.readsNothing(signals.default, signals.local.physical),
              idle = signals.local.idle,
              fullscreen = signals.local.fullscreen,
              landscape = signals.local.landscape,
              powerSaveOn = signals.powerSave,
            ),
          )
        val inputs =
          MonitorInputs(
            enabled = signals.settings.enabled,
            hasOverlayPermission = signals.local.permission,
            paused = signals.settings.paused,
            pausedUntilEpochMillis = signals.settings.pausedUntilEpochMillis,
            nowEpochMillis = clock.millis(),
            suppressions = suppressions,
            surface = active.notification.surface,
          )
        apply(MonitorReducer.reduce(inputs), MonitorReducer.resumeDelayMillis(inputs), active)
        if (signals.settings.paused && inputs.pausedUntilEpochMillis != null && resumeJob == null) {
          // A deadline that passed while the service was away is cleared on the next start.
          lifecycleScope.launch { controller.expirePauseIfDue() }
        }
      }
  }

  private fun apply(state: MonitorState, resumeDelayMillis: Long?, active: Profile) {
    if (state != stateHolder.state.value) AppLog.i("Monitor state: $state")
    stateHolder.setState(state)
    scheduleResume(resumeDelayMillis)
    when (state) {
      MonitorState.Off -> shutdown()
      // A foreground service with nothing to draw has no reason to stay resident, but the way back
      // must survive it: a plain notification carries the grant action until the next start.
      MonitorState.NeedsPermission -> {
        shutdown()
        notification.postNeedsPermission(mapper.map(state, null, lastSettings.activeProfile))
      }
      is MonitorState.Paused -> {
        probeFullscreen(false)
        hideOverlay(active.appearance.windowOpacity)
      }
      is MonitorState.Running -> {
        probeFullscreen(active.behavior.hideInFullscreen)
        val opacity = active.appearance.windowOpacity
        val inStatusBar = active.position.anchor.inStatusBar
        if (state.overlayOn && !window.isAttached) {
          val attached =
            window.attach(inStatusBar, opacity) {
              OverlayRoot(
                profile = profile,
                sample = stateHolder.latestSample,
                loading = stateHolder.loading,
                geometry = effectiveGeometry,
                shown = overlayShown,
                promotedChipAvailable = PromotedChip.available(this),
                onOrigin = { origin -> window.moveTo(origin.xDp, origin.yDp) },
                onFits = stateHolder::setOverlayFits,
              )
            }
          if (!attached) {
            apply(MonitorState.NeedsPermission, null, active)
            return
          }
        } else if (window.isAttached) {
          window.setInStatusBar(inStatusBar)
        }
        if (state.visible) showOverlay(opacity, active.position.sideFlip) else hideOverlay(opacity)
      }
    }
  }

  /**
   * The window shows at once and the readout animates in. With the side flip on, the readout asks
   * for a fresh read and waits for it, or [FIRST_READING_GRACE_MS], so it springs in at its final
   * spot instead of moving there from a reading taken before the bar changed, as it does across an
   * unlock, where the lock screen's bar reads differently from the home screen's.
   */
  private fun showOverlay(opacity: Float, sideFlip: Boolean) {
    hideJob?.cancel()
    hideJob = null
    window.setVisible(true, opacity)
    statusBarReadings.setOverlayVisible(true)
    if (showRequested) return
    showRequested = true
    AppLog.i("Overlay shown (opacity $opacity, side flip $sideFlip)")
    if (!sideFlip) {
      overlayShown.value = true
      return
    }
    overlayShown.value = false
    val readsBefore = statusBarReadings.reads.value
    statusBarReadings.requestRead()
    showJob = lifecycleScope.launch {
      withTimeoutOrNull(FIRST_READING_GRACE_MS) {
        statusBarReadings.reads.first { it > readsBefore }
        // The geometry that carries the read's own value, so the order of the two flows never
        // matters; a read that found no bar, or none settled, holds and the grace decides.
        effectiveGeometry.first {
          it.measuredBar != null && it.measuredBar == statusBarReadings.readings.value
        }
      }
      overlayShown.value = true
    }
  }

  /**
   * The readout animates out first; the window hides once the exit has finished, or at once when
   * animations are off, unless a show arrives in between.
   */
  private fun hideOverlay(opacity: Float) {
    if (showRequested) AppLog.i("Overlay hidden")
    showRequested = false
    showJob?.cancel()
    statusBarReadings.setOverlayVisible(false)
    if (!overlayShown.value && hideJob?.isActive == true) return
    overlayShown.value = false
    hideJob?.cancel()
    hideJob = lifecycleScope.launch {
      if (ValueAnimator.areAnimatorsEnabled()) delay(OVERLAY_EXIT_MILLIS)
      if (!overlayShown.value) window.setVisible(false, opacity)
    }
  }

  /** Wakes the pause deadline. A timed pause resumes by clearing the persisted flag. */
  private fun scheduleResume(delayMillis: Long?) {
    resumeJob?.cancel()
    resumeJob = null
    if (delayMillis == null) return
    resumeJob = lifecycleScope.launch {
      delay(delayMillis.coerceAtLeast(0L))
      controller.expirePauseIfDue()
    }
  }

  /**
   * The probe window exists only while the fullscreen rule is on and the monitor runs. A probe the
   * platform refused ends its flow, and the cleared job lets the next state change try again.
   */
  private fun probeFullscreen(enabled: Boolean) {
    if (enabled && fullscreenJob == null) {
      fullscreenJob =
        lifecycleScope
          .launch { window.fullscreen().collect { fullscreen.value = it } }
          .also { job ->
            job.invokeOnCompletion { if (fullscreenJob === job) fullscreenJob = null }
          }
    } else if (!enabled && fullscreenJob != null) {
      fullscreenJob?.cancel()
      fullscreenJob = null
      fullscreen.value = false
    }
  }

  /**
   * Any change in the counter set, interval, smoothing, or idle rule restarts the loop from a fresh
   * baseline. Sampling runs only in a running state whose suppressions allow it.
   */
  private suspend fun collectSamplerInputs() {
    data class Inputs(
      val kind: CounterSourceKind,
      val interfaces: Set<String>,
      val defaultInterface: String?,
      val intervalMs: Int,
      val smoothing: Smoothing,
      val behavior: BehaviorSettings,
      val sampling: Boolean,
      val sourceEmpty: Boolean,
    )
    combine(
        profile,
        networkSource.physicalInterfaces,
        networkSource.defaultNetwork,
        stateHolder.state.map { it is MonitorState.Running && it.sampling }.distinctUntilChanged(),
      ) { active, physical, default, sampling ->
        transport.value = default.transport
        Inputs(
          kind = active.counterSource,
          interfaces = physical.names,
          defaultInterface = default.interfaceName,
          intervalMs =
            SettingsBounds.samplerTickMs(active.behavior.updateIntervalMs, Build.VERSION.SDK_INT),
          smoothing = active.behavior.smoothing,
          behavior = active.behavior,
          sampling = sampling,
          sourceEmpty = active.counterSource.readsNothing(default, physical.names),
        )
      }
      .distinctUntilChanged()
      .collectLatest { inputs ->
        samplerJob?.cancel()
        idle.value = false
        if (!inputs.sampling || inputs.sourceEmpty) {
          // A suppression stopped the reading, or the chosen counters have nothing to read: the
          // null sample renders the dashes the offline mode promises, and the idle rule, a rule
          // about a quiet link, stands down until a link is back.
          stateHolder.setSample(null)
          return@collectLatest
        }
        stateHolder.expectSample()
        val source =
          TrafficStatsCounterSource(inputs.kind, inputs.interfaces, inputs.defaultInterface)
        val sampler = SpeedSampler(source, SystemClock::elapsedRealtimeNanos) { transport.value }
        val idleTracker =
          IdleTracker(
            inputs.behavior.idleThresholdBytesPerSecond,
            inputs.behavior.idleDelayMs.toLong(),
          )
        samplerJob = lifecycleScope.launch {
          sampler
            .samples(inputs.intervalMs.toLong(), inputs.smoothing)
            .flowOn(Dispatchers.Default)
            .collect { sample ->
              stateHolder.setSample(sample)
              if (inputs.behavior.hideWhenIdle && sample.supported) {
                idle.value =
                  idleTracker.onSample(
                    sample.rxBytesPerSecond,
                    sample.txBytesPerSecond,
                    sample.atElapsedNanos / NANOS_PER_MILLI,
                  )
              }
            }
        }
      }
  }

  /** Edit mode ends on Done, on leaving the app, or after [EDIT_TIMEOUT_MS] with no Done. */
  private suspend fun collectEditMode() {
    stateHolder.editMode.collectLatest { editing ->
      window.setEditable(editing) { xDp, yDp ->
        lifecycleScope.launch { controller.setCustomPosition(xDp, yDp) }
      }
      if (editing) {
        delay(EDIT_TIMEOUT_MS)
        stateHolder.setEditMode(false)
      }
    }
  }

  /**
   * Publishes every state or settings change to every sink, including the terminal states, then
   * each sample only to the sinks that asked for samples under these settings.
   */
  private suspend fun collectReadouts() {
    combine(stateHolder.state, settingsStore.settings, sinks, ::Triple).collectLatest {
      (state, settings, targets) ->
      val profile = settings.activeProfile
      val readout = Readout(state, stateHolder.latestSample.value, profile)
      publisher.publish(readout, targets)
      val sampleSinks = targets.filter { it.wantsSamples(readout) }
      if (sampleSinks.isNotEmpty()) {
        stateHolder.latestSample.drop(1).collect { sample ->
          publisher.publish(Readout(state, sample, profile), sampleSinks)
        }
      }
    }
  }

  /** The shared holder carries nothing of a service that is gone. */
  private fun resetSharedState() {
    stateHolder.clear()
    stateHolder.setEditMode(false)
    stateHolder.setOverlayFits(true)
    // The reader outlives this service under the system's grant, so the gate closes here too.
    statusBarReadings.setOverlayVisible(false)
  }

  private fun unregisterReceivers() {
    unregisterQuietly(clockReceiver)
    clockReceiver = null
    unregisterQuietly(dismissReceiver)
    dismissReceiver = null
  }

  /** A receiver whose registration failed was never registered; the teardown goes on regardless. */
  private fun unregisterQuietly(receiver: BroadcastReceiver?) {
    if (receiver == null) return
    try {
      unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
      AppLog.w("Receiver was not registered", e)
    }
  }

  /** Once per service lifetime, from [onDestroy]; a sink that throws here is logged and skipped. */
  private fun stopSinks() {
    for (sink in sinks.value) {
      try {
        sink.onStopped()
      } catch (e: Exception) {
        AppLog.e("Sink ${sink::class.simpleName} failed to stop", e)
      }
    }
  }

  /**
   * Stops everything in an order that leaves no notification behind: the collectors first, so no
   * post can land after the foreground notification is removed.
   */
  private fun shutdown() {
    running = false
    collectorsJob?.cancel()
    collectorsJob = null
    samplerJob?.cancel()
    samplerJob = null
    fullscreenJob?.cancel()
    fullscreenJob = null
    resumeJob?.cancel()
    resumeJob = null
    hideJob?.cancel()
    showJob?.cancel()
    showJob = null
    hideJob = null
    showRequested = false
    unregisterConfiguration?.invoke()
    unregisterConfiguration = null
    unregisterReceivers()
    window.detach()
    resetSharedState()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  /** The one teardown: stopService and a platform kill both arrive here. */
  override fun onDestroy() {
    AppLog.i("Service destroyed")
    running = false
    collectorsJob?.cancel()
    samplerJob?.cancel()
    fullscreenJob?.cancel()
    resumeJob?.cancel()
    hideJob?.cancel()
    showJob?.cancel()
    showJob = null
    showRequested = false
    unregisterConfiguration?.invoke()
    unregisterConfiguration = null
    unregisterReceivers()
    window.detach()
    stopSinks()
    resetSharedState()
    val state = stateHolder.state.value
    if (state is MonitorState.Running || state is MonitorState.Paused) {
      stateHolder.setState(MonitorState.Off)
    }
    super.onDestroy()
  }

  private companion object {
    const val NANOS_PER_MILLI: Long = 1_000_000L
    const val EDIT_TIMEOUT_MS: Long = 60_000L
    /** Long enough for the expressive effects spec to settle the readout's exit. */
    const val OVERLAY_EXIT_MILLIS: Long = 300L
    /** A sink still suspended after this is logged and cut off for that readout. */
    const val SINK_TIMEOUT_MS: Long = 2_000L
    /** A bar reading survives this long of quiet before it reaches the overlay. */
    const val MEASURED_BAR_DEBOUNCE_MS: Long = 1_000L

    /** How often the bar is re-read while the readout follows the icons. */
    const val READ_BACKSTOP_MS: Long = 15_000L
    /** How long a shown readout waits for the bar's first measurement before it appears anyway. */
    const val FIRST_READING_GRACE_MS: Long = 1_200L
  }
}

/** A default network with an interface is the app's one definition of being online. */
private val DefaultNetwork.online: Boolean
  get() = interfaceName != null

/**
 * Whether a counter source has nothing to read. The device-wide sources always read, since
 * tethering and local-only links move bytes with no default network.
 */
private fun CounterSourceKind.readsNothing(
  default: DefaultNetwork,
  physical: Set<String>,
): Boolean =
  when (this) {
    CounterSourceKind.DEFAULT_NETWORK -> !default.online
    CounterSourceKind.PHYSICAL_INTERFACES -> physical.isEmpty()
    CounterSourceKind.ALL_INTERFACES,
    CounterSourceKind.MOBILE_ONLY,
    CounterSourceKind.NON_MOBILE_ONLY -> false
  }
