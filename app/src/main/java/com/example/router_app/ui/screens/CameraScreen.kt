package com.example.router_app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.router_app.data.ocr.OcrTextAnalyzer
import com.example.router_app.data.local.Stop
import com.example.router_app.ui.camera.CameraViewModel

@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun CameraScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(cameraPermissionState.status, hasRequested) {
        if (!hasRequested && cameraPermissionState.status is PermissionStatus.Denied) {
            hasRequested = true
            cameraPermissionState.launchPermissionRequest()
        }
    }

    when (val status = cameraPermissionState.status) {
        is PermissionStatus.Granted -> {
            val cameraViewModel: CameraViewModel = viewModel()
            CameraScreenContent(
                onFinish = onFinish,
                viewModel = cameraViewModel,
            )
        }
        is PermissionStatus.Denied -> {
            when {
                status.shouldShowRationale -> {
                    PermissionRationaleDialog(
                        onConfirm = { cameraPermissionState.launchPermissionRequest() },
                        onDismiss = onBack,
                    )
                }
                hasRequested -> {
                    PermissionSettingsMessage(
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            context.startActivity(intent)
                        },
                        onBack = onBack,
                    )
                }
                else -> {
                    PermissionRequestMessage(onBack = onBack)
                }
            }
        }
    }
}

@Composable
private fun CameraScreenContent(
    onFinish: () -> Unit,
    viewModel: CameraViewModel,
) {
    val scanState by viewModel.scanState.collectAsState()
    val sessionStops by viewModel.sessionStops.collectAsState()
    val sessionPanelState by viewModel.sessionPanelState.collectAsState()
    val isBusy = scanState !is CameraViewModel.ScanState.Idle
    val listState = rememberLazyListState()
    var stopPendingRemoval by remember { mutableStateOf<Stop?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualAddressText by remember { mutableStateOf("") }

    BackHandler(enabled = sessionPanelState.isOpen) {
        viewModel.closeSessionPanel()
    }

    LaunchedEffect(sessionStops.size) {
        if (sessionStops.isNotEmpty()) {
            listState.animateScrollToItem(sessionStops.lastIndex)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
        )

        if (sessionPanelState.isOpen) {
            SessionAddressPanel(
                sessionStops = sessionStops,
                selectedStopId = sessionPanelState.selectedStopId,
                onClose = { viewModel.closeSessionPanel() },
                onSelectStop = { viewModel.selectStop(it) },
                onRemoveSelected = {
                    stopPendingRemoval = sessionStops.firstOrNull { stop -> stop.id == sessionPanelState.selectedStopId }
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                SessionSummaryBar(
                    sessionStops = sessionStops,
                    scanState = scanState,
                    onOpenPanel = { viewModel.openSessionPanel() },
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = { viewModel.requestScan() },
                        enabled = scanState is CameraViewModel.ScanState.Idle,
                    ) {
                        Text(text = "Scan")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            manualAddressText = ""
                            showManualInput = true
                        },
                        enabled = scanState is CameraViewModel.ScanState.Idle,
                    ) {
                        Text(text = "Type Address")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onFinish,
                        enabled = sessionStops.isNotEmpty() && !isBusy,
                    ) {
                        Text(text = "Finish")
                    }
                }
            }
        }

        ScanStateOverlay(
            scanState = scanState,
            modifier = Modifier.fillMaxSize(),
        )

    }

    stopPendingRemoval?.let { stop ->
        AlertDialog(
            onDismissRequest = { stopPendingRemoval = null },
            title = { Text(text = "Remove address?") },
            text = { Text(text = stop.address) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeSelectedStop()
                        stopPendingRemoval = null
                        if (viewModel.sessionStops.value.isEmpty()) {
                            viewModel.closeSessionPanel()
                        }
                    },
                ) {
                    Text(text = "Remove")
                }
            },
            dismissButton = {
                Button(onClick = { stopPendingRemoval = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (showManualInput) {
        AlertDialog(
            onDismissRequest = { showManualInput = false },
            title = { Text(text = "Enter address manually") },
            text = {
                OutlinedTextField(
                    value = manualAddressText,
                    onValueChange = { manualAddressText = it },
                    label = { Text(text = "Address") },
                    placeholder = { Text(text = "e.g. Carrera 18b # 32-06 Sur, Bogotá") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showManualInput = false
                        viewModel.onManualAddress(manualAddressText.trim())
                        manualAddressText = ""
                    },
                    enabled = manualAddressText.isNotBlank(),
                ) {
                    Text(text = "Search")
                }
            },
            dismissButton = {
                Button(onClick = { showManualInput = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun SessionSummaryBar(
    sessionStops: List<com.example.router_app.data.local.Stop>,
    scanState: CameraViewModel.ScanState,
    onOpenPanel: () -> Unit,
) {
    val enabled = sessionStops.isNotEmpty() && scanState is CameraViewModel.ScanState.Idle
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onOpenPanel)
            .padding(12.dp)
            .widthIn(max = 360.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sessionStops.isEmpty()) {
                Text(text = "No scans yet", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessionStops.take(3).forEachIndexed { index, stop ->
                        Text(
                            text = "${index + 1}. ${stop.address}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SessionAddressPanel(
    sessionStops: List<com.example.router_app.data.local.Stop>,
    selectedStopId: Long?,
    onClose: () -> Unit,
    onSelectStop: (Long) -> Unit,
    onRemoveSelected: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Session Addresses", style = MaterialTheme.typography.titleLarge, color = Color.White)
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxHeight(0.82f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(sessionStops, key = { _, item -> item.order }) { _, stop ->
                    val selected = selectedStopId == stop.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStop(stop.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .background(if (selected) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = if (selected) "●" else "○", color = MaterialTheme.colorScheme.onSurface)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(text = stop.label, style = MaterialTheme.typography.titleMedium)
                                Text(text = stop.address, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onRemoveSelected,
                enabled = selectedStopId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Text(text = "Remove Selected")
            }
        }
    }
}

@Composable
private fun ScanStateOverlay(
    scanState: CameraViewModel.ScanState,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (scanState) {
        is CameraViewModel.ScanState.Idle -> return
        is CameraViewModel.ScanState.Scanning ->
            "Reading label…" to Color.Black.copy(alpha = 0.5f)
        is CameraViewModel.ScanState.Extracting ->
            "Parsing address…" to Color.Black.copy(alpha = 0.5f)
        is CameraViewModel.ScanState.Geocoding ->
            "Adding stop…" to Color.Black.copy(alpha = 0.5f)
        is CameraViewModel.ScanState.Success ->
            "Address added ✓" to Color(0x6600C853)
        is CameraViewModel.ScanState.Failure ->
            scanState.reason to Color(0x66D50000)
    }
    Box(
        modifier = modifier.background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    OcrTextAnalyzer(
                        shouldAnalyze = { viewModel.shouldAnalyze() },
                        onResult = { viewModel.onOcrResult(it) },
                        onFailure = { viewModel.onOcrFailure() },
                    ),
                )
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

@Composable
private fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Camera permission required") },
        text = { Text(text = "We need access to the camera to scan package labels.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Continue")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Not now")
            }
        },
    )
}

@Composable
private fun PermissionSettingsMessage(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission is permanently denied. Please enable it in Settings.",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onOpenSettings) {
            Text(text = "Open Settings")
        }
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun PermissionRequestMessage(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Requesting camera permission...",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}
