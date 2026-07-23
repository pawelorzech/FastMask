package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.auth.LoginScreen
import com.fastmask.ui.create.CreateMaskedEmailScreen
import com.fastmask.ui.detail.MaskedEmailDetailScreen
import com.fastmask.ui.list.MaskedEmailListScreen
import com.fastmask.ui.pro.ProScreen
import com.fastmask.ui.settings.SettingsScreen
import com.fastmask.ui.welcome.WelcomeScreen

private const val TRANSITION_DURATION_MS = 220
private const val KEY_ARCHIVED_ID = "archived_mask_id"
private const val KEY_ARCHIVED_STATE = "archived_mask_state"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FastMaskNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
                )
            }
        ) {
            composable(
                route = NavRoutes.WELCOME,
                enterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)) },
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

            composable(
                route = NavRoutes.LOGIN,
                enterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)) }
            ) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(NavRoutes.EMAIL_LIST) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(NavRoutes.EMAIL_LIST) { entry ->
                // Set by the detail screen after archiving, so the list can show
                // an "archived — undo" snackbar where the mask reappears.
                val justArchivedId by entry.savedStateHandle
                    .getStateFlow<String?>(KEY_ARCHIVED_ID, null)
                    .collectAsState()
                val justArchivedState by entry.savedStateHandle
                    .getStateFlow<String?>(KEY_ARCHIVED_STATE, null)
                    .collectAsState()
                MaskedEmailListScreen(
                    onNavigateToCreate = {
                        navController.navigate(NavRoutes.CREATE_EMAIL) { launchSingleTop = true }
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
                    justArchivedId = justArchivedId,
                    justArchivedState = justArchivedState
                        ?.let { runCatching { EmailState.valueOf(it) }.getOrNull() },
                    onArchivedConsumed = {
                        entry.savedStateHandle[KEY_ARCHIVED_ID] = null
                        entry.savedStateHandle[KEY_ARCHIVED_STATE] = null
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }

            composable(NavRoutes.SETTINGS) {
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

            composable(
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

            composable(NavRoutes.CREATE_EMAIL) {
                CreateMaskedEmailScreen(
                    onNavigateBack = {
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

            composable(
                route = NavRoutes.EMAIL_DETAIL,
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
                        navController.previousBackStackEntry?.savedStateHandle?.let {
                            it[KEY_ARCHIVED_STATE] = previousState.name
                            it[KEY_ARCHIVED_ID] = id
                        }
                        navController.popBackStack()
                    },
                    onSignInFromBanner = {
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
        }
    }
}
