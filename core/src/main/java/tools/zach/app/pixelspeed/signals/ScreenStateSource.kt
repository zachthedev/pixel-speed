package tools.zach.app.pixelspeed.signals

import android.app.KeyguardManager
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

/** Whether the display is interactive and whether the lock screen is showing. */
data class ScreenState(val interactive: Boolean, val locked: Boolean)

/**
 * Screen and keyguard transitions as one shared flow, so every collector rides one receiver. Screen
 * off stops sampling; the keyguard hides the overlay while the platform hides it anyway, so the
 * state machine names the reason.
 *
 * The user-present broadcast itself means unlocked: a read of [KeyguardManager.isKeyguardLocked]
 * inside that broadcast can still say locked on a phone that unlocks by face or finger, and no
 * later broadcast corrects it. The platform's lock-state listener is not an option, since its
 * permission is granted to system apps only.
 */
@Singleton
class ScreenStateSource
@Inject
constructor(
  @ApplicationContext private val context: Context,
  @ApplicationScope scope: CoroutineScope,
) {

  private val powerManager: PowerManager = context.getSystemService(PowerManager::class.java)
  private val keyguardManager: KeyguardManager =
    context.getSystemService(KeyguardManager::class.java)

  private fun current(locked: Boolean = keyguardManager.isKeyguardLocked): ScreenState =
    ScreenState(interactive = powerManager.isInteractive, locked = locked)

  /** Whether the screen is interactive and whether the keyguard is showing. */
  val state: StateFlow<ScreenState> = callbackFlow {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val state =
            if (intent.action == Intent.ACTION_USER_PRESENT) current(locked = false) else current()
          sendLatest(state)
        }
      }
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_USER_PRESENT)
        // A fold or unfold re-shows the keyguard without a screen-off, so the state re-reads then.
        addAction(Intent.ACTION_CONFIGURATION_CHANGED)
      }
    // Exported because SystemUI, an ordinary app uid, sends the user-present broadcast on recent
    // Android, and a receiver that is not exported refuses every broadcast from another app. Every
    // action in the filter must be a protected broadcast, which only the platform can send; an
    // unprotected one would let any app spoof the whole state, unlocked included.
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    sendLatest(current())
    awaitClose { context.unregisterReceiver(receiver) }
  }
    .conflate()
    .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), current())
}
