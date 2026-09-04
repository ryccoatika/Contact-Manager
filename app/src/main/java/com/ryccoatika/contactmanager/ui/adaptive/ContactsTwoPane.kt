package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.Routes
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.detail.DetailScreen
import com.ryccoatika.contactmanager.ui.editor.EditorScreen
import com.ryccoatika.contactmanager.ui.home.HomeScreen

private const val PANE_EMPTY = "pane_empty"

/**
 * Contacts list-detail: the reused [HomeScreen] list on the left, and a nested
 * [NavHost] on the right that hosts the empty placeholder / detail / editor.
 * The nested graph gives [DetailScreen]/[EditorScreen] their ids via nav args,
 * so no ViewModel changes are needed.
 */
@Composable
fun ContactsTwoPane() {
    val detailNav = rememberNavController()
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(360.dp)) {
            HomeScreen(
                onContactClick = { contact ->
                    detailNav.navigate(Routes.detail(contact.contactId)) {
                        popUpTo(PANE_EMPTY)
                        launchSingleTop = true
                    }
                },
                onAddClick = { detailNav.navigate(Routes.EDITOR_NEW) { launchSingleTop = true } },
                onSettingsClick = {},
                onDuplicatesClick = {},
                embedded = true,
            )
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = detailNav, startDestination = PANE_EMPTY) {
                composable(PANE_EMPTY) { EmptyDetail() }
                composable(
                    Routes.DETAIL,
                    arguments = listOf(
                        navArgument("contactId") { type = NavType.LongType },
                        navArgument("name") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("photo") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    DetailScreen(
                        onBack = {
                            detailNav.navigate(PANE_EMPTY) { popUpTo(PANE_EMPTY) { inclusive = true } }
                        },
                        onEditRawContact = { rawId ->
                            detailNav.navigate(Routes.editorEdit(rawId)) { launchSingleTop = true }
                        },
                        embedded = true,
                    )
                }
                composable(Routes.EDITOR_NEW) {
                    EditorScreen(onBack = { detailNav.popBackStack() })
                }
                composable(
                    Routes.EDITOR_EDIT,
                    arguments = listOf(navArgument("rawContactId") { type = NavType.LongType }),
                ) {
                    EditorScreen(onBack = { detailNav.popBackStack() })
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Empty detail", widthDp = 720, heightDp = 800)
@androidx.compose.ui.tooling.preview.Preview(
    name = "Empty detail · dark",
    widthDp = 720,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun EmptyDetailPreview() {
    com.ryccoatika.contactmanager.ui.theme
        .ContactManagerTheme { EmptyDetail() }
}

/** Friendly placeholder shown in the detail pane before a contact is picked. */
@Composable
private fun EmptyDetail() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContactAvatar(name = "?", photoUri = null, size = 96.dp)
        Text(
            stringResource(R.string.tablet_select_contact),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
