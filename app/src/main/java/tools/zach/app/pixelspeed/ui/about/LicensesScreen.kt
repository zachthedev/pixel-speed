package tools.zach.app.pixelspeed.ui.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import tools.zach.app.pixelspeed.R
import tools.zach.app.pixelspeed.ui.settings.SettingsTopBar
import tools.zach.app.pixelspeed.ui.settings.contentWidthCap

@Composable
fun LicensesScreen(onBack: () -> Unit) {
  val libraries by produceLibraries(R.raw.aboutlibraries)
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      SettingsTopBar(stringResource(R.string.about_licenses_screen_title), onBack, scrollBehavior)
    },
  ) { innerPadding ->
    val loaded = libraries ?: return@Scaffold
    // The library draws square rows with no gap, so the whole list sits in one card instead.
    Surface(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .wrapContentWidth()
          .contentWidthCap()
          .padding(horizontal = 16.dp),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceBright,
    ) {
      LibrariesContainer(
        libraries = loaded,
        modifier = Modifier.fillMaxSize(),
        // The list paints its own ground under any content padding, so the card gives it none.
        contentPadding = PaddingValues(0.dp),
        variantColors =
          LibraryDefaults.m3VariantColors(rowBackground = MaterialTheme.colorScheme.surfaceBright),
      )
    }
  }
}
