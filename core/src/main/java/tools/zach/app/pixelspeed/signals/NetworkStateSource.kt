package tools.zach.app.pixelspeed.signals

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import tools.zach.app.pixelspeed.di.ApplicationScope
import tools.zach.app.pixelspeed.sampling.Transport

/**
 * The default network as the sampler and the glyph see it. [interfaceName] is null when offline.
 */
data class DefaultNetwork(val transport: Transport, val interfaceName: String?)

/** Interface names of every physical (non-VPN) network that is currently up. */
data class PhysicalInterfaces(val names: Set<String>)

/**
 * Two connectivity callbacks: the default network for transport and offline detection, and every
 * physical network for the interface set the counter source sums. A VPN's underlying network is not
 * queryable by apps, which is why the second callback exists. Each flow is shared, so every
 * collector reads one platform registration and sees the same network at the same moment.
 */
@Singleton
class NetworkStateSource
@Inject
constructor(
  private val connectivityManager: ConnectivityManager,
  @ApplicationScope private val scope: CoroutineScope,
) {

  /** The default network and its transport, updated as the platform switches or loses it. */
  val defaultNetwork: Flow<DefaultNetwork> = callbackFlow {
    val tracker = DefaultNetworkTracker<Network>()
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
          tracker.onCapabilities(network, caps.toTransport())?.let(::sendLatest)
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
          tracker.onLinkProperties(network, properties.interfaceName)?.let(::sendLatest)
        }

        override fun onLost(network: Network) {
          tracker.onLost(network)?.let(::sendLatest)
        }
      }
    sendLatest(DefaultNetwork(Transport.NONE, null))
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
    .shared()

  /** The interface names of every physical network the device holds, VPNs excluded. */
  val physicalInterfaces: Flow<PhysicalInterfaces> = callbackFlow {
    val names = mutableMapOf<Network, String>()
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
          val name = properties.interfaceName
          if (name == null) names.remove(network) else names[network] = name
          sendLatest(PhysicalInterfaces(names.values.toSet()))
        }

        override fun onLost(network: Network) {
          names.remove(network)
          sendLatest(PhysicalInterfaces(names.values.toSet()))
        }
      }
    val request =
      NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()
    sendLatest(PhysicalInterfaces(emptySet()))
    connectivityManager.registerNetworkCallback(request, callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
    .shared()

  /**
   * One registration for every collector, held [STOP_TIMEOUT_MS] past the last one so a service
   * restart reuses it; the replay is dropped with the registration, so no collector reads a stale
   * network.
   */
  private fun <T> Flow<T>.shared(): Flow<T> =
    conflate()
      .shareIn(
        scope,
        SharingStarted.WhileSubscribed(
          stopTimeoutMillis = STOP_TIMEOUT_MS,
          replayExpirationMillis = 0,
        ),
        replay = 1,
      )
      .distinctUntilChanged()
}

private fun NetworkCapabilities.toTransport(): Transport =
  when {
    hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
    else -> Transport.OTHER
  }
