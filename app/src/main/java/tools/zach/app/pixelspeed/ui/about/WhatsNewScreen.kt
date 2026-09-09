package tools.zach.app.pixelspeed.ui.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.ui.settings.InfoRow
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen
import tools.zach.app.pixelspeed.ui.settings.settingsCard

private const val CHANGELOG_ASSET: String = "CHANGELOG.md"

@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  // Null until the asset is read, so the empty state never flashes before a real list.
  val releases: List<Release>? by
    produceState<List<Release>?>(initialValue = null) {
      value =
        withContext(Dispatchers.IO) {
          try {
            val text = context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
            Changelog.parse(text)
          } catch (e: FileNotFoundException) {
            AppLog.i("No changelog asset yet; this is a development build")
            emptyList()
          } catch (e: IOException) {
            AppLog.e("Cannot read the changelog asset", e)
            emptyList()
          }
        }
    }

  SettingsScreen(title = stringResource(R.string.about_whats_new_screen_title), onBack = onBack) {
    val loaded = releases
    if (loaded == null) {
      return@SettingsScreen
    }
    if (loaded.isEmpty()) {
      settingsCard {
        row { shapes ->
          InfoRow(
            shapes = shapes,
            title = stringResource(R.string.about_whats_new_empty_title),
            subtitle = stringResource(R.string.about_whats_new_empty_subtitle),
          )
        }
      }
    } else {
      loaded.forEach { release ->
        val releaseLabel = release.date?.let { "${release.version}, $it" } ?: release.version
        release.sections.forEach { section ->
          settingsCard(title = "$releaseLabel: ${section.title}") {
            section.items.forEach { item ->
              row { shapes -> InfoRow(shapes = shapes, title = item) }
            }
          }
        }
      }
    }
  }
}
