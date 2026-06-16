package com.example.router_app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.router_app.data.export.CsvExporter
import com.example.router_app.data.local.Stop
import com.example.router_app.ui.detail.RouteDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedFolderUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The DB already reflects every edit (deletes persist immediately, new stops are
    // added through the camera flow), so "save" here writes the up-to-date route to CSV.
    val writeCsv: (Uri) -> Unit = { uri ->
        scope.launch {
            isSaving = true
            val result = runCatching {
                val csv = CsvExporter.buildCsv(uiState.stops)
                withContext(Dispatchers.IO) {
                    val folder = DocumentFile.fromTreeUri(context, uri)
                        ?: error("Unable to access selected folder")
                    val fileName = "route_${routeId}.csv"
                    folder.findFile(fileName)?.delete()
                    val file = folder.createFile("text/csv", fileName)
                        ?: error("Unable to create route CSV")
                    context.contentResolver.openOutputStream(file.uri, "w")?.use { output ->
                        output.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output stream")
                }
            }
            isSaving = false
            snackbarHostState.showSnackbar(
                if (result.isSuccess) "Route saved" else "Couldn't save the CSV file",
            )
        }
    }

    val folderPickerForSave = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            selectedFolderUri = uri
            writeCsv(uri)
        }
    }

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

         SnackbarHost(hostState = snackbarHostState)

         Button(
             onClick = {
                 val uri = selectedFolderUri
                 if (uri == null) showLocationDialog = true else writeCsv(uri)
             },
             enabled = uiState.stops.isNotEmpty() && !isSaving,
             modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
         ) {
             if (isSaving) {
                 CircularProgressIndicator(modifier = Modifier.height(20.dp))
             } else {
                 Text(text = "Save Route")
             }
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

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text(text = "Choose a location") },
            text = { Text(text = "Select a folder where the CSV file will be saved.") },
            confirmButton = {
                Button(onClick = {
                    showLocationDialog = false
                    folderPickerForSave.launch(null)
                }) {
                    Text(text = "Choose folder")
                }
            },
            dismissButton = {
                Button(onClick = { showLocationDialog = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}
