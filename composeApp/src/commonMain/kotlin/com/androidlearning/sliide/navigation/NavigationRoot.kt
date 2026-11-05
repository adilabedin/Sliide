package com.androidlearning.sliide.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.androidlearning.sliide.users.presentation.navigation.UsersGraphRoute
import com.androidlearning.sliide.users.presentation.navigation.usersGraph

/** The app's one `NavHost`: every feature adds its graph here, and features reach each other only through callbacks. */
@Composable
fun NavigationRoot() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = UsersGraphRoute,
    ) {
        usersGraph(navController)
    }
}
