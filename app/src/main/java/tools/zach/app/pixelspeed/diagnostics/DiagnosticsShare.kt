package tools.zach.app.pixelspeed.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** The provider authority the manifest declares for the diagnostics cache folder. */
fun diagnosticsAuthority(context: Context): String = "${context.packageName}.diagnostics"

/**
 * A chooser that offers [file] as plain text through the diagnostics provider, with read access
 * granted to whichever app the user picks.
 */
fun diagnosticsShareIntent(context: Context, file: File, title: String): Intent {
  val uri: Uri = FileProvider.getUriForFile(context, diagnosticsAuthority(context), file)
  val send =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, title)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  return Intent.createChooser(send, title)
}
