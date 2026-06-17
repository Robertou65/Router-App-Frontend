package com.example.router_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.router_app.data.local.RouteSummary
import com.example.router_app.ui.history.RouteHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RouteHistoryScreen(
    onNewRoute: () -> Unit,
    onOpenRoute: (Long) -> Unit,
) {
    val viewModel: RouteHistoryViewModel = viewModel()
    val routes by viewModel.routes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val deletingRouteIds by viewModel.deletingRouteIds.collectAsState()
    var routePendingDelete by remember { mutableStateOf<RouteSummary?>(null) }

    BoxWithFab(
        onFabClick = onNewRoute,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Text(
                text = "Route History",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            } else if (routes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No routes yet. Tap + New Route to start scanning.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(routes) { route ->
                        RouteHistoryRow(
                            route = route,
                            isDeleting = deletingRouteIds.contains(route.id),
                            onOpen = { onOpenRoute(route.id) },
                            onDelete = { routePendingDelete = route },
                        )
                    }
                }
            }
        }
    }

    routePendingDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routePendingDelete = null },
            title = { Text(text = "Delete route?") },
            text = { Text(text = route.name) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoute(route.id)
                        routePendingDelete = null
                    },
                    enabled = !deletingRouteIds.contains(route.id),
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                Button(onClick = { routePendingDelete = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun BoxWithFab(
    onFabClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        content()
        FloatingActionButton(
            onClick = onFabClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
        ) {
            Text(text = "+ New Route")
        }
    }
}

@Composable
private fun RouteHistoryRow(
    route: RouteSummary,
    isDeleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = route.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatDate(route.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${route.stopCount} stops",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onOpen) {
                Text(text = "Open")
            }
            IconButton(onClick = onDelete, enabled = !isDeleting) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete route")
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
