package tools.zach.app.pixelspeed.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat

/**
 * The device's 24-hour clock setting, read again when the platform announces a change to it. The
 * Compose Preview renderer has no broadcast delivery and keeps the first read.
 */
@Composable
internal fun rememberHour24(): Boolean {
  val context = LocalContext.current
  var hour24 by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
  if (LocalInspectionMode.current) return hour24
  DisposableEffect(context) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          hour24 = DateFormat.is24HourFormat(context)
        }
      }
    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(Intent.ACTION_TIME_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    onDispose { context.unregisterReceiver(receiver) }
  }
  return hour24
}
