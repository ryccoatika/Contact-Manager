package com.ryccoatika.contactmanager.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import kotlinx.coroutines.launch

private data class Library(val name: String, val license: String, val url: String)

// The third-party libraries this app ships. Keep in sync with gradle/libs.versions.toml
// when dependencies change (build-only tooling like the changelog plugin is excluded).
private val LIBRARIES = listOf(
    Library("Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
    Library("AndroidX (Core, Lifecycle, Navigation, DataStore)", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Library("Material 3 & Material Icons", "Apache License 2.0", "https://m3.material.io"),
    Library("Kotlin & Coroutines", "Apache License 2.0", "https://kotlinlang.org"),
    Library("Dagger Hilt", "Apache License 2.0", "https://dagger.dev/hilt"),
    Library("Coil", "Apache License 2.0", "https://coil-kt.github.io/coil"),
    Library("Firebase Android SDK", "Google APIs Terms of Service", "https://firebase.google.com/terms"),
    Library("Google Play In-App Review", "Play Core Software Development Kit Terms", "https://developer.android.com/guide/playcore/in-app-review"),
    Library("Google Play In-App Updates", "Play Core Software Development Kit Terms", "https://developer.android.com/guide/playcore/in-app-updates"),
    Library("Manrope", "SIL Open Font License 1.1", "https://github.com/sharanda/manrope"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    TrackScreenView("licenses")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noBrowser = stringResource(R.string.licenses_no_browser)

    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(noBrowser) }
        }
    }

    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.licenses_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(Modifier.fillMaxWidth()) {
                LIBRARIES.forEachIndexed { index, library ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                    LibraryRow(library, onClick = { openUrl(library.url) })
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(library: Library, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(library.name, style = MaterialTheme.typography.titleMedium)
            Text(
                library.license,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LicensesScreenPreview() {
    ContactManagerTheme {
        LicensesScreen(onBack = {})
    }
}
