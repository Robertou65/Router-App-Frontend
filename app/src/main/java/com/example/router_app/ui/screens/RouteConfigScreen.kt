package com.example.router_app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.router_app.ui.camera.CameraViewModel

val COLOMBIAN_CITIES = listOf(
    "Bogotá",
    "Medellín",
    "Cali",
    "Barranquilla",
    "Cartagena",
    "Bucaramanga",
    "Pereira",
    "Manizales",
    "Cúcuta",
    "Ibagué",
    "Santa Marta",
    "Villavicencio",
    "Pasto",
    "Montería",
    "Valledupar",
    "Armenia",
    "Neiva",
    "Popayán",
    "Sincelejo",
    "Tunja",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteConfigScreen(
    cameraViewModel: CameraViewModel,
    onStartScanning: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val routeConfig by cameraViewModel.routeConfig.collectAsState()
    val importState by cameraViewModel.importState.collectAsState()
    val sessionStops by cameraViewModel.sessionStops.collectAsState()

    var routeName by remember(routeConfig.routeName) { mutableStateOf(routeConfig.routeName) }
    var selectedCity by remember(routeConfig.city) { mutableStateOf(routeConfig.city) }
    var selectedFolderUri by remember(routeConfig.folderUri) { mutableStateOf(routeConfig.folderUri) }
    var cityDropdownExpanded by remember { mutableStateOf(false) }

    fun commitConfig() {
        cameraViewModel.updateRouteConfig(
            CameraViewModel.RouteConfig(
                routeName = routeName,
                folderUri = selectedFolderUri,
                city = selectedCity,
            ),
        )
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            selectedFolderUri = uri
            commitConfig()
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            commitConfig()
            cameraViewModel.importCsv(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "New Route", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = routeName,
            onValueChange = {
                routeName = it
                commitConfig()
            },
            label = { Text(text = "Route name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        ExposedDropdownMenuBox(
            expanded = cityDropdownExpanded,
            onExpandedChange = { cityDropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedCity,
                onValueChange = {},
                readOnly = true,
                label = { Text(text = "City") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            DropdownMenu(
                expanded = cityDropdownExpanded,
                onDismissRequest = { cityDropdownExpanded = false },
            ) {
                COLOMBIAN_CITIES.forEach { city ->
                    DropdownMenuItem(
                        text = { Text(text = city) },
                        onClick = {
                            selectedCity = city
                            cityDropdownExpanded = false
                            commitConfig()
                        },
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Save folder", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { folderPicker.launch(null) }) {
                        Text(text = "Pick folder")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectedFolderUri?.lastPathSegment ?: "Not selected",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Import stops from CSV", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Optional. Import a semicolon-delimited CSV with columns: Name;Address;Latitude;Longitude.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (val state = importState) {
                    is CameraViewModel.ImportState.Idle -> Button(
                        onClick = {
                            commitConfig()
                            csvPicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Choose CSV file")
                    }
                    is CameraViewModel.ImportState.Importing -> {
                        val progress = if (state.total > 0) state.done.toFloat() / state.total else 0f
                        Text(text = "Importing ${state.done} / ${state.total}…")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is CameraViewModel.ImportState.Done -> {
                        Text(
                            text = "✓ ${state.imported} stop${if (state.imported != 1) "s" else ""} imported" +
                                if (state.failed > 0) ", ${state.failed} failed" else "",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                cameraViewModel.resetImportState()
                                csvPicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Import another file")
                        }
                    }
                }
            }
        }

        if (sessionStops.isNotEmpty()) {
            Button(
                onClick = {
                    commitConfig()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Done — ${sessionStops.size} stop${if (sessionStops.size != 1) "s" else ""} ready")
            }
        }

        Button(
            onClick = {
                commitConfig()
                onStartScanning()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (sessionStops.isEmpty()) "Start Scanning" else "Continue Scanning (${sessionStops.size} imported)")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Back")
        }
    }
}
