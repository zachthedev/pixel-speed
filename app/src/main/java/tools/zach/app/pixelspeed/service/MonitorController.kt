package tools.zach.app.pixelspeed.service

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.MonitorStateHolder
import tools.zach.app.pixelspeed.state.PauseTransitions

/**
 * The one place that changes what the monitor should be doing. Every surface (Home, tiles,
 * notification actions, shortcuts, receivers) calls it, and the service reacts to the settings it
 * writes. Pause state is persisted, so a pause survives process death and a reboot.
 */
@Singleton
class MonitorController
@Inject
constructor(
  private val platform: MonitorPlatform,
  private val settingsStore: SettingsStore,
  private val stateHolder: MonitorStateHolder,
  private val clock: Clock,
  private val uptime: Uptime,
) {

  val hasOverlayPermission: Boolean
    get() = platform.hasOverlayPermission

  /**
   * The master switch. Persists first so a process death restores the same answer. A start the
   * platform refuses outright rolls the switch back through [clearEnabled]; the service does the
   * same when the foreground promotion of this user-asked start fails after it was accepted. A
   * missing overlay permission keeps the switch on, since Home shows the permission state and the
   * grant resumes.
   */
  suspend fun setEnabled(enabled: Boolean): Boolean {
    val at = uptime.millis()
    settingsStore.update { it.copy(enabled = enabled) }
    if (!enabled) {
      platform.stop()
      return true
    }
    lastEnabledAt = at
    val started = startIfEnabled(userAsked = true)
    if (!started) clearEnabled(startedAt = at)
    return started
  }

  /**
   * Turns the switch off in answer to a start attempted at [startedAt] on the [Uptime] scale, and
   * only if no newer enable arrived since, so a user who re-enabled while the refusal was in flight
   * keeps their switch.
   */
  suspend fun clearEnabled(startedAt: Long) {
    if (lastEnabledAt > startedAt) return
    settingsStore.update { if (it.enabled) it.copy(enabled = false) else it }
  }

  @Volatile private var lastEnabledAt: Long = 0L

  /** When the last accepted start was sent; the service has not reported back until it runs. */
  private val startSentAt: AtomicLong = AtomicLong(Long.MIN_VALUE / 2)

  /**
   * Starts the service when the switch is on, the permission is held, and it is not already
   * resident. Returns false only when the platform refused the start. A start reaches the service
   * after this call returns, so the state still reads off while one is on its way; a second caller
   * inside [START_IN_FLIGHT_MS] (the activity, Home, and the tile all ask at launch) is told the
   * start is already sent. [userAsked] is true only for the user's own switch, which always sends,
   * and marks the only start whose later failure may turn that switch back off.
   */
  suspend fun startIfEnabled(userAsked: Boolean = false): Boolean {
    val settings = settingsStore.settings.first()
    if (!settings.enabled) return true
    if (!hasOverlayPermission) {
      stateHolder.setState(MonitorState.NeedsPermission)
      return true
    }
    val current = stateHolder.state.value
    if (current is MonitorState.Running || current is MonitorState.Paused) return true
    val now = uptime.millis()
    val sentAt = startSentAt.get()
    if (!userAsked && now - sentAt < START_IN_FLIGHT_MS) return true
    // Two callers that read the same stamp race here; the loser sees the winner's send.
    if (!startSentAt.compareAndSet(sentAt, now) && !userAsked) return true
    val accepted = platform.start(userAsked)
    if (!accepted) startSentAt.set(sentAt)
    return accepted
  }

  suspend fun pause(durationMinutes: Int?): Settings = settingsStore.update {
    PauseTransitions.pause(it, clock.millis(), durationMinutes)
  }

  suspend fun resume(): Settings = settingsStore.update { PauseTransitions.resume(it) }

  suspend fun togglePause(durationMinutes: Int?): Settings = settingsStore.update {
    PauseTransitions.toggle(it, clock.millis(), durationMinutes)
  }

  /** Clears a pause whose deadline passed. Returns true when it wrote anything. */
  suspend fun expirePauseIfDue(): Boolean {
    val now = clock.millis()
    val before = settingsStore.settings.first()
    if (!before.paused || PauseTransitions.isPaused(before, now)) return false
    val after = settingsStore.update {
      if (it.paused && !PauseTransitions.isPaused(it, now)) PauseTransitions.resume(it) else it
    }
    return after != before
  }

  /** Records a dragged position on the active profile and switches its anchor to CUSTOM. */
  suspend fun setCustomPosition(xDp: Int, yDp: Int) {
    settingsStore.update { settings ->
      val active = settings.activeProfile
      settings.copy(
        profiles =
          settings.profiles.map {
            if (it.id != active.id) it
            else
              it.copy(
                position =
                  it.position.copy(anchor = Anchor.CUSTOM, customXdp = xDp, customYdp = yDp)
              )
          }
      )
    }
  }

  /** Makes [id] the active profile when it names one; an unknown id changes nothing. */
  suspend fun selectProfile(id: String) {
    settingsStore.update { settings ->
      if (settings.profiles.any { it.id == id }) settings.copy(activeProfileId = id) else settings
    }
  }

  private companion object {
    /** How long a sent start is trusted to be on its way before another caller sends again. */
    const val START_IN_FLIGHT_MS: Long = 5_000L
  }
}
