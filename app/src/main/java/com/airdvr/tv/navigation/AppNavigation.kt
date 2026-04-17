package com.airdvr.tv.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.ui.screens.*
import com.airdvr.tv.util.Constants

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val tokenManager = remember { AirDVRApp.instance.tokenManager }

    val startDestination = if (tokenManager.isLoggedIn()) {
        "zip_check"
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
                    navController.navigate("zip_check") {
                        popUpTo(Constants.ROUTE_LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Zip code check — skip if guide already has channels
        composable("zip_check") {
            val api = remember { ApiClient.api }
            LaunchedEffect(Unit) {
                try {
                    val guideResp = api.getGuide()
                    val hasChannels = guideResp.body()?.channels?.isNotEmpty() == true
                    if (hasChannels) {
                        navController.navigate(Constants.ROUTE_LIVE_TV) {
                            popUpTo("zip_check") { inclusive = true }
                        }
                    } else {
                        val profileResp = api.getUserProfile()
                        val zip = profileResp.body()?.zipCode
                        if (zip.isNullOrBlank()) {
                            navController.navigate(Constants.ROUTE_ZIP_CODE) {
                                popUpTo("zip_check") { inclusive = true }
                            }
                        } else {
                            navController.navigate(Constants.ROUTE_LIVE_TV) {
                                popUpTo("zip_check") { inclusive = true }
                            }
                        }
                    }
                } catch (_: Exception) {
                    navController.navigate(Constants.ROUTE_LIVE_TV) {
                        popUpTo("zip_check") { inclusive = true }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                com.airdvr.tv.ui.components.LoadingSpinner(message = "")
            }
        }

        composable(Constants.ROUTE_ZIP_CODE) {
            ZipCodeScreen(
                onContinue = {
                    navController.navigate(Constants.ROUTE_LIVE_TV) {
                        popUpTo(Constants.ROUTE_ZIP_CODE) { inclusive = true }
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
                onNavigatePlayer = { recordingId, streamUrl ->
                    val route = if (streamUrl != null) {
                        "player/$recordingId?streamUrl=${Uri.encode(streamUrl)}"
                    } else {
                        "player/$recordingId"
                    }
                    navController.navigate(route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Constants.ROUTE_PLAYER}?streamUrl={streamUrl}",
            arguments = listOf(
                navArgument("recordingId") { type = NavType.StringType },
                navArgument("streamUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
            val streamUrl = backStackEntry.arguments?.getString("streamUrl")
            PlayerScreen(
                recordingId = recordingId,
                streamUrl = streamUrl,
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
