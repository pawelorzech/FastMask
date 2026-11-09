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
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.fastmask.ui.auth.LoginScreen
import com.fastmask.ui.create.CreateMaskedEmailScreen
import com.fastmask.ui.detail.MaskedEmailDetailScreen
import com.fastmask.ui.components.expressive.LocalSharedTransitionScope
import com.fastmask.ui.list.ListResult
import com.fastmask.ui.list.MaskedEmailListScreen
import com.fastmask.ui.list.consumeListResult
import com.fastmask.ui.list.pendingListResult
import com.fastmask.ui.list.postListResult
import com.fastmask.ui.pro.PRO_SOURCE_SETTINGS
import com.fastmask.ui.pro.ProScreen
import com.fastmask.ui.settings.SettingsScreen
import com.fastmask.ui.welcome.WelcomeScreen

/**
 * Hands [result] to the list entry wherever it sits in the back stack: a share can
 * open Create on top of Settings, Pro or Detail, so `previousBackStackEntry` is not
 * always the list. No list entry on the stack means nothing is shown.
 */
private fun NavController.postToList(result: ListResult) {
    val list = try {
        getBackStackEntry(NavRoutes.EMAIL_LIST)
    } catch (_: IllegalArgumentException) {
        return
    }
    list.savedStateHandle.postListResult(result)
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

    // Keep the current entry under Login so cancelling returns to the user's filters,
    // scroll position or draft; login success still replaces the stack with a fresh list.
    val onReauthenticate: () -> Unit = {
        navController.navigate(NavRoutes.LOGIN) { launchSingleTop = true }
    }

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
                    navController.resetTo(NavRoutes.EMAIL_LIST)
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
                    navController.resetTo(NavRoutes.EMAIL_LIST)
                },
                onEnterDemo = {
                    navController.resetTo(NavRoutes.EMAIL_LIST)
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
            val pendingResult by entry.savedStateHandle.pendingListResult().collectAsState()
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
                    navController.resetTo(NavRoutes.LOGIN)
                },
                onReauthenticate = onReauthenticate,
                pendingResult = pendingResult,
                onResultConsumed = { entry.savedStateHandle.consumeListResult(it) },
            )
        }

        screen(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.resetTo(NavRoutes.WELCOME)
                },
                onSignInFromDemo = {
                    navController.resetTo(NavRoutes.LOGIN)
                },
                onNavigateToPro = { source ->
                    navController.navigate(NavRoutes.pro(source)) { launchSingleTop = true }
                },
            )
        }

        screen(
            route = NavRoutes.PRO,
            arguments = listOf(
                navArgument(NavRoutes.ARG_SOURCE) {
                    type = NavType.StringType
                    defaultValue = PRO_SOURCE_SETTINGS
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
                navArgument(NavRoutes.ARG_FOR_DOMAIN) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            CreateMaskedEmailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCreated = { email ->
                    navController.postToList(ListResult.Created(email))
                    navController.popBackStack()
                },
                onSignInFromBanner = {
                    navController.resetTo(NavRoutes.LOGIN)
                },
                onReauthenticate = onReauthenticate,
            )
        }

        screen(
            route = NavRoutes.EMAIL_DETAIL,
            enterTransition = { fadeIn(fadeInSpec) },
            popExitTransition = { fadeOut(fadeOutSpec) },
            arguments = listOf(
                navArgument(NavRoutes.ARG_EMAIL_ID) { type = NavType.StringType }
            )
        ) {
            MaskedEmailDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onArchived = { id, previousState ->
                    navController.postToList(ListResult.Archived(id, previousState))
                    navController.popBackStack()
                },
                onSignInFromBanner = {
                    navController.resetTo(NavRoutes.LOGIN)
                },
                onReauthenticate = onReauthenticate,
            )
        }
    }
    }
    }
}
