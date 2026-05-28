package com.qingmo.app.ui.navigation

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

object Routes {
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
fun NavGraph() {
    val navController = rememberNavController()
    val currentEpisodeId = rememberSaveable { mutableStateOf(-1L) }

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            DramaListScreen(onDramaClick = { id ->
                navController.navigate(Routes.player(id, -1L)) {
                    popUpTo(Routes.LIST) { inclusive = false }
                }
            })
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("dramaId") { type = NavType.IntType }),
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
                    navController.navigate(Routes.detail(dramaId)) {
                        popUpTo(Routes.LIST) { inclusive = false }
                    }
                },
            )
        }
    }
}
