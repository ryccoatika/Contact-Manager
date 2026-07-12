package com.ryccoatika.contactmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ryccoatika.contactmanager.ui.accounts.AccountsScreen
import com.ryccoatika.contactmanager.ui.detail.DetailScreen
import com.ryccoatika.contactmanager.ui.duplicates.DuplicatesScreen
import com.ryccoatika.contactmanager.ui.editor.EditorScreen
import com.ryccoatika.contactmanager.ui.home.HomeScreen
import com.ryccoatika.contactmanager.ui.permission.PermissionGate

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{contactId}"
    const val EDITOR_NEW = "editor"
    const val EDITOR_EDIT = "editor/{rawContactId}"
    const val ACCOUNTS = "accounts"
    const val DUPLICATES = "duplicates"

    /** Home backstack-entry key used by Accounts to hand back an account filter. */
    const val FILTER_ACCOUNT_KEY = "filterAccountKey"

    fun detail(contactId: Long) = "detail/$contactId"
    fun editorEdit(rawContactId: Long) = "editor/$rawContactId"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    PermissionGate {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { entry ->
                val pendingFilter by entry.savedStateHandle
                    .getStateFlow<String?>(Routes.FILTER_ACCOUNT_KEY, null)
                    .collectAsStateWithLifecycle()
                HomeScreen(
                    onContactClick = { contactId ->
                        navController.navigate(Routes.detail(contactId))
                    },
                    onAddClick = { navController.navigate(Routes.EDITOR_NEW) },
                    onAccountsClick = { navController.navigate(Routes.ACCOUNTS) },
                    onDuplicatesClick = { navController.navigate(Routes.DUPLICATES) },
                    pendingFilterAccountKey = pendingFilter,
                    onPendingFilterConsumed = {
                        entry.savedStateHandle[Routes.FILTER_ACCOUNT_KEY] = null
                    },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("contactId") { type = NavType.LongType }),
            ) {
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditRawContact = { rawContactId ->
                        navController.navigate(Routes.editorEdit(rawContactId))
                    },
                )
            }
            composable(Routes.EDITOR_NEW) {
                EditorScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.EDITOR_EDIT,
                arguments = listOf(navArgument("rawContactId") { type = NavType.LongType }),
            ) {
                EditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DUPLICATES) {
                DuplicatesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onBack = { navController.popBackStack() },
                    onAccountClick = { accountKey ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(Routes.FILTER_ACCOUNT_KEY, accountKey)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
