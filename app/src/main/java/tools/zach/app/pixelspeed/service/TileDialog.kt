package tools.zach.app.pixelspeed.service

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.compose.runtime.getValue
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tools.zach.app.pixelspeed.overlay.OverlayComposeHost
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.state.MonitorState
import tools.zach.app.pixelspeed.ui.theme.PixelSpeedTheme
import tools.zach.app.pixelspeed.ui.tile.TileProfileDialog

/**
 * Builds the platform dialog a [android.service.quicksettings.TileService] shows, hosting the
 * Compose profile picker through the same owner graph the overlay window uses, since a Service
 * provides none. The window is transparent so the Compose surface draws the container.
 */
object TileDialog {
  fun create(
    context: Context,
    settings: Flow<Settings>,
    initialSettings: Settings,
    state: StateFlow<MonitorState>,
    onSelect: (String) -> Unit,
    onPause: (minutes: Int?) -> Unit,
    onResume: () -> Unit,
  ): Dialog {
    val host = OverlayComposeHost(context)
    val dialog = Dialog(context)
    host.setContent {
      val current by settings.collectAsStateWithLifecycle(initialSettings)
      val monitor by state.collectAsStateWithLifecycle()
      PixelSpeedTheme {
        TileProfileDialog(
          settings = current,
          monitor = monitor,
          onSelect = onSelect,
          onPause = {
            onPause(it)
            dialog.dismiss()
          },
          onResume = {
            onResume()
            dialog.dismiss()
          },
        )
      }
    }
    dialog.setContentView(host.view)
    dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    dialog.setOnShowListener { host.show() }
    // The owner graph dies with the dialog, whichever way it goes: a dismiss fires both the
    // dismiss listener and the window detach, and the caller's own dismiss listener replaces this
    // one, so the detach path is what runs then. The body runs once whichever path fires first.
    var tornDown = false
    val tearDown = {
      if (!tornDown) {
        tornDown = true
        host.destroy()
        host.clearViewModels()
      }
    }
    dialog.setOnDismissListener { tearDown() }
    dialog.window
      ?.decorView
      ?.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
          override fun onViewAttachedToWindow(v: View) = Unit

          override fun onViewDetachedFromWindow(v: View) = tearDown()
        }
      )
    return dialog
  }
}
