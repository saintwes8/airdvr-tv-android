package com.airdvr.tv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.ui.screens.*
import com.airdvr.tv.util.Constants

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val tokenManager = remember { AirDVRApp.instance.tokenManager }

    val startDestination = if (tokenManager.isLoggedIn()) {
        Constants.ROUTE_LIVE_TV
    } else {
        Constants.ROUTE_LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Constants.ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Constants.ROUTE_LIVE_TV) {
                        popUpTo(Constants.ROUTE_LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Constants.ROUTE_HOME) {
            HomeScreen(
                onNavigateLiveTV = { navController.navigate(Constants.ROUTE_LIVE_TV) },
                onNavigateWhereToWatch = { navController.navigate(Constants.ROUTE_WHERE_TO_WATCH) },
                onNavigateSportsCalendar = { navController.navigate(Constants.ROUTE_SPORTS_CALENDAR) },
                onNavigateRecordings = { navController.navigate(Constants.ROUTE_RECORDINGS) },
                onNavigateCustomChannels = { navController.navigate(Constants.ROUTE_CUSTOM_CHANNELS) },
                onNavigateSettings = { navController.navigate(Constants.ROUTE_SETTINGS) },
                onNavigatePlayer = { recordingId ->
                    navController.navigate("player/$recordingId")
                },
                onLogout = {
                    navController.navigate(Constants.ROUTE_LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Constants.ROUTE_LIVE_TV) {
            LiveTVScreen(
                onNavigateHome = { navController.navigate(Constants.ROUTE_HOME) },
                onNavigateWhereToWatch = { navController.navigate(Constants.ROUTE_WHERE_TO_WATCH) },
                onNavigateSportsCalendar = { navController.navigate(Constants.ROUTE_SPORTS_CALENDAR) },
                onNavigateRecordings = { navController.navigate(Constants.ROUTE_RECORDINGS) },
                onNavigateCustomChannels = { navController.navigate(Constants.ROUTE_CUSTOM_CHANNELS) },
                onNavigateSettings = { navController.navigate(Constants.ROUTE_SETTINGS) }
            )
        }

        composable(Constants.ROUTE_WHERE_TO_WATCH) {
            WhereToWatchScreen(
                onBack = { navController.popBackStack() },
                onNavigateLiveTV = { _ ->
                    navController.navigate(Constants.ROUTE_LIVE_TV)
                }
            )
        }

        composable(Constants.ROUTE_SPORTS_CALENDAR) {
            SportsCalendarScreen(onBack = { navController.popBackStack() })
        }

        composable(Constants.ROUTE_CUSTOM_CHANNELS) {
            CustomChannelsScreen(onBack = { navController.popBackStack() })
        }

        composable(Constants.ROUTE_RECORDINGS) {
            RecordingsScreen(
                onNavigatePlayer = { recordingId ->
                    navController.navigate("player/$recordingId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Constants.ROUTE_PLAYER,
            arguments = listOf(
                navArgument("recordingId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
            PlayerScreen(
                recordingId = recordingId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Constants.ROUTE_SETTINGS) {
            SettingsScreen(
                onLogout = {
                    navController.navigate(Constants.ROUTE_LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
