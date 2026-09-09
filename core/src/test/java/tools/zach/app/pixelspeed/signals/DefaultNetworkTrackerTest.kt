package tools.zach.app.pixelspeed.signals

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.sampling.Transport

class DefaultNetworkTrackerTest {

  /** One platform callback, as the tracker sees it. */
  internal sealed interface Event {
    data class Caps(val network: String, val transport: Transport) : Event

    data class Link(val network: String, val interfaceName: String?) : Event

    data class Lost(val network: String) : Event
  }

  companion object {
    private val wifi = DefaultNetwork(Transport.WIFI, "wlan0")
    private val cell = DefaultNetwork(Transport.CELLULAR, "rmnet0")
    private val offline = DefaultNetwork(Transport.NONE, null)

    @JvmStatic
    fun sequences(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "registration replays capabilities first, nothing until the link arrives",
          listOf(Event.Caps("a", Transport.WIFI), Event.Link("a", "wlan0")),
          listOf(null, wifi),
        ),
        Arguments.of(
          "link before capabilities pairs the same way",
          listOf(Event.Link("a", "wlan0"), Event.Caps("a", Transport.WIFI)),
          listOf(null, wifi),
        ),
        Arguments.of(
          "a handover whose old network is lost last never reads as offline",
          listOf(
            Event.Caps("a", Transport.WIFI),
            Event.Link("a", "wlan0"),
            Event.Caps("b", Transport.CELLULAR),
            Event.Link("b", "rmnet0"),
            Event.Lost("a"),
          ),
          listOf(null, wifi, null, cell, null),
        ),
        Arguments.of(
          "a handover whose old network is lost first passes through offline",
          listOf(
            Event.Caps("a", Transport.WIFI),
            Event.Link("a", "wlan0"),
            Event.Lost("a"),
            Event.Caps("b", Transport.CELLULAR),
            Event.Link("b", "rmnet0"),
          ),
          listOf(null, wifi, offline, null, cell),
        ),
        Arguments.of(
          "a new network's capabilities alone never pair with the old link",
          listOf(
            Event.Caps("a", Transport.WIFI),
            Event.Link("a", "wlan0"),
            Event.Caps("b", Transport.CELLULAR),
          ),
          listOf(null, wifi, null),
        ),
        Arguments.of(
          "losing the current network reads as offline",
          listOf(Event.Caps("a", Transport.WIFI), Event.Link("a", "wlan0"), Event.Lost("a")),
          listOf(null, wifi, offline),
        ),
        Arguments.of(
          "losing a network that was never current is ignored",
          listOf(Event.Caps("a", Transport.WIFI), Event.Link("a", "wlan0"), Event.Lost("z")),
          listOf(null, wifi, null),
        ),
        Arguments.of(
          "a link with no interface name reads as offline on that network",
          listOf(Event.Caps("a", Transport.WIFI), Event.Link("a", null)),
          listOf(null, DefaultNetwork(Transport.WIFI, null)),
        ),
        Arguments.of(
          "a later link update on the same network is reported",
          listOf(
            Event.Caps("a", Transport.WIFI),
            Event.Link("a", "wlan0"),
            Event.Link("a", "wlan1"),
          ),
          listOf(null, wifi, DefaultNetwork(Transport.WIFI, "wlan1")),
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sequences")
  internal fun `each callback reports exactly the network the pairing allows`(
    name: String,
    events: List<Event>,
    expected: List<DefaultNetwork?>,
  ) {
    val tracker = DefaultNetworkTracker<String>()
    val reported = events.map { event ->
      when (event) {
        is Event.Caps -> tracker.onCapabilities(event.network, event.transport)
        is Event.Link -> tracker.onLinkProperties(event.network, event.interfaceName)
        is Event.Lost -> tracker.onLost(event.network)
      }
    }
    assertEquals(expected, reported, name)
  }
}
