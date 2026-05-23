package com.example.router_app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.router_app.ui.screens.CameraScreen
import com.example.router_app.ui.screens.RouteDetailScreen
import com.example.router_app.ui.screens.RouteHistoryScreen
import com.example.router_app.ui.screens.SaveExportScreen
import com.example.router_app.ui.camera.CameraViewModel

@Composable
fun RouterNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.RouteHistory,
    ) {
        composable(Routes.RouteHistory) {
            RouteHistoryScreen(
                onNewRoute = { navController.navigate(Routes.Camera) },
                onOpenRoute = { routeId ->
                    navController.navigate("${Routes.RouteDetail}/$routeId")
                },
            )
        }
        composable(Routes.Camera) {
            CameraScreen(
                onFinish = { navController.navigate(Routes.SaveExport) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SaveExport) {
            val cameraEntry = remember(navController) { navController.getBackStackEntry(Routes.Camera) }
            val cameraViewModel: CameraViewModel = viewModel(cameraEntry)
            SaveExportScreen(
                cameraViewModel = cameraViewModel,
                onSaveComplete = {
                    navController.navigate(Routes.RouteHistory) {
                        popUpTo(Routes.RouteHistory) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.RouteDetail}/{routeId}",
            arguments = listOf(navArgument("routeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getLong("routeId") ?: 0L
            RouteDetailScreen(
                routeId = routeId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
