package tools.zach.app.pixelspeed.service

import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.state.Readout
import tools.zach.app.pixelspeed.state.ReadoutSink

/**
 * Posts each readout as the foreground notification. The terminal states are the service's own: it
 * removes the foreground notification on Off and posts the permission notification itself.
 */
class NotificationSink(
  private val notification: MonitorNotification,
  private val mapper: NotificationModelMapper,
) : ReadoutSink {
  override suspend fun publish(readout: Readout) {
    if (readout.state == MonitorState.Off || readout.state == MonitorState.NeedsPermission) return
    notification.post(mapper.map(readout.state, readout.sample, readout.profile))
  }

  override fun wantsSamples(readout: Readout): Boolean =
    mapper.showsSpeed(readout.state, readout.profile)
}
