package tools.zach.app.pixelspeed.ui.help

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.diagnostics.DiagnosticsViewModel
import tools.zach.app.pixelspeed.diagnostics.diagnosticsShareIntent
import tools.zach.app.pixelspeed.log.AppLog
import tools.zach.app.pixelspeed.ui.settings.SettingsScreen

private const val ISSUES_URL: String = "https://github.com/zachthedev/pixel-speed/issues/new/choose"

/**
 * What a useful report says, what the diagnostics file holds and never holds, and the two actions:
 * share the file, open the issue form. The file is built on demand and offered through the share
 * sheet; nothing leaves the phone on its own.
 */
@Composable
fun HelpReportProblemScreen(onBack: () -> Unit) {
  val viewModel: DiagnosticsViewModel = hiltViewModel()
  val busy: Boolean by viewModel.busy.collectAsState()
  val context = LocalContext.current
  val resources = LocalResources.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val shareTitle: String = stringResource(R.string.help_report_share_action)

  // The platform can refuse any of the reads behind the report, so the page reports a failure
  // rather than becoming the crash it was meant to describe.
  @Suppress("TooGenericExceptionCaught")
  fun share() {
    if (viewModel.busy.value) return
    scope.launch {
      try {
        val file = viewModel.write()
        context.startActivity(diagnosticsShareIntent(context, file, shareTitle))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLog.e("Cannot build or share the diagnostics file", e)
        snackbar.showSnackbar(
          resources.getString(R.string.help_report_share_failed, e.message.orEmpty())
        )
      }
    }
  }

  fun openIssues() {
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW, ISSUES_URL.toUri()))
    } catch (e: ActivityNotFoundException) {
      AppLog.e("No activity to open $ISSUES_URL", e)
    }
  }

  Box(Modifier.fillMaxSize()) {
    SettingsScreen(
      title = stringResource(R.string.help_link_report_problem_title),
      onBack = onBack,
    ) {
      helpSection(
        titleRes = R.string.help_card_what_helps,
        entries =
          listOf(
            HelpEntry(R.string.help_report_describe_title, R.string.help_report_describe_subtitle),
            HelpEntry(R.string.help_report_picture_title, R.string.help_report_picture_subtitle),
          ),
      )
      helpSection(
        titleRes = R.string.help_card_diagnostics_file,
        entries =
          listOf(
            HelpEntry(R.string.help_report_holds_title, R.string.help_report_holds_subtitle),
            HelpEntry(R.string.help_report_private_title, R.string.help_report_private_subtitle),
          ),
      )
      item {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Button(onClick = ::share, enabled = !busy) {
            Text(
              if (busy) stringResource(R.string.help_report_share_working)
              else stringResource(R.string.help_report_share_action)
            )
          }
          TextButton(onClick = ::openIssues) {
            Text(stringResource(R.string.help_report_issue_action))
          }
          Text(
            text = stringResource(R.string.help_report_issue_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    // Outside the list, so a failure shows and is announced wherever the list is scrolled.
    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
  }
}
