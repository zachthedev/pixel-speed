package tools.zach.app.pixelspeed.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import tools.zach.app.pixelspeed.OpenSourceLicenses
import tools.zach.app.pixelspeed.Privacy
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.WhatsNew
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.ui.settings.InfoRow
import tools.zach.app.pixelspeed.ui.settings.LinkRow
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen
import tools.zach.app.pixelspeed.ui.settings.settingsCard

private const val SOURCE_URL: String = "https://github.com/zachthedev/pixel-speed"
private const val LICENSE_URL: String = "$SOURCE_URL/blob/main/LICENSE"

@Composable
fun AboutScreen(onBack: () -> Unit, onNavigate: (NavKey) -> Unit) {
  val context = LocalContext.current
  val versionName: String? = remember {
    try {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
      AppLog.w("Own package missing while reading the version", e)
      null
    }
  }
  val version: String =
    versionName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.about_version_dev_build)

  fun openUrl(url: String) {
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
      AppLog.e("No activity to open $url", e)
    }
  }

  val appCardTitle = stringResource(R.string.about_card_app)
  val moreCardTitle = stringResource(R.string.about_card_more)

  SettingsScreen(title = stringResource(R.string.settings_title_about), onBack = onBack) {
    settingsCard(title = appCardTitle) {
      row { shapes ->
        InfoRow(
          shapes = shapes,
          title = stringResource(R.string.about_version_title),
          subtitle = version,
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.about_license_title),
          onClick = { openUrl(LICENSE_URL) },
          subtitle = stringResource(R.string.about_license_subtitle),
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.about_source_title),
          onClick = { openUrl(SOURCE_URL) },
          subtitle = SOURCE_URL.removePrefix("https://"),
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.about_privacy_title),
          onClick = { onNavigate(Privacy) },
          subtitle = stringResource(R.string.about_privacy_subtitle),
        )
      }
    }
    settingsCard(title = moreCardTitle) {
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.about_whats_new_title),
          onClick = { onNavigate(WhatsNew) },
          subtitle = stringResource(R.string.about_whats_new_subtitle),
        )
      }
      row { shapes ->
        LinkRow(
          shapes = shapes,
          title = stringResource(R.string.about_licenses_title),
          onClick = { onNavigate(OpenSourceLicenses) },
          subtitle = stringResource(R.string.about_licenses_subtitle),
        )
      }
    }
  }
}
