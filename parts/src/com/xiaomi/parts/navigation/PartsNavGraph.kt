package com.xiaomi.parts.navigation

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiaomi.parts.ui.about.AboutScreen
import com.xiaomi.parts.ui.doze.DozeScreen
import com.xiaomi.parts.ui.fps.FpsMeterScreen
import com.xiaomi.parts.ui.home.HomeScreen
import com.xiaomi.parts.ui.saturation.SaturationScreen
import com.xiaomi.parts.ui.speaker.ClearSpeakerScreen
import com.xiaomi.parts.ui.thermal.ThermalScreen

object Routes {
    const val HOME = "home"
    const val THERMAL = "thermal"
    const val FPS = "fps"
    const val SATURATION = "saturation"
    const val SPEAKER = "speaker"
    const val DOZE = "doze"
    const val ABOUT = "about"
}

@Composable
fun PartsNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(280)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) },
        exitTransition = { fadeOut(animationSpec = tween(280)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) },
        popEnterTransition = { fadeIn(animationSpec = tween(280)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) },
        popExitTransition = { fadeOut(animationSpec = tween(280)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToThermal = { navController.navigate(Routes.THERMAL) },
                onNavigateToFps = { navController.navigate(Routes.FPS) },
                onNavigateToSaturation = { navController.navigate(Routes.SATURATION) },
                onNavigateToSpeaker = { navController.navigate(Routes.SPEAKER) },
                onNavigateToDoze = { navController.navigate(Routes.DOZE) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onBack = { activity?.finish() },
            )
        }

        composable(Routes.THERMAL) {
            ThermalScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FPS) {
            FpsMeterScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SATURATION) {
            SaturationScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SPEAKER) {
            ClearSpeakerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DOZE) {
            DozeScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
