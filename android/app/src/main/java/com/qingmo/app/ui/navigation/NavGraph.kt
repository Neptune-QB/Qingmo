package com.qingmo.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qingmo.app.ui.screens.DramaDetailScreen
import com.qingmo.app.ui.screens.DramaListScreen
import com.qingmo.app.ui.screens.DramaPagerScreen
import com.qingmo.app.ui.screens.LoginScreen
import com.qingmo.app.ui.screens.RegisterScreen
import com.qingmo.app.ui.screens.UserProfileScreen
import com.qingmo.app.data.auth.TokenManager

object Routes {
    const val LOGIN = "auth_login"
    const val REGISTER = "auth_register"
    const val PROFILE = "user_profile"
    const val LIST = "drama_list"
    const val DETAIL = "drama_detail/{dramaId}"
    const val PLAYER = "player/{dramaId}/{episodeId}"

    fun detail(id: Int) = "drama_detail/$id"

    fun player(
        dramaId: Int,
        episodeId: Long,
    ) = "player/$dramaId/$episodeId"
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun NavGraph(deviceId: String) {
    val navController = rememberNavController()
    val currentEpisodeId = rememberSaveable { mutableStateOf(-1L) }
    val isLoggedIn = TokenManager.isLoggedIn()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Routes.LIST else Routes.LOGIN,
    ) {
        composable(
            Routes.LOGIN,
            enterTransition = { fadeIn(tween(200)) },
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onGoRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                deviceId = deviceId,
            )
        }

        composable(
            Routes.REGISTER,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
        ) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onGoLogin = { navController.popBackStack() },
                deviceId = deviceId,
            )
        }

        composable(Routes.PROFILE) {
            UserProfileScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onDramaClick = { id -> navController.navigate(Routes.detail(id)) },
            )
        }

        composable(
            Routes.LIST,
            enterTransition = { fadeIn(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
        ) {
            DramaListScreen(onDramaClick = { id ->
                navController.navigate(Routes.player(id, -1L)) {
                    popUpTo(Routes.LIST) { inclusive = false }
                }
            }, onProfileClick = {
                navController.navigate(Routes.PROFILE)
            })
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("dramaId") { type = NavType.IntType }),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
            },
        ) { backStackEntry ->
            val dramaId = backStackEntry.arguments?.getInt("dramaId") ?: return@composable
            DramaDetailScreen(
                dramaId = dramaId,
                onEpisodeClick = { episodeId ->
                    navController.navigate(Routes.player(dramaId, episodeId)) {
                        popUpTo(Routes.LIST) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
                currentEpisodeId = currentEpisodeId.value,
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments =
                listOf(
                    navArgument("dramaId") { type = NavType.IntType },
                    navArgument("episodeId") { type = NavType.LongType },
                ),
            enterTransition = {
                if (initialState.destination.route == Routes.DETAIL) {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                } else {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                }
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
            },
        ) { backStackEntry ->
            val dramaId = backStackEntry.arguments?.getInt("dramaId") ?: return@composable
            val episodeId = backStackEntry.arguments?.getLong("episodeId") ?: return@composable
            DramaPagerScreen(
                dramaId = dramaId,
                episodeId = episodeId,
                onBack = { navController.popBackStack() },
                onNextDrama = { id ->
                    navController.navigate(Routes.player(id, -1L)) {
                        popUpTo(Routes.LIST) { inclusive = false }
                    }
                },
                onCurrentEpisodeChanged = { currentEpisodeId.value = it },
                onGoDetail = {
                    navController.navigate(Routes.detail(dramaId))
                },
            )
        }
    }
}
