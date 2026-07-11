package com.ryccoatika.contactmanager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryccoatika.contactmanager.ui.home.HomeScreen
import com.ryccoatika.contactmanager.ui.permission.PermissionGate

object Routes {
    const val HOME = "home"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    PermissionGate {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen() }
        }
    }
}
