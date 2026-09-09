package tools.zach.app.pixelspeed.service

import android.app.Dialog
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.format.FormattedSpeed
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.MonitorStateHolder

/**
 * Quick Settings tile mirroring the overlay's live speed. A tap pauses a running overlay for the
 * active profile's tile pause duration, resumes a paused one, or starts a stopped one. The tile
 * reads active only while the monitor runs, inactive while it is paused or stopped, and unavailable
 * only without the overlay permission, so the tap that pauses it changes its color and a process
 * the system killed leaves a tile that starts it again.
 */
@AndroidEntryPoint
class SpeedTileService : TileService() {

  @Inject lateinit var controller: MonitorController
  @Inject lateinit var stateHolder: MonitorStateHolder
  @Inject lateinit var settingsStore: SettingsStore
  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
  @Inject lateinit var clock: Clock

  private var listeningJob: Job? = null
  private var clickJob: Job? = null
  private var dialog: Dialog? = null
  private var destroyed: Boolean = false

  override fun onStartListening() {
    super.onStartListening()
    listeningJob?.cancel()
    listeningJob =
      applicationScope.launch(Dispatchers.Main) {
        combine(stateHolder.state, settingsStore.settings, stateHolder.latestSample, ::Triple)
          .collect { (state, settings, sample) -> updateTile(state, settings, sample) }
      }
  }

  /**
   * The shade collapses before a click's dialog shows, so a click in flight and a dialog on screen
   * both outlive listening; only the service's death ends them.
   */
  override fun onStopListening() {
    listeningJob?.cancel()
    listeningJob = null
    super.onStopListening()
  }

  override fun onDestroy() {
    destroyed = true
    listeningJob?.cancel()
    listeningJob = null
    clickJob?.cancel()
    clickJob = null
    dialog?.dismiss()
    dialog = null
    super.onDestroy()
  }

  /**
   * With one profile a tap is the pause toggle. With several it opens the profile picker, which
   * carries the same pause and resume, through the keyguard when the device is locked.
   */
  override fun onClick() {
    super.onClick()
    clickJob?.cancel()
    clickJob =
      applicationScope.launch(Dispatchers.Main) {
        val settings = settingsStore.settings.first()
        if (settings.profiles.size >= 2) openDialog(settings) else togglePause(settings)
      }
  }

  private fun openDialog(settings: Settings) {
    val show = Runnable {
      // The first read or the unlock can outlast the service itself.
      if (destroyed) return@Runnable
      dialog?.dismiss()
      val created =
        TileDialog.create(
          context = this,
          settings = settingsStore.settings,
          initialSettings = settings,
          state = stateHolder.state,
          onSelect = { id -> applicationScope.launch { controller.selectProfile(id) } },
          onPause = { minutes -> applicationScope.launch { controller.pause(minutes) } },
          onResume = { applicationScope.launch { resume() } },
        )
      created.setOnDismissListener { if (dialog === created) dialog = null }
      dialog = created
      showDialog(created)
    }
    if (isLocked) unlockAndRun(show) else show.run()
  }

  /** Off with no overlay permission is the permission gap, whatever the state holder reports. */
  private fun resolveOff(state: MonitorState): MonitorState =
    if (state == MonitorState.Off && !controller.hasOverlayPermission) MonitorState.NeedsPermission
    else state

  private fun togglePause(settings: Settings) {
    when (resolveOff(stateHolder.state.value)) {
      is MonitorState.Running ->
        applicationScope.launch {
          controller.pause(TilePauseOptions.default(settings.activeProfile))
        }
      is MonitorState.Paused -> applicationScope.launch { resume() }
      // Off is the user's switch, or a process the system killed with the switch still on: the tap
      // turns the switch on, which also sends the start, a no-op for whichever already holds.
      MonitorState.Off -> applicationScope.launch { start() }
      MonitorState.NeedsPermission -> Unit
    }
  }

  private suspend fun start() {
    if (!controller.setEnabled(true)) {
      AppLog.w("Speed tile could not start the foreground service")
    }
  }

  private suspend fun resume() {
    controller.resume()
    if (!controller.startIfEnabled()) {
      AppLog.w("Speed tile resume could not restart the foreground service")
    }
  }

  private fun updateTile(reported: MonitorState, settings: Settings, sample: SpeedSample?) {
    val tile = qsTile ?: return
    val state = resolveOff(reported)
    val subtitle = subtitleFor(state, settings, sample)
    tile.label = getString(R.string.tile_speed_label)
    tile.icon =
      Icon.createWithResource(
        this,
        if (state is MonitorState.Paused) R.drawable.ic_pause_circle else R.drawable.ic_readout,
      )
    tile.state = tileStateFor(state)
    tile.subtitle = subtitle
    tile.stateDescription =
      if (state is MonitorState.Running && state.visible) speedDescription(settings, sample)
      else subtitle
    tile.updateTile()
  }

  private fun tileStateFor(state: MonitorState): Int =
    when (state) {
      is MonitorState.Running -> Tile.STATE_ACTIVE
      is MonitorState.Paused,
      MonitorState.Off -> Tile.STATE_INACTIVE
      MonitorState.NeedsPermission -> Tile.STATE_UNAVAILABLE
    }

  private fun subtitleFor(state: MonitorState, settings: Settings, sample: SpeedSample?): String =
    when (state) {
      is MonitorState.Running ->
        if (state.visible) speedSubtitle(settings, sample)
        else getString(R.string.home_state_hidden)
      is MonitorState.Paused ->
        state.until?.let { until -> getString(R.string.tile_speed_paused_until, formatTime(until)) }
          ?: getString(R.string.home_state_paused)
      MonitorState.Off -> getString(R.string.tile_speed_off)
      MonitorState.NeedsPermission -> getString(R.string.home_state_needs_permission)
    }

  private fun speedDescription(settings: Settings, sample: SpeedSample?): String {
    val (download, upload) = speedPair(settings, sample)
    return getString(
      R.string.tile_speed_state_description,
      download.number.trim(),
      download.unit,
      upload.number.trim(),
      upload.unit,
    )
  }

  private fun speedPair(
    settings: Settings,
    sample: SpeedSample?,
  ): Pair<FormattedSpeed, FormattedSpeed> {
    val formatter = SpeedFormatter(Locale.getDefault())
    val options = settings.activeProfile.content.format.copy(unitLabel = UnitLabel.FULL)
    return if (sample == null || !sample.supported) {
      val dash = formatter.format(Long.MIN_VALUE, options)
      dash to dash
    } else {
      formatter.formatPair(sample.rxBytesPerSecond, sample.txBytesPerSecond, options)
    }
  }

  private fun speedSubtitle(settings: Settings, sample: SpeedSample?): String {
    val (download, upload) = speedPair(settings, sample)
    val pair = getString(R.string.tile_speed_pair, download.number.trim(), upload.number.trim())
    val speed = if (download.unit.isEmpty()) pair else "$pair ${download.unit}"
    // The large tile size shows which profile the readout belongs to once there is a choice, but
    // not on the lock screen, where a profile name is the user's own text shown before unlock.
    return if (settings.profiles.size >= 2 && !isLocked) {
      getString(R.string.tile_speed_subtitle_profile, speed, settings.activeProfile.name)
    } else {
      speed
    }
  }

  private fun formatTime(until: Instant): String =
    DeadlineFormatter.format(
      until,
      clock.millis(),
      Locale.getDefault(),
      ZoneId.systemDefault(),
      DateFormat.is24HourFormat(this),
    )
}
