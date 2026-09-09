package tools.zach.app.pixelspeed.state

import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.settings.Profile

/**
 * One published readout: the monitor's state, the newest sample if any, and the active profile. A
 * sink gets the profile alone, never the rest of the user's settings.
 */
data class Readout(val state: MonitorState, val sample: SpeedSample?, val profile: Profile)

/**
 * A surface the service pushes readouts to. The overlay reads [MonitorStateHolder] directly, since
 * it composes from the same flows; push surfaces such as the notification, or a watch publisher,
 * implement this and receive every readout the service emits, including [MonitorState.Off] and
 * [MonitorState.NeedsPermission], so each sink decides what a stopped monitor means for it.
 *
 * [ReadoutPublisher] delivers to every sink at once on a background dispatcher: a sink that throws
 * is logged and skipped for that readout, a sink that stays suspended too long is cut off, and a
 * sink that blocks its thread delays only itself. Nothing stops the other sinks or the service.
 */
interface ReadoutSink {
  /** Shows [readout] on this surface. */
  suspend fun publish(readout: Readout)

  /** True when this sink wants every sample under this profile, not only state changes. */
  fun wantsSamples(readout: Readout): Boolean = false

  /**
   * The service stopped. A sink releases what it holds open and stays usable, since a sink bound in
   * the application scope outlives the service and is published to again after the next start. A
   * publish already in flight on another thread can land after this call.
   */
  fun onStopped() = Unit
}
