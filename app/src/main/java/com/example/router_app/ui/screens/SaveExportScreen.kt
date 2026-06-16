package com.example.router_app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.router_app.data.export.CsvExporter
import com.example.router_app.data.local.AppDatabase
import com.example.router_app.data.local.Route
import com.example.router_app.ui.camera.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaveExportScreen(
    cameraViewModel: CameraViewModel,
    onSaveComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val stops by cameraViewModel.sessionStops.collectAsState()
    val existingRouteId by cameraViewModel.existingRouteId.collectAsState()
    val routeConfig by cameraViewModel.routeConfig.collectAsState()
    val isEditing = existingRouteId != null

    val now = remember { Date() }
    val defaultRouteName = remember(now) {
        "Route - ${SimpleDateFormat("MMM dd - HH:mm", Locale.getDefault()).format(now)}"
    }
    val defaultCsvName = remember(now) {
        val datePart = SimpleDateFormat("MMM_dd_HHmm", Locale.US).format(now).lowercase(Locale.US)
        "route_${datePart}.csv"
    }

    var routeName by rememberSaveable(routeConfig.routeName) { mutableStateOf(routeConfig.routeName) }
    var csvFileName by rememberSaveable { mutableStateOf(defaultCsvName) }
    var selectedFolderUri by rememberSaveable(routeConfig.folderUri?.toString()) {
        mutableStateOf(routeConfig.folderUri)
    }
    var isSaving by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    // True when the picker was opened from the save action, so we save as soon as a folder is granted.
    var saveAfterPick by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Saves the route to Room AND writes the CSV to the chosen folder, then leaves the screen.
    val saveRoute: (Uri) -> Unit = { folderUri ->
        scope.launch {
            isSaving = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(context)
                    val routeId = existingRouteId?.also { id ->
                        val startOrder = db.stopDao().getByRouteId(id).size + 1
                        stops.mapIndexed { index, stop ->
                            stop.copy(routeId = id, order = startOrder + index, id = 0L)
                        }.forEach { db.stopDao().insert(it) }
                    } ?: run {
                        val newId = db.routeDao().insert(
                            Route(
                                name = routeName.ifBlank { defaultRouteName },
                                createdAt = System.currentTimeMillis(),
                                city = routeConfig.city,
                            ),
                        )
                        stops.mapIndexed { index, stop ->
                            stop.copy(routeId = newId, order = index + 1, id = 0L)
                        }.forEach { db.stopDao().insert(it) }
                        newId
                    }

                    // Export the full, persisted route (existing + newly added stops).
                    val allStops = db.stopDao().getByRouteId(routeId)
                    val csv = CsvExporter.buildCsv(allStops)
                    val fileName = if (isEditing) "route_${routeId}.csv" else csvFileName.ifBlank { defaultCsvName }
                    val folder = DocumentFile.fromTreeUri(context, folderUri)
                        ?: error("Unable to access selected folder")
                    folder.findFile(fileName)?.delete()
                    val file = folder.createFile("text/csv", fileName)
                        ?: error("Unable to create CSV file")
                    context.contentResolver.openOutputStream(file.uri, "w")?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to write CSV file")
                }
            }
            isSaving = false
            if (result.isSuccess) {
                onSaveComplete()
            } else {
                snackbarHostState.showSnackbar("Couldn't save the route. Check the folder and try again.")
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            selectedFolderUri = uri
            if (saveAfterPick) {
                saveAfterPick = false
                saveRoute(uri)
            }
        }
    }

    // Single entry point for the save button: prompt for a folder when none is set.
    val onSaveClicked: () -> Unit = {
        val folderUri = selectedFolderUri
        if (folderUri == null) {
            showLocationDialog = true
        } else {
            saveRoute(folderUri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEditing) "Add Stops to Route" else "Save Route",
            style = MaterialTheme.typography.titleLarge,
        )

        if (!isEditing) {
            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text(text = "Route name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = csvFileName,
                onValueChange = { csvFileName = it },
                label = { Text(text = "CSV filename") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                saveAfterPick = false
                folderPicker.launch(null)
            }) {
                Text(text = "Pick folder")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedFolderUri?.lastPathSegment ?: "No folder selected",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = if (isEditing) "New stops to add" else "Stops",
            style = MaterialTheme.typography.titleMedium,
        )
        if (stops.isEmpty()) {
            Text(text = "No scanned stops", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(stops) { index, stop ->
                    Text(text = "${index + 1}. ${stop.address}")
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)

        Button(
            onClick = onSaveClicked,
            enabled = stops.isNotEmpty() && !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text(text = "Save Route")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onBack, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text(text = "Choose a location") },
            text = { Text(text = "Select a folder where the CSV file will be saved.") },
            confirmButton = {
                Button(onClick = {
                    showLocationDialog = false
                    saveAfterPick = true
                    folderPicker.launch(null)
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
