package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.fastmask.ui.adaptive.FastMaskAdaptiveRules
import com.fastmask.ui.adaptive.MasksWorkspace
import com.fastmask.ui.adaptive.currentFastMaskAdaptiveInfo
import com.fastmask.ui.auth.LoginScreen
import com.fastmask.ui.create.CreateMaskedEmailScreen
import com.fastmask.ui.detail.MaskedEmailDetailScreen
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

private fun NavGraphBuilder.appScreen(
    route: String,
    layerRoles: MutableMap<String, TransitionLayerRole>,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = screen(
    route = route,
    arguments = arguments,
    layerRole = { id -> layerRoles[id] ?: TransitionLayerRole.NONE },
    onDisposeEntry = { id -> layerRoles.remove(id) },
    content = content,
)

@Composable
fun FastMaskNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val specs = rememberNavMotionSpecs()
    val layerRoles = remember { mutableMapOf<String, TransitionLayerRole>() }

    // Keep the current entry under Login so cancelling returns to the user's filters,
    // scroll position or draft; login success still replaces the stack with a fresh list.
    val onReauthenticate: () -> Unit = {
        navController.navigate(NavRoutes.login(modal = true)) { launchSingleTop = true }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            val style = resolveStyle(isPop = false)
            layerRoles[targetState.id] = style.enterLayerRole()
            enterTransitionFor(style, specs)
        },
        exitTransition = {
            val style = resolveStyle(isPop = false)
            layerRoles[initialState.id] = style.exitLayerRole()
            exitTransitionFor(style, specs)
        },
        popEnterTransition = {
            val style = resolveStyle(isPop = true)
            layerRoles[targetState.id] = style.enterLayerRole()
            enterTransitionFor(style, specs)
        },
        popExitTransition = {
            val style = resolveStyle(isPop = true)
            layerRoles[initialState.id] = style.exitLayerRole()
            exitTransitionFor(style, specs)
        },
    ) {
        appScreen(route = NavRoutes.WELCOME, layerRoles = layerRoles) {
            WelcomeScreen(
                onSignIn = {
                    navController.navigate(NavRoutes.login(modal = false)) { launchSingleTop = true }
                },
                onEnterDemo = {
                    navController.resetTo(NavRoutes.EMAIL_LIST)
                },
            )
        }

        appScreen(
            route = NavRoutes.LOGIN,
            layerRoles = layerRoles,
            arguments = listOf(
                navArgument(NavRoutes.ARG_LOGIN_MODAL) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val isModal = entry.arguments?.getBoolean(NavRoutes.ARG_LOGIN_MODAL) == true
            LoginScreen(
                onLoginSuccess = {
                    navController.resetTo(NavRoutes.EMAIL_LIST)
                },
                onEnterDemo = {
                    navController.resetTo(NavRoutes.EMAIL_LIST)
                },
                isModal = isModal,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.resetTo(NavRoutes.WELCOME)
                    }
                },
            )
        }

        appScreen(route = NavRoutes.EMAIL_LIST, layerRoles = layerRoles) { entry ->
            val pendingResult by entry.savedStateHandle.pendingListResult().collectAsState()
            val adaptiveInfo = currentFastMaskAdaptiveInfo()
            if (FastMaskAdaptiveRules.usesWorkspace(adaptiveInfo)) {
                MasksWorkspace(
                    onNavigateToSettings = {
                        navController.navigate(NavRoutes.SETTINGS) { launchSingleTop = true }
                    },
                    onSignInFromBanner = {
                        navController.resetTo(NavRoutes.login(modal = false))
                    },
                    onReauthenticate = onReauthenticate,
                    externalPendingResult = pendingResult,
                    onExternalResultConsumed = { entry.savedStateHandle.consumeListResult(it) },
                )
            } else {
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
                        navController.resetTo(NavRoutes.login(modal = false))
                    },
                    onReauthenticate = onReauthenticate,
                    pendingResult = pendingResult,
                    onResultConsumed = { entry.savedStateHandle.consumeListResult(it) },
                )
            }
        }

        appScreen(route = NavRoutes.SETTINGS, layerRoles = layerRoles) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.resetTo(NavRoutes.WELCOME)
                },
                onSignInFromDemo = {
                    navController.resetTo(NavRoutes.login(modal = false))
                },
                onNavigateToPro = { source ->
                    navController.navigate(NavRoutes.pro(source)) { launchSingleTop = true }
                },
            )
        }

        appScreen(
            route = NavRoutes.PRO,
            layerRoles = layerRoles,
            arguments = listOf(
                navArgument(NavRoutes.ARG_SOURCE) {
                    type = NavType.StringType
                    defaultValue = PRO_SOURCE_SETTINGS
                },
            ),
        ) {
            ProScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        appScreen(
            route = NavRoutes.CREATE_EMAIL,
            layerRoles = layerRoles,
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
                    navController.resetTo(NavRoutes.login(modal = false))
                },
                onReauthenticate = onReauthenticate,
            )
        }

        appScreen(
            route = NavRoutes.EMAIL_DETAIL,
            layerRoles = layerRoles,
            arguments = listOf(
                navArgument(NavRoutes.ARG_EMAIL_ID) { type = NavType.StringType },
            ),
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
                    navController.resetTo(NavRoutes.login(modal = false))
                },
                onReauthenticate = onReauthenticate,
            )
        }
    }
}
