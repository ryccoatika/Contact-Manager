package com.ryccoatika.contactmanager.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.ui.about.AboutScreen
import com.ryccoatika.contactmanager.ui.about.ContactDeveloperScreen
import com.ryccoatika.contactmanager.ui.accounts.AccountsScreen
import com.ryccoatika.contactmanager.ui.adaptive.AdaptiveApp
import com.ryccoatika.contactmanager.ui.detail.DetailScreen
import com.ryccoatika.contactmanager.ui.duplicates.DuplicatesScreen
import com.ryccoatika.contactmanager.ui.editor.EditorScreen
import com.ryccoatika.contactmanager.ui.home.HomeScreen
import com.ryccoatika.contactmanager.ui.onboarding.OnboardingScreen
import com.ryccoatika.contactmanager.ui.onboarding.OnboardingViewModel
import com.ryccoatika.contactmanager.ui.permission.PermissionGate
import com.ryccoatika.contactmanager.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"

    // name/photo ride along as optional args so the detail hero avatar can render
    // immediately — before the full contact loads — which the open shared-element
    // transition needs as its morph target from the very first frame.
    const val DETAIL = "detail/{contactId}?name={name}&photo={photo}"
    const val EDITOR_NEW = "editor"
    const val EDITOR_EDIT = "editor/{rawContactId}"
    const val ACCOUNTS = "accounts"
    const val DUPLICATES = "duplicates"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val CONTACT_DEVELOPER = "contact_developer"

    /** Home backstack-entry key used by Accounts to hand back an account filter. */
    const val FILTER_ACCOUNT_KEY = "filterAccountKey"

    fun detail(contactId: Long, name: String = "", photoUri: String? = null) =
        "detail/$contactId?name=${Uri.encode(name)}&photo=${Uri.encode(photoUri.orEmpty())}"

    fun editorEdit(rawContactId: Long) = "editor/$rawContactId"
}

@Composable
fun AppNav(onboardingViewModel: OnboardingViewModel = hiltViewModel()) {
    val showOnboarding by onboardingViewModel.showOnboarding.collectAsStateWithLifecycle()
    when (showOnboarding) {
        // Brief blank while the flag loads — avoids flashing the app then onboarding.
        null -> Surface(color = MaterialTheme.colorScheme.background) {}

        true -> OnboardingScreen(onDone = onboardingViewModel::complete)

        false -> AdaptiveApp()
    }
}

// --- Navigation motion ------------------------------------------------------
// Forward navigation "spreads" open: the incoming screen fades in while scaling
// up from 92%, and the outgoing screen holds its position and fades under. The
// hold matters — it lets the shared contact avatar read clearly as it morphs
// from the list row into the detail header (see HomeScreen / DetailScreen). The
// editor spreads from the bottom-right, growing out of the FAB that launched it.
private const val NAV_ANIM_MS = 320

// The editor spread is deliberately quicker so opening "add contact" snaps open
// rather than dragging while the heavy form composes.
private const val FAB_ENTER_MS = 220
private const val FAB_EXIT_MS = 190
private val navEasing = FastOutSlowInEasing

// Roughly the FAB's centre (bottom-end, inset 16dp) as a fraction of the screen.
private val fabOrigin = TransformOrigin(0.9f, 0.93f)

private fun spreadEnter(): EnterTransition =
    fadeIn(tween(NAV_ANIM_MS)) + scaleIn(tween(NAV_ANIM_MS, easing = navEasing), initialScale = 0.92f)

private fun spreadExit(): ExitTransition = fadeOut(tween(NAV_ANIM_MS))

private fun spreadPopEnter(): EnterTransition = fadeIn(tween(NAV_ANIM_MS))

private fun spreadPopExit(): ExitTransition =
    fadeOut(tween(NAV_ANIM_MS)) + scaleOut(tween(NAV_ANIM_MS, easing = navEasing), targetScale = 0.92f)

private fun fabSpreadEnter(): EnterTransition =
    fadeIn(tween(FAB_ENTER_MS)) +
        scaleIn(tween(FAB_ENTER_MS, easing = navEasing), initialScale = 0.9f, transformOrigin = fabOrigin)

