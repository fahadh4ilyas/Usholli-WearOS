package com.mrhabibi.usholli.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mrhabibi.usholli.wear.ui.home.HomeScreen
import com.mrhabibi.usholli.wear.ui.kiblat.KiblatScreen
import com.mrhabibi.usholli.wear.ui.location.LocationScreen
import com.mrhabibi.usholli.wear.ui.preference.NotificationPreferenceScreen
import com.mrhabibi.usholli.wear.ui.settings.HijriCorrectionScreen
import com.mrhabibi.usholli.wear.ui.settings.SettingsScreen

object Dest {
    const val HOME = "home"
    const val KIBLAT = "kiblat"
    const val SETTINGS = "settings"
    const val LOCATION = "location"
    const val HIJRI = "hijri"
    const val PREFERENCE = "preference/{periodId}"

    fun preference(periodId: String) = "preference/$periodId"
}

@Composable
fun UsholliApp(openHomeTrigger: Int = 0) {
    val navController = rememberSwipeDismissableNavController()
    val viewModel: UsholliViewModel = viewModel()

    // When the app is (re)opened from a tile/complication, always land on the home menu.
    LaunchedEffect(openHomeTrigger) {
        if (openHomeTrigger > 0) {
            navController.navigate(Dest.HOME) {
                popUpTo(Dest.HOME) { inclusive = true }
            }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Dest.HOME,
    ) {
        composable(Dest.HOME) {
            HomeScreen(viewModel = viewModel, navController = navController)
        }
        composable(Dest.KIBLAT) {
            KiblatScreen(navController = navController)
        }
        composable(Dest.SETTINGS) {
            SettingsScreen(viewModel = viewModel, navController = navController)
        }
        composable(Dest.LOCATION) {
            LocationScreen(viewModel = viewModel, navController = navController)
        }
        composable(Dest.HIJRI) {
            HijriCorrectionScreen(viewModel = viewModel, navController = navController)
        }
        composable(
            route = Dest.PREFERENCE,
            arguments = listOf(navArgument("periodId") { type = NavType.StringType }),
        ) { entry ->
            val periodId = entry.arguments?.getString("periodId")
            NotificationPreferenceScreen(viewModel = viewModel, navController = navController, periodId = periodId)
        }
    }
}
