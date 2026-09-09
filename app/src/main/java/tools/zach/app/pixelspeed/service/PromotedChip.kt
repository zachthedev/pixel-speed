package tools.zach.app.pixelspeed.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Whether this device can show a promoted notification's chip in the status bar. */
object PromotedChip {
  fun available(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
    if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.BAKLAVA_1) return false
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    return manager.canPostPromotedNotifications()
  }
}