private fun fabSpreadExit(): ExitTransition =
    fadeOut(tween(FAB_EXIT_MS)) +
        scaleOut(tween(FAB_EXIT_MS, easing = navEasing), targetScale = 0.9f, transformOrigin = fabOrigin)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainNavGraph() {
    val navController = rememberNavController()
    PermissionGate {
        // Hosts the cross-screen shared elements (the contact avatar). Each
        // destination's AnimatedContentScope is the visibility scope the avatar
        // animates within; both are handed to the screens below. The themed
        // background fills the fade/scale gap during transitions so nothing flashes
        // the window background (white in dark mode) through it.
        SharedTransitionLayout(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val sharedScope = this
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { spreadEnter() },
                // Hold the underlying screen still (don't fade it out) while the
                // editor spreads open on top — one cheap layer animating, not two.
                exitTransition = {
                    if (targetState.destination.route == Routes.EDITOR_NEW) {
                        ExitTransition.None
                    } else {
                        spreadExit()
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route == Routes.EDITOR_NEW) {
                        EnterTransition.None
                    } else {
                        spreadPopEnter()
                    }
                },
                popExitTransition = { spreadPopExit() },
            ) {
                composable(Routes.HOME) { entry ->
                    val pendingFilter by entry.savedStateHandle
                        .getStateFlow<String?>(Routes.FILTER_ACCOUNT_KEY, null)
                        .collectAsStateWithLifecycle()
                    HomeScreen(
                        onContactClick = { contact ->
                            navController.navigate(
                                Routes.detail(contact.contactId, contact.displayName, contact.photoThumbnailUri),
                            )
                        },
                        onAddClick = { navController.navigate(Routes.EDITOR_NEW) },
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                        onDuplicatesClick = { navController.navigate(Routes.DUPLICATES) },
                        pendingFilterAccountKey = pendingFilter,
                        onPendingFilterConsumed = {
                            entry.savedStateHandle[Routes.FILTER_ACCOUNT_KEY] = null
                        },
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this,
                    )
                }
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
                ) { entry ->
                    val args = entry.arguments
                    DetailScreen(
                        onBack = { navController.popBackStack() },
                        onEditRawContact = { rawContactId ->
                            navController.navigate(Routes.editorEdit(rawContactId))
                        },
                        contactId = args?.getLong("contactId") ?: 0L,
                        initialName = args?.getString("name").orEmpty(),
                        initialPhotoUri = args?.getString("photo").orEmpty(),
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this,
                    )
                }
                composable(
                    Routes.EDITOR_NEW,
                    // Launched from the FAB — spread open from the bottom-right.
                    enterTransition = { fabSpreadEnter() },
                    exitTransition = { fabSpreadExit() },
                    popEnterTransition = { fabSpreadEnter() },
                    popExitTransition = { fabSpreadExit() },
                ) {
                    EditorScreen(
                        onBack = { navController.popBackStack() },
                        animatedVisibilityScope = this,
                    )
                }
                composable(
                    Routes.EDITOR_EDIT,
                    arguments = listOf(navArgument("rawContactId") { type = NavType.LongType }),
                    // Launched from a contact's detail — plain centre spread.
                    enterTransition = { spreadEnter() },
                    exitTransition = { spreadExit() },
                    popEnterTransition = { spreadPopEnter() },
                    popExitTransition = { spreadPopExit() },
                ) {
                    EditorScreen(
                        onBack = { navController.popBackStack() },
                        animatedVisibilityScope = this,
                    )
                }
                composable(Routes.DUPLICATES) {
                    DuplicatesScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onAccountsClick = { navController.navigate(Routes.ACCOUNTS) },
                        onContactClick = { navController.navigate(Routes.CONTACT_DEVELOPER) },
                        onAboutClick = { navController.navigate(Routes.ABOUT) },
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.CONTACT_DEVELOPER) {
                    ContactDeveloperScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.ACCOUNTS) {
                    AccountsScreen(
                        onBack = { navController.popBackStack() },
                        onAccountClick = { accountKey ->
                            // Accounts sits under Settings now — hand the filter to Home
                            // directly and pop the whole way back, whatever the depth.
                            navController
                                .getBackStackEntry(Routes.HOME)
                                .savedStateHandle[Routes.FILTER_ACCOUNT_KEY] = accountKey
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                    )
                }
            }
        }
    }
}
