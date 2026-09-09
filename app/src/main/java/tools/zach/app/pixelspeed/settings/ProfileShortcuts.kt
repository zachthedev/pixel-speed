package tools.zach.app.pixelspeed.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.service.ShortcutActions
import tools.zach.app.pixelspeed.service.ShortcutActivity

/** Publishes and removes the launcher shortcut that switches directly to one profile. */
object ProfileShortcuts {
  private const val ID_PREFIX: String = "profile-"

  fun shortcutId(profileId: String): String = ID_PREFIX + profileId

  /** True while a launcher shortcut for [profileId] is published. */
  fun isPublished(context: Context, profileId: String): Boolean =
    ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == shortcutId(profileId) }

  /** Publishes or updates the shortcut "Switch to <name>" for [profile]; false when refused. */
  fun publish(context: Context, profile: Profile): Boolean {
    val intent =
      Intent(context, ShortcutActivity::class.java)
        .setAction(ShortcutActions.SHORTCUT_PROFILE)
        .putExtra(ShortcutActions.EXTRA_PROFILE_ID, profile.id)
    val shortcut =
      ShortcutInfoCompat.Builder(context, shortcutId(profile.id))
        .setShortLabel(context.getString(R.string.settings_profiles_shortcut_label, profile.name))
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    return try {
      ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    } catch (e: IllegalArgumentException) {
      AppLog.w("Could not publish the launcher shortcut for profile ${profile.id}", e)
      false
    }
  }

  fun remove(context: Context, profileId: String) {
    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(profileId)))
  }

  /** Whether any profile shortcut is published: the cheap question before a settings read. */
  fun anyPublished(context: Context): Boolean =
    ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id.startsWith(ID_PREFIX) }

  private val reconcileLock = Mutex()

  /**
   * Brings the published shortcuts back in line with [profiles] after a write that could have
   * renamed or dropped any of them, or after a locale change that renamed every label: a shortcut
   * whose profile is gone is removed, and one whose label no longer matches is republished. Two
   * callers never interleave, so a stale snapshot cannot republish what the other just removed.
   * Returns the profile ids whose republish the platform refused, which its background quota does
   * after ten updates a day; a caller retries those once the app is in the foreground.
   */
  suspend fun reconcile(context: Context, profiles: List<Profile>): List<String> =
    reconcileLock.withLock {
      val byId = profiles.associateBy { shortcutId(it.id) }
      val refused = mutableListOf<String>()
      ShortcutManagerCompat.getDynamicShortcuts(context)
        .filter { it.id.startsWith(ID_PREFIX) }
        .forEach { shortcut ->
          val profile = byId[shortcut.id]
          when {
            profile == null ->
              ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcut.id))
            shortcut.shortLabel.toString() !=
              context.getString(R.string.settings_profiles_shortcut_label, profile.name) ->
              if (!publish(context, profile)) refused += profile.id
          }
        }
      if (refused.isNotEmpty()) {
        AppLog.w("The platform refused to relabel ${refused.size} launcher shortcut(s): $refused")
      }
      refused
    }
}
