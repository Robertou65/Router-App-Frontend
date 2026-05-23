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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.router_app.data.local.AppDatabase
import com.example.router_app.data.local.Route
import com.example.router_app.data.local.Stop
import com.example.router_app.data.export.CsvExporter
import com.example.router_app.ui.camera.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
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
    val now = remember { Date() }
    val defaultRouteName = remember(now) {
        "Route - ${SimpleDateFormat("MMM dd - HH:mm", Locale.getDefault()).format(now)}"
    }
    val defaultCsvName = remember(now) {
        val datePart = SimpleDateFormat("MMM_dd_HHmm", Locale.US).format(now).lowercase(Locale.US)
        "route_${datePart}.csv"
    }

    var routeName by rememberSaveable { mutableStateOf(defaultRouteName) }
    var csvFileName by rememberSaveable { mutableStateOf(defaultCsvName) }
    var selectedFolderUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            selectedFolderUri = uri
        }
    }

    if (existingRouteId != null) {
        // Edit mode
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Add Stops to Route", style = MaterialTheme.typography.titleLarge)
            
            Text(text = "New stops to add:", style = MaterialTheme.typography.titleMedium)
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
                onClick = {
                    val db = AppDatabase.getInstance(context)
                    scope.launch {
                        isSaving = true
                        withContext(Dispatchers.IO) {
                            val existingStops = db.stopDao().getByRouteId(existingRouteId!!)
                            val startOrder = existingStops.size + 1
                            val stopsToInsert = stops.mapIndexed { index, stop ->
                                stop.copy(
                                    routeId = existingRouteId!!,
                                    order = startOrder + index,
                                    id = 0L
                                )
                            }
                            stopsToInsert.forEach { db.stopDao().insert(it) }
                        }
                        isSaving = false
                        onSaveComplete()
                    }
                },
                enabled = stops.isNotEmpty() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(text = "Save to Route")
                }
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Back")
            }
        }
    } else {
        // Create mode
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Save & Export", style = MaterialTheme.typography.titleLarge)

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { folderPicker.launch(null) }) {
                    Text(text = "Pick folder")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = selectedFolderUri?.toString() ?: "No folder selected",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(text = "Stops", style = MaterialTheme.typography.titleMedium)
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
                onClick = {
                    val db = AppDatabase.getInstance(context)
                    scope.launch {
                        isSaving = true
                        withContext(Dispatchers.IO) {
                            val routeId = db.routeDao().insert(
                                Route(
                                    name = routeName.ifBlank { defaultRouteName },
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                            val stopsToInsert = stops.mapIndexed { index, stop ->
                                stop.copy(routeId = routeId, order = index + 1)
                            }
                            stopsToInsert.forEach { db.stopDao().insert(it) }
                        }
                        isSaving = false
                        onSaveComplete()
                    }
                },
                enabled = stops.isNotEmpty() && !isSaving && !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(text = "Save Route")
                }
            }

            Button(
                onClick = {
                    val folderUri = selectedFolderUri
                    if (folderUri == null) {
                        scope.launch { snackbarHostState.showSnackbar("Please select a folder first.") }
                        return@Button
                    }
                    if (stops.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("No stops to export.") }
                        return@Button
                    }
                    scope.launch {
                        isExporting = true
                        val csv = CsvExporter.buildCsv(stops)
                        val finalFileName = csvFileName.ifBlank { defaultCsvName }
                        val created = withContext(Dispatchers.IO) {
                            val folder = DocumentFile.fromTreeUri(context, folderUri)
                            val existing = folder?.findFile(finalFileName)
                            existing?.delete()
                            folder?.createFile("text/csv", finalFileName)
                        }
                        if (created == null) {
                            isExporting = false
                            snackbarHostState.showSnackbar("Unable to create file in selected folder.")
                            return@launch
                        }
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(created.uri, "w")?.use { out ->
                                out.write(csv.toByteArray(Charsets.UTF_8))
                            }
                            val cacheFile = File(context.cacheDir, finalFileName)
                            cacheFile.writeText(csv, Charsets.UTF_8)
                        }
                        val cacheFile = File(context.cacheDir, finalFileName)
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
                        isExporting = false
                    }
                },
                enabled = stops.isNotEmpty() && !isSaving && !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(text = "Export to Routin")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Back")
            }
        }
    }
}
