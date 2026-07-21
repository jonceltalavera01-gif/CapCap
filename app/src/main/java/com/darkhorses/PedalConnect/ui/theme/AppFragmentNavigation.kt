package com.darkhorses.PedalConnect.ui.theme

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink

@Composable
fun AppNavigator(paddingValues: PaddingValues) {
    val context       = LocalContext.current
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()

    // ── Online Status Lifecycle Observer ─────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                chatViewModel.setOnlineStatus(true)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                chatViewModel.setOnlineStatus(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            "home/{userName}?linkedWeek={linkedWeek}&linkedWorkoutId={linkedWorkoutId}&autoStart={autoStart}&startId={startId}",
            arguments = listOf(
                navArgument("userName")        { type = NavType.StringType },
                navArgument("linkedWeek")       { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("linkedWorkoutId")  { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("autoStart")        { type = NavType.StringType; nullable = true; defaultValue = "false" },
                navArgument("startId")          { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val userName        = backStackEntry.arguments?.getString("userName") ?: "Rider"
            val linkedWeek      = backStackEntry.arguments?.getString("linkedWeek")?.toIntOrNull()
            val linkedWorkoutId = backStackEntry.arguments?.getString("linkedWorkoutId")
            val autoStart       = backStackEntry.arguments?.getString("autoStart") == "true"
            val startId         = backStackEntry.arguments?.getString("startId")
            HomeScreen(
                navController    = navController,
                userName         = userName,
                linkedWeekNumber = linkedWeek,
                linkedWorkoutId  = linkedWorkoutId,
                autoStartRide    = autoStart,
                startId          = startId
            )
        }

        composable(
            "home_feed/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            HomeScreen(navController, userName, initialTab = 0)
        }

        composable(
            route = "chat/{conversationId}/{otherUserId}/{otherUserName}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType },
                navArgument("otherUserName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""
            val otherUserName = backStackEntry.arguments?.getString("otherUserName") ?: "Rider"

            ChatScreen(
                navController = navController,
                paddingValues = paddingValues, // pass whatever PaddingValues you use elsewhere
                conversationId = conversationId,
                otherUserId = otherUserId,
                otherUserName = otherUserName
            )
        }

        composable("messages") {
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
            "training/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            TrainingScreen(navController, userName, paddingValues = paddingValues)
        }

        composable(
            "add_post/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            AddPostScreen(navController, userName)
        }

        composable(
            "add_post_simple/{userName}",
            arguments = listOf(navArgument("userName") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("userName") ?: "Rider"
            AddPostSimpleScreen(navController, userName)
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
            // Store the pending destination coords for HomeScreen to consume on launch —
            // same mechanism already used for "pending_destination" (ride events),
            // extended to carry raw coordinates instead of a text query.
            prefs.edit()
                .putFloat("pending_dest_lat", lat.toFloat())
                .putFloat("pending_dest_lon", lon.toFloat())
                .apply()
            navController.navigate("home/$userName") {
                popUpTo("home/$userName") { inclusive = true }
            }
        }
        composable(
            "post/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pedalconnect://post/{postId}" })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            val currentUserName = prefs.getString("saved_user_name", null) ?: "Rider"
            PostDetailScreen(
                navController   = navController,
                postId          = postId,
                currentUserName = currentUserName
            )
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