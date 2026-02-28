package com.darkhorses.RideSafe.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavigator(paddingValues: PaddingValues) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "login") {
        composable("login") { LoginScreen(navController, paddingValues)}
        composable("register") { RegisterScreen(navController, paddingValues) }
        composable(
            "home/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            HomeScreen(navController, userName)
        }
        composable("message") { MessageScreen(navController, paddingValues) }
        composable("settings") { SettingsScreen(navController) }

        composable(
            "add_post/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            AddPostScreen(navController, userName)
        }

        composable(
            "notifications/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            NotificationsScreen(navController, userName)
        }

    }
}

