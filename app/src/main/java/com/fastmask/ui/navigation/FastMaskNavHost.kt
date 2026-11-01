package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.share.SharePrefill
import com.fastmask.ui.auth.LoginScreen
import com.fastmask.ui.create.CreateMaskedEmailScreen
import com.fastmask.ui.detail.MaskedEmailDetailScreen
import com.fastmask.ui.list.MaskedEmailListScreen
import com.fastmask.ui.pro.ProScreen
import com.fastmask.ui.settings.SettingsScreen
import com.fastmask.ui.welcome.WelcomeScreen

private const val KEY_ARCHIVED_ID = "archived_mask_id"
private const val KEY_ARCHIVED_STATE = "archived_mask_state"
private const val KEY_CREATED_EMAIL = "created_mask_email"

internal fun SavedStateHandle.consumeArchivedResultIfCurrent(consumedId: String) {
    if (get<String>(KEY_ARCHIVED_ID) == consumedId) {
        this[KEY_ARCHIVED_ID] = null
        this[KEY_ARCHIVED_STATE] = null
    }
}

@Composable
fun FastMaskNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    // M3 Expressive motion: a shared X axis driven by the theme's spatial spring for
    // movement and its effects spring for fades. Navigation 2.8+ runs the pop pair
    // against the predictive back gesture, so the same curves follow the finger.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val isDetail: (androidx.navigation.NavBackStackEntry) -> Boolean = { it.destination.route == NavRoutes.EMAIL_DETAIL }

    SharedTransitionLayout(modifier = modifier) {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(spatial) { it / 5 } + fadeIn(fadeInSpec) },
        exitTransition = { slideOutHorizontally(spatial) { -it / 10 } + fadeOut(fadeOutSpec) },
        popEnterTransition = { slideInHorizontally(spatial) { -it / 10 } + fadeIn(fadeInSpec) },
        popExitTransition = { slideOutHorizontally(spatial) { it / 5 } + fadeOut(fadeOutSpec) },
    ) {
        screen(
            route = NavRoutes.WELCOME,
            enterTransition = { fadeIn(fadeInSpec) },
            exitTransition = { fadeOut(fadeOutSpec) },
        ) {
            WelcomeScreen(
                onSignIn = {
                    navController.navigate(NavRoutes.LOGIN) { launchSingleTop = true }
                },
                onEnterDemo = {
                    navController.navigate(NavRoutes.EMAIL_LIST) {
                        popUpTo(NavRoutes.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        screen(
            route = NavRoutes.LOGIN,
            enterTransition = { fadeIn(fadeInSpec) },
            exitTransition = { fadeOut(fadeOutSpec) }
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.EMAIL_LIST) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEnterDemo = {
                    // Match the other authenticated/demo handoffs and clear
                    // the stack completely so back never returns to Welcome/Login.
                    navController.navigate(NavRoutes.EMAIL_LIST) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        screen(
            NavRoutes.EMAIL_LIST,
            // Into and back from a mask the row itself carries the motion (container
            // transform), so the list only fades instead of sliding away under it.
            exitTransition = { if (isDetail(targetState)) fadeOut(fadeOutSpec) else slideOutHorizontally(spatial) { -it / 10 } + fadeOut(fadeOutSpec) },
            popEnterTransition = { if (isDetail(initialState)) fadeIn(fadeInSpec) else slideInHorizontally(spatial) { -it / 10 } + fadeIn(fadeInSpec) },
        ) { entry ->
            // Set by the detail screen after archiving, so the list can show
            // an "archived — undo" snackbar where the mask reappears.
            val justArchivedId by entry.savedStateHandle
                .getStateFlow<String?>(KEY_ARCHIVED_ID, null)
                .collectAsState()
            val justArchivedState by entry.savedStateHandle
                .getStateFlow<String?>(KEY_ARCHIVED_STATE, null)
                .collectAsState()
            // Set by the create screen, so the "mask created — copy"
            // confirmation appears on the list rather than holding the
            // form open until the snackbar times out.
            val justCreatedEmail by entry.savedStateHandle
                .getStateFlow<String?>(KEY_CREATED_EMAIL, null)
                .collectAsState()
            MaskedEmailListScreen(
                onNavigateToCreate = {
                    navController.navigate(NavRoutes.createEmail()) { launchSingleTop = true }
                },
                onNavigateToDetail = { emailId ->
                    navController.navigate(NavRoutes.emailDetail(emailId)) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.SETTINGS) { launchSingleTop = true }
                },
                onSignInFromBanner = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onReauthenticate = {
                    // Keep the list entry underneath Login so cancelling the
                    // flow returns to the user's filters and scroll position.
                    // Login success still replaces the stack with a fresh list.
                    navController.navigate(NavRoutes.LOGIN) {
                        launchSingleTop = true
                    }
                },
                justArchivedId = justArchivedId,
                justArchivedState = justArchivedState
                    ?.let { runCatching { EmailState.valueOf(it) }.getOrNull() },
                onArchivedConsumed = { consumedId ->
                    // A second archive can arrive while the first Undo is
                    // visible. Never let result A erase newer pending B.
                    entry.savedStateHandle.consumeArchivedResultIfCurrent(consumedId)
                },
                justCreatedEmail = justCreatedEmail,
                onCreatedConsumed = { entry.savedStateHandle[KEY_CREATED_EMAIL] = null },
            )
        }

        screen(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(NavRoutes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSignInFromDemo = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToPro = { source ->
                    navController.navigate(NavRoutes.pro(source)) { launchSingleTop = true }
                },
            )
        }

        screen(
            route = NavRoutes.PRO,
            arguments = listOf(
                navArgument("source") {
                    type = NavType.StringType
                    defaultValue = "settings"
                }
            ),
        ) {
            ProScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        screen(
            route = NavRoutes.CREATE_EMAIL,
            arguments = listOf(
                navArgument("forDomain") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("description") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val forDomain: String = entry.arguments?.getString("forDomain").orEmpty()
            val url: String = entry.arguments?.getString("url").orEmpty()
            val description: String = entry.arguments?.getString("description").orEmpty()
            val prefill: SharePrefill? = if (forDomain.isNotBlank()) {
                SharePrefill(
                    forDomain = forDomain,
                    url = url,
                    description = description,
                )
            } else {
                null
            }
            CreateMaskedEmailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCreated = { email ->
                    // Hand the address to the list entry, then leave at
                    // once — the confirmation belongs where the new mask is.
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set(KEY_CREATED_EMAIL, email)
                    navController.popBackStack()
                },
                onSignInFromBanner = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                prefill = prefill,
            )
        }

        screen(
            route = NavRoutes.EMAIL_DETAIL,
            enterTransition = { fadeIn(fadeInSpec) },
            popExitTransition = { fadeOut(fadeOutSpec) },
            arguments = listOf(
                navArgument("emailId") { type = NavType.StringType }
            )
        ) {
            MaskedEmailDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onArchived = { id, previousState ->
                    // Hand the archived id + pre-archive state back to the
                    // list, then pop. State is written FIRST so the list's
                    // id-triggered latch never observes a torn pair.
                    val backEntry = navController.previousBackStackEntry
                    if (backEntry != null) {
                        backEntry.savedStateHandle[KEY_ARCHIVED_STATE] = previousState.name
                        backEntry.savedStateHandle[KEY_ARCHIVED_ID] = id
                    } else {
                        // Edge case: detail reached via deep-link with no list entry
                        // on the back stack. Write to the current back stack entry's
                        // SavedStateHandle so the data survives configuration changes.
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.apply {
                                set(KEY_ARCHIVED_STATE, previousState.name)
                                set(KEY_ARCHIVED_ID, id)
                            }
                    }
                    navController.popBackStack()
                },
                onSignInFromBanner = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
    }
    }
}
