package com.example.router_app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.router_app.data.export.CsvExporter
import com.example.router_app.data.local.Stop
import com.example.router_app.ui.detail.RouteDetailViewModel
import java.io.File

@Composable
fun RouteDetailScreen(
    routeId: Long,
    onBack: () -> Unit,
    onAddStops: (Long) -> Unit,
) {
    val viewModel: RouteDetailViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val deletingStopIds by viewModel.deletingStopIds.collectAsState()
    val context = LocalContext.current
    var stopPendingDelete by remember { mutableStateOf<Stop?>(null) }

    LaunchedEffect(routeId) {
        viewModel.load(routeId)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = uiState.route?.name ?: "Route Detail",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Stops",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.stops.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "No stops for this route.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(uiState.stops) { index, stop ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "${index + 1}. ${stop.address}")
                                Text(
                                    text = "Lat: ${stop.lat}, Lng: ${stop.lng}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(
                                onClick = { stopPendingDelete = stop },
                                enabled = !deletingStopIds.contains(stop.id),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove stop",
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onAddStops(routeId) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(text = "Add More Stops")
        }

         Button(
            onClick = {
                val csv = CsvExporter.buildCsv(uiState.stops)
                val fileName = "route_${routeId}.csv"
                val cacheFile = File(context.cacheDir, fileName)
                cacheFile.writeText(csv, Charsets.UTF_8)
                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Export to Routin"))
                val cutoff = System.currentTimeMillis() - 3_600_000L
                context.cacheDir.listFiles()
                    ?.filter { it.extension == "csv" && it.lastModified() < cutoff }
                    ?.forEach { it.delete() }
            },
            enabled = uiState.stops.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(text = "Export CSV to Routin")
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(text = "Back")
        }
    }

    stopPendingDelete?.let { stop ->
        AlertDialog(
            onDismissRequest = { stopPendingDelete = null },
            title = { Text(text = "Remove stop?") },
            text = { Text(text = stop.address) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeStop(stop.id)
                        stopPendingDelete = null
                    },
                    enabled = !deletingStopIds.contains(stop.id),
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                Button(onClick = { stopPendingDelete = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}
