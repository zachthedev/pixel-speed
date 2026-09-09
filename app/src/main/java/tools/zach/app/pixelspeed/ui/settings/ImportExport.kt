package tools.zach.app.pixelspeed.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.settings.Settings
import tools.zach.app.pixelspeed.settings.SettingsImport
import tools.zach.app.pixelspeed.settings.isControlOrFormat

/** A launcher that writes [settingsProvider]'s current value to a file the user picks. */
@Composable
fun rememberSettingsExportLauncher(
  settingsProvider: () -> Settings,
  onFailure: (String) -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val resources = LocalResources.current
  val exportFailed: (Exception) -> Unit = { e ->
    AppLog.e("Failed to export settings", e)
    onFailure(
      resources.getString(R.string.settings_system_export_failed, e.message.orEmpty().printable())
    )
  }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
      uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      try {
        val stream = context.contentResolver.openOutputStream(uri)
        if (stream == null) {
          onFailure(
            resources.getString(R.string.settings_system_export_failed, uri.toString().printable())
          )
          return@rememberLauncherForActivityResult
        }
        stream.use { it.write(SettingsImport.serialize(settingsProvider()).toByteArray()) }
      } catch (e: IOException) {
        exportFailed(e)
      } catch (e: SecurityException) {
        // A provider can refuse a URI it handed out a moment ago.
        exportFailed(e)
      } catch (e: IllegalArgumentException) {
        // A provider can reject the URI's shape once it is opened.
        exportFailed(e)
      }
    }
  val fileName = resources.getString(R.string.settings_system_export_file_name)
  return { launcher.launch(fileName) }
}

/**
 * Reads at most [SettingsImport.MAX_FILE_BYTES] bytes and returns null once the stream runs past
 * it, so a huge file never lands in memory before the parser can refuse it.
 */
private fun readCapped(stream: java.io.InputStream): String? {
  val cap = SettingsImport.MAX_FILE_BYTES
  val buffer = java.io.ByteArrayOutputStream()
  val chunk = ByteArray(16 * 1024)
  while (true) {
    val read = stream.read(chunk)
    if (read < 0) break
    if (buffer.size() + read > cap) return null
    buffer.write(chunk, 0, read)
  }
  return buffer.toString(Charsets.UTF_8.name())
}

/** A launcher that reads a user-picked file, parses it, and reports the outcome. */
@Composable
fun rememberSettingsImportLauncher(
  onImported: (Settings) -> Unit,
  onFailure: (String) -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val resources = LocalResources.current
  val scope = rememberCoroutineScope()
  // The parser's own messages quote the file, so the line is bounded and made printable here,
  // where every failure passes, rather than at each message.
  val fail: (String) -> Unit = { detail ->
    onFailure(resources.getString(R.string.settings_system_import_failed, detail.printable()))
  }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      // A document provider can stream from the cloud or block, so the read and the parse leave
      // the main thread and give up after a bound. A blocking read on a provider's descriptor
      // ignores both cancellation and interruption, so the bound is enforced by closing the
      // stream under it, which ends the read with an IOException.
      scope.launch {
        var stream: InputStream? = null
        val watchdog = launch {
          delay(IMPORT_TIMEOUT_MS)
          AppLog.w("The settings file did not arrive within $IMPORT_TIMEOUT_MS ms")
          stream?.close()
        }
        val outcome: Result<Settings> =
          try {
            withContext(Dispatchers.IO) {
              stream = context.contentResolver.openInputStream(uri)
              val text = stream?.use { readCapped(it) }
              if (text == null) {
                Result.failure(
                  TooLargeException(
                    resources.getString(
                      R.string.settings_system_import_too_large,
                      SettingsImport.MAX_FILE_BYTES,
                    )
                  )
                )
              } else {
                SettingsImport.parse(text)
              }
            }
          } catch (e: IOException) {
            AppLog.e("Failed to read the settings file", e)
            Result.failure(e)
          } catch (e: SecurityException) {
            // A provider can refuse a URI it handed out a moment ago.
            AppLog.e("Failed to read the settings file", e)
            Result.failure(e)
          } catch (e: IllegalArgumentException) {
            // A provider can reject the URI's shape once it is opened.
            AppLog.e("Failed to read the settings file", e)
            Result.failure(e)
          } finally {
            watchdog.cancel()
          }
        outcome.onSuccess(onImported).onFailure { e -> fail(e.message ?: "") }
      }
    }
  return { launcher.launch(arrayOf("application/json", "text/plain")) }
}

/** A file past the byte cap; the message is the user-facing reason. */
private class TooLargeException(message: String) : Exception(message)

private const val IMPORT_TIMEOUT_MS: Long = 15_000L

/** A failure line long enough to name a field and a value, short enough to read. */
private const val FAILURE_DETAIL_LENGTH: Int = 200

/** Provider or parser text as a snackbar can carry it: bounded, with no control characters. */
private fun String.printable(): String = filterNot(::isControlOrFormat).take(FAILURE_DETAIL_LENGTH)
