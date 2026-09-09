package tools.zach.app.pixelspeed.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import tools.zach.app.pixelspeed.di.ApplicationScope

/** Whether Battery Saver is on. Shared, so every collector rides one receiver. */
@Singleton
class PowerSaveSource
@Inject
constructor(
  @ApplicationContext private val context: Context,
  @ApplicationScope scope: CoroutineScope,
) {

  private val powerManager: PowerManager = context.getSystemService(PowerManager::class.java)

  /** True while Battery Saver is on. */
  val powerSaveOn: StateFlow<Boolean> = callbackFlow {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          sendLatest(powerManager.isPowerSaveMode)
        }
      }
    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    sendLatest(powerManager.isPowerSaveMode)
    awaitClose { context.unregisterReceiver(receiver) }
  }
    .conflate()
    .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), powerManager.isPowerSaveMode)
}
