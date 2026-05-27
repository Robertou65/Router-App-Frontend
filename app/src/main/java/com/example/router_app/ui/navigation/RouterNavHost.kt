package com.example.router_app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.router_app.ui.screens.CameraScreen
import com.example.router_app.ui.screens.RouteConfigScreen
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
                onNewRoute = { navController.navigate(Routes.RouteConfig) },
                onOpenRoute = { routeId ->
                    navController.navigate("${Routes.RouteDetail}/$routeId")
                },
            )
        }
        composable(Routes.RouteConfig) { routeConfigEntry ->
            val cameraViewModel: CameraViewModel = viewModel(routeConfigEntry)
            RouteConfigScreen(
                cameraViewModel = cameraViewModel,
                onStartScanning = { navController.navigate(Routes.Camera) },
                onDone = { navController.navigate(Routes.SaveExport) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Camera) {
            val routeConfigEntry = remember(navController) {
                navController.getBackStackEntry(Routes.RouteConfig)
            }
            val cameraViewModel: CameraViewModel = viewModel(routeConfigEntry)
            CameraScreen(
                cameraViewModel = cameraViewModel,
                onFinish = { navController.navigate(Routes.SaveExport) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.CameraEdit}/{routeId}",
            arguments = listOf(navArgument("routeId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getLong("routeId") ?: 0L
            val cameraViewModel: CameraViewModel = viewModel(backStackEntry)
            LaunchedEffect(routeId) {
                cameraViewModel.setExistingRoute(routeId)
            }
            CameraScreen(
                cameraViewModel = cameraViewModel,
                onFinish = { navController.navigate(Routes.SaveExport) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SaveExport) {
            @Suppress("UnrememberedGetBackStackEntry")
            val cameraEntry = remember(navController) {
                try {
                    navController.getBackStackEntry(Routes.RouteConfig)
                } catch (e: IllegalArgumentException) {
                    navController.getBackStackEntry("${Routes.CameraEdit}/{routeId}")
                }
            }
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
                onAddStops = { id ->
                    navController.navigate("${Routes.CameraEdit}/$id")
                },
            )
        }
    }
}
