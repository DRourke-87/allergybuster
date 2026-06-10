package com.tarnlabs.allergybuster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tarnlabs.allergybuster.ui.history.HistoryScreen
import com.tarnlabs.allergybuster.ui.home.HomeScreen
import com.tarnlabs.allergybuster.ui.places.PlaceCheckScreen
import com.tarnlabs.allergybuster.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onPlacesClick = {
                navController.navigate("places") { launchSingleTop = true }
            })
        }
        composable("history")  { HistoryScreen() }
        composable("settings") { SettingsScreen() }
        composable("places")   { PlaceCheckScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = current == "home",
            onClick  = { navController.navigate("home") { launchSingleTop = true } },
            icon     = { Icon(Icons.Default.Home, contentDescription = null) },
            label    = { Text("Today") }
        )
        NavigationBarItem(
            selected = current == "history",
            onClick  = { navController.navigate("history") { launchSingleTop = true } },
            icon     = { Icon(Icons.Default.History, contentDescription = null) },
            label    = { Text("History") }
        )
        NavigationBarItem(
            selected = current == "settings",
            onClick  = { navController.navigate("settings") { launchSingleTop = true } },
            icon     = { Icon(Icons.Default.Settings, contentDescription = null) },
            label    = { Text("Settings") }
        )
    }
}
