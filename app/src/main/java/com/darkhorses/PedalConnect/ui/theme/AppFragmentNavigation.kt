package com.darkhorses.PedalConnect.ui.theme

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavigator(paddingValues: PaddingValues) {
    val context       = LocalContext.current
    val navController = rememberNavController()

    // ── Session check ────────────────────────────────────────────────────────
    // Any successful login creates a session that persists until explicit logout.
    // The app always skips the login screen on reopen as long as a session exists,
    // regardless of whether Remember Me was checked or not.
    // Session is only destroyed by logoutAndNavigate().
    val prefs         = context.getSharedPreferences("PedalConnectPrefs", Context.MODE_PRIVATE)
    val savedUserName = prefs.getString("saved_user_name", null)
    val startDest     = if (!savedUserName.isNullOrBlank()) "home/$savedUserName" else "login"

    NavHost(navController, startDestination = startDest) {

        composable("login") {
            // System back button on the login screen exits the app —
            // never navigates back to a protected screen.
            val activity = context as? Activity
            BackHandler { activity?.finish() }
            LoginScreen(navController, paddingValues)
        }

        // register route removed — login and registration
        // are now handled by LoginScreen via tab switcher

        composable(
            "home/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            HomeScreen(navController, userName)
        }

        composable(
            "home_feed/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            HomeScreen(navController, userName, initialTab = 0)
        }

        composable("message") {
            MessageScreen(navController, paddingValues)
        }

        composable("settings") {
            SettingsScreen(navController)
        }

        composable(
            "profile/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            ProfileScreen(
                navController = navController,
                userName      = userName,
                paddingValues = paddingValues
            )
        }

        composable(
            "add_post/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            AddPostScreen(navController, userName)
        }

        composable(
            "public_profile/{targetUserName}",
            arguments = listOf(navArgument("targetUserName") { type = NavType.StringType })
        ) { backStackEntry ->
            val targetUserName = backStackEntry.arguments?.getString("targetUserName") ?: return@composable
            val currentUserName = prefs.getString("saved_user_name", null) ?: "Rider"
            PublicProfileScreen(
                navController   = navController,
                targetUserName  = targetUserName,
                currentUserName = currentUserName
            )
        }

        // ── Notifications ────────────────────────────────────────────────────
        composable(
            "notifications/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            NotificationsScreen(navController, userName)
        }

        // ── Directions ───────────────────────────────────────────────────────
        composable(
            "directions/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            DirectionsScreen(navController, userName)
        }

        // ── Ride Events ───────────────────────────────────────────────────────
        composable(
            "events/{userName}?openEventId={openEventId}",
            arguments = listOf(
                navArgument("userName")    { type = NavType.StringType },
                navArgument("openEventId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val userName    = backStackEntry.arguments?.getString("userName")    ?: "Rider"
            val openEventId = backStackEntry.arguments?.getString("openEventId")
            RidingEventsScreen(navController, userName, openEventId = openEventId)
        }


        // ── Navigate to saved route destination ──────────────────────────────
        composable(
            "home_navigate/{lat}/{lon}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lon") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val lat      = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lon      = backStackEntry.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0
            val prefs    = context.getSharedPreferences("PedalConnectPrefs", Context.MODE_PRIVATE)
            val userName = prefs.getString("saved_user_name", null) ?: "Rider"
            // Pop back to home with the destination pre-loaded via query param
            navController.navigate("home/$userName") {
                popUpTo("home/$userName") { inclusive = true }
            }
            // Note: HomeScreen reads destinationPoint from saved state —
            // for full deep-link support, pass lat/lon as a shared ViewModel or
            // saved state handle. This navigates home so the user can set it manually.
        }
        composable(
            "home_alerts/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            HomeScreen(navController, userName, openAlertsTab = true)
        }
    }
}