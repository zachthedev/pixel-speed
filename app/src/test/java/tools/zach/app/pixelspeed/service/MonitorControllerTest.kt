package tools.zach.app.pixelspeed.service

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.zach.app.pixelspeed.overlay.Anchor
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsStore
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.MonitorStateHolder

class MonitorControllerTest {

  private class FakeSettingsStore(initial: Settings) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state

    override suspend fun update(transform: (Settings) -> Settings): Settings =
      state.updateAndGet(transform)
  }

  private class FakePlatform(
    override var hasOverlayPermission: Boolean = true,
    var accepts: Boolean = true,
  ) : MonitorPlatform {
    var starts: Int = 0
    var stops: Int = 0
    val userAskedStarts: MutableList<Boolean> = mutableListOf()

    override fun start(userAsked: Boolean): Boolean {
      starts += 1
      userAskedStarts += userAsked
      return accepts
    }

    override fun stop() {
      stops += 1
    }
  }

  /** A clock the test advances by hand. */
  private class SteppingClock(var now: Long) : Clock() {
    override fun millis(): Long = now

    override fun instant(): Instant = Instant.ofEpochMilli(now)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
  }

  private class Harness(initial: Settings = Settings()) {
    val store = FakeSettingsStore(initial)
    val platform = FakePlatform()
    val holder = MonitorStateHolder()
    val clock = SteppingClock(NOW)
    val controller = MonitorController(platform, store, holder, clock, Uptime { clock.now })

    suspend fun settings(): Settings = store.settings.first()
  }

  @Test
  fun `enabling with the permission held starts the service and keeps the switch on`() = runTest {
    val h = Harness()

    val started = h.controller.setEnabled(true)

    assertTrue(started)
    assertTrue(h.settings().enabled)
    assertEquals(1, h.platform.starts)
  }

  @Test
  fun `only the switch's own start is marked as the user's`() = runTest {
    val h = Harness(Settings(enabled = true))

    h.controller.startIfEnabled()
    h.controller.setEnabled(true)

    assertEquals(listOf(false, true), h.platform.userAskedStarts)
  }

  @Test
  fun `a start already on its way is not sent again, except for the user's own`() = runTest {
    val h = Harness(Settings(enabled = true))

    h.controller.startIfEnabled()
    h.clock.now += 1_000L
    h.controller.startIfEnabled()
    h.controller.setEnabled(true)
    h.clock.now += 5_000L
    h.controller.startIfEnabled()

    assertEquals(listOf(false, true, false), h.platform.userAskedStarts)
  }

  @Test
  fun `a refused start does not count as one on its way`() = runTest {
    val h = Harness(Settings(enabled = true))
    h.platform.accepts = false
    h.controller.startIfEnabled()
    h.platform.accepts = true

    h.controller.startIfEnabled()

    assertEquals(2, h.platform.starts)
  }

  @Test
  fun `a start the platform refuses rolls the switch back`() = runTest {
    val h = Harness()
    h.platform.accepts = false

    val started = h.controller.setEnabled(true)

    assertFalse(started)
    assertFalse(h.settings().enabled)
  }

  @Test
  fun `enabling without the permission keeps the switch on and reports the missing grant`() =
    runTest {
      val h = Harness()
      h.platform.hasOverlayPermission = false

      val started = h.controller.setEnabled(true)

      assertTrue(started)
      assertTrue(h.settings().enabled)
      assertEquals(MonitorState.NeedsPermission, h.holder.state.value)
      assertEquals(0, h.platform.starts)
    }

  @Test
  fun `disabling turns the switch off and stops the service`() = runTest {
    val h = Harness(Settings(enabled = true))

    h.controller.setEnabled(false)

    assertFalse(h.settings().enabled)
    assertEquals(1, h.platform.stops)
    assertEquals(0, h.platform.starts)
  }

  @Test
  fun `a refused start on its own leaves the switch alone`() = runTest {
    val h = Harness(Settings(enabled = true))
    h.platform.accepts = false

    val started = h.controller.startIfEnabled()

    assertFalse(started)
    assertTrue(h.settings().enabled)
  }

  @Test
  fun `a refusal older than a newer enable leaves the newer switch alone`() = runTest {
    val h = Harness()
    val firstAttempt = h.clock.now
    h.controller.setEnabled(true)
    h.clock.now += 1_000L
    h.controller.setEnabled(true)

    h.controller.clearEnabled(startedAt = firstAttempt)

    assertTrue(h.settings().enabled)
  }

  @Test
  fun `a refusal of the latest enable turns the switch off`() = runTest {
    val h = Harness()
    h.controller.setEnabled(true)

    h.controller.clearEnabled(startedAt = h.clock.now)

    assertFalse(h.settings().enabled)
  }

  @Test
  fun `a running monitor is not started a second time`() = runTest {
    val h = Harness(Settings(enabled = true))
    h.holder.setState(MonitorState.Running(emptySet()))

    val started = h.controller.startIfEnabled()

    assertTrue(started)
    assertEquals(0, h.platform.starts)
  }

  @Test
  fun `a pause with a duration sets its deadline from the clock`() = runTest {
    val h = Harness()

    val after = h.controller.pause(30)

    assertTrue(after.paused)
    assertEquals(NOW + 30 * 60_000L, after.pausedUntilEpochMillis)
  }

  @Test
  fun `a pause with no duration lasts until resumed and resume clears it`() = runTest {
    val h = Harness()

    val paused = h.controller.pause(null)
    val resumed = h.controller.resume()

    assertTrue(paused.paused)
    assertNull(paused.pausedUntilEpochMillis)
    assertFalse(resumed.paused)
    assertNull(resumed.pausedUntilEpochMillis)
  }

  @Test
  fun `toggling pauses a live monitor and resumes a paused one`() = runTest {
    val h = Harness()

    val first = h.controller.togglePause(15)
    val second = h.controller.togglePause(15)

    assertTrue(first.paused)
    assertFalse(second.paused)
  }

  @Test
  fun `an expired deadline is cleared and reported, a live or absent one is not`() = runTest {
    val expired = Harness(Settings(paused = true, pausedUntilEpochMillis = NOW - 1L))
    val live = Harness(Settings(paused = true, pausedUntilEpochMillis = NOW + 60_000L))
    val unpaused = Harness()

    assertTrue(expired.controller.expirePauseIfDue())
    assertFalse(expired.settings().paused)
    assertFalse(live.controller.expirePauseIfDue())
    assertTrue(live.settings().paused)
    assertFalse(unpaused.controller.expirePauseIfDue())
  }

  @Test
  fun `a dropped position lands on the active profile as a custom anchor and leaves the rest`() =
    runTest {
      val home = Profile(id = "home", name = "Home")
      val work = Profile(id = "work", name = "Work")
      val h = Harness(Settings(profiles = listOf(home, work), activeProfileId = "work"))

      h.controller.setCustomPosition(xDp = 120, yDp = 340)

      val after = h.settings()
      val active = after.profiles.first { it.id == "work" }
      assertEquals(Anchor.CUSTOM, active.position.anchor)
      assertEquals(120, active.position.customXdp)
      assertEquals(340, active.position.customYdp)
      assertEquals(home, after.profiles.first { it.id == "home" })
    }

  @Test
  fun `selecting a known profile switches to it and an unknown id changes nothing`() = runTest {
    val home = Profile(id = "home", name = "Home")
    val work = Profile(id = "work", name = "Work")
    val h = Harness(Settings(profiles = listOf(home, work), activeProfileId = "home"))

    h.controller.selectProfile("work")
    val switched = h.settings().activeProfileId
    h.controller.selectProfile("missing")

    assertEquals("work", switched)
    assertEquals("work", h.settings().activeProfileId)
  }

  private companion object {
    const val NOW: Long = 1_700_000_000_000L
  }
}
