package tools.zach.app.pixelspeed.signals

import tools.zach.app.pixelspeed.sampling.Transport

/**
 * Pairs the two callbacks the platform reports for a default network into one [DefaultNetwork]. The
 * capabilities and the link properties arrive separately, each named for a network; only a pair
 * from the same network describes one default network, so nothing is reported until both halves of
 * the current one arrived, and a stale network's loss is ignored. The platform follows every
 * network it announces with both callbacks, so a half never waits alone. Pure over any key type
 * [N], so the pairing is table-tested without a connectivity service.
 */
class DefaultNetworkTracker<N : Any> {
  private var current: N? = null
  private var transport: Transport? = null
  private var interfaceName: String? = null
  private var linkKnown: Boolean = false

  /** The default network to report after this capabilities callback, or null for nothing yet. */
  fun onCapabilities(network: N, transport: Transport): DefaultNetwork? {
    track(network)
    this.transport = transport
    return pair()
  }

  /** The default network to report after this link callback, or null for nothing yet. */
  fun onLinkProperties(network: N, interfaceName: String?): DefaultNetwork? {
    track(network)
    this.interfaceName = interfaceName
    linkKnown = true
    return pair()
  }

  /** Offline when the current network is lost; null for a network that was already superseded. */
  fun onLost(network: N): DefaultNetwork? {
    if (network != current) return null
    current = null
    transport = null
    interfaceName = null
    linkKnown = false
    return DefaultNetwork(Transport.NONE, null)
  }

  private fun track(network: N) {
    if (network == current) return
    current = network
    transport = null
    interfaceName = null
    linkKnown = false
  }

  private fun pair(): DefaultNetwork? {
    val transport = transport ?: return null
    if (!linkKnown) return null
    return DefaultNetwork(transport, interfaceName)
  }
}
