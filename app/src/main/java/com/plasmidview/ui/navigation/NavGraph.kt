package com.plasmidview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plasmidview.ui.home.HomeScreen
import com.plasmidview.ui.about.AboutScreen
import com.plasmidview.ui.settings.DisplayScreen
import com.plasmidview.ui.settings.AIConfigScreen
import com.plasmidview.ui.settings.LanguageScreen
import com.plasmidview.ui.about.AboutScreen

object Routes {
    const val HOME = "home"
    const val PLASMID = "plasmid/{docIndex}"
    const val HELP = "help"

    fun plasmid(docIndex: Int) = "plasmid/$docIndex"
}

@Composable
fun PlasmidNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDocument = { index -> navController.navigate(Routes.plasmid(index)) },
                onNavTo = { route -> navController.navigate(route) }
            )
        }

        composable(
            Routes.PLASMID,
            arguments = listOf(navArgument("docIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val docIndex = backStackEntry.arguments?.getInt("docIndex") ?: 0
            PlasmidContentScreen(
                docIndex = docIndex,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings_display") {
            DisplayScreen(onBack = { navController.popBackStack() })
        }

        composable("settings_ai") {
            AIConfigScreen(onBack = { navController.popBackStack() })
        }

        composable("settings_lang") {
            LanguageScreen(onBack = { navController.popBackStack() })
        }

        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
