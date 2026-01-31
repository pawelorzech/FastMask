package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fastmask.ui.auth.LoginScreen
import com.fastmask.ui.create.CreateMaskedEmailScreen
import com.fastmask.ui.detail.MaskedEmailDetailScreen
import com.fastmask.ui.list.MaskedEmailListScreen
import com.fastmask.ui.settings.SettingsScreen

private const val TRANSITION_DURATION_MS = 220

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
                route = NavRoutes.LOGIN,
                enterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)) }
            ) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(NavRoutes.EMAIL_LIST) {
                            popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoutes.EMAIL_LIST) {
                MaskedEmailListScreen(
                    onNavigateToCreate = {
                        navController.navigate(NavRoutes.CREATE_EMAIL)
                    },
                    onNavigateToDetail = { emailId ->
                        navController.navigate(NavRoutes.emailDetail(emailId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoutes.SETTINGS)
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
                        navController.navigate(NavRoutes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoutes.CREATE_EMAIL) {
                CreateMaskedEmailScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
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
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@composable
                )
            }
        }
    }
}
