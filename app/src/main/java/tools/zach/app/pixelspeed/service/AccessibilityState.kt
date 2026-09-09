package tools.zach.app.pixelspeed.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Whether [StatusBarReaderService] is turned on in the system's accessibility settings. */
fun sideFlipServiceEnabled(context: Context): Boolean {
  val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
  val enabled =
    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
  return enabled.any {
    val info = it.resolveInfo.serviceInfo
    info.packageName == context.packageName && info.name == StatusBarReaderService::class.java.name
  }
}

/** The system's accessibility settings page, where the side flip service is turned on or off. */
fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
