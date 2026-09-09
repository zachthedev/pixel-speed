package tools.zach.app.pixelspeed.service

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.function.Consumer
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog

/** Asks the system to add the speed tile with its own dialog, which exists from API 33. */
object SpeedTileRequest {
  /**
   * Returns false when the version has no prompt; [onAdded] then never fires. [onAdded] receives
   * true when the tile is in Quick Settings afterwards, whether the dialog added it or it was
   * already there.
   */
  fun request(context: Context, onAdded: (Boolean) -> Unit): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val statusBarManager = context.getSystemService(StatusBarManager::class.java) ?: return false
    statusBarManager.requestAddTileService(
      ComponentName(context, SpeedTileService::class.java),
      context.getString(R.string.tile_speed_label),
      Icon.createWithResource(context, R.drawable.ic_readout),
      ContextCompat.getMainExecutor(context),
      Consumer { result ->
        AppLog.i("requestAddTileService result: $result")
        onAdded(
          result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        )
      },
    )
    return true
  }
}
