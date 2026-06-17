package com.example.router_app.ui.camera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.router_app.data.format.AddressResolver
import com.example.router_app.data.local.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CameraViewModel(
    private val addressResolver: AddressResolver = AddressResolver(),
) : ViewModel() {
    data class RouteConfig(
        val routeName: String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
        val folderUri: Uri? = null,
        val city: String = "Medellín",
    )

    data class SessionPanelState(
        val isOpen: Boolean = false,
        val selectedStopId: Long? = null,
    )

    sealed class ImportState {
        object Idle : ImportState()
        data class Importing(val done: Int, val total: Int) : ImportState()
        data class Done(val imported: Int, val failed: Int) : ImportState()
    }

    sealed class ScanState {
        object Idle : ScanState()
        object Scanning : ScanState()
        object Extracting : ScanState()
        data class Success(val stop: Stop) : ScanState()
        data class Failure(val reason: String) : ScanState()
    }

    private val scanInProgress = AtomicBoolean(false)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private val _sessionStops = MutableStateFlow<List<Stop>>(emptyList())
    val sessionStops: StateFlow<List<Stop>> = _sessionStops

    private val _sessionPanelState = MutableStateFlow(SessionPanelState())
    val sessionPanelState: StateFlow<SessionPanelState> = _sessionPanelState

    private val _routeConfig = MutableStateFlow(RouteConfig())
    val routeConfig: StateFlow<RouteConfig> = _routeConfig

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val _lastOcrText = MutableStateFlow("")
    val lastOcrText: StateFlow<String> = _lastOcrText

    private val _existingRouteId = MutableStateFlow<Long?>(null)
    val existingRouteId: StateFlow<Long?> = _existingRouteId

    fun requestScan() {
        if (_scanState.value is ScanState.Idle && !_sessionPanelState.value.isOpen) {
            _scanState.value = ScanState.Scanning
        }
    }

    fun setExistingRoute(routeId: Long) {
        _existingRouteId.value = routeId
    }

    fun updateRouteConfig(config: RouteConfig) {
        _routeConfig.value = config
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing(0, 0)
            val lines = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readLines()
                    }.orEmpty()
                }.getOrElse { emptyList() }
            }
            if (lines.isEmpty()) {
                _importState.value = ImportState.Done(0, 0)
                return@launch
            }

            val cleanLines = lines.toMutableList()
            cleanLines[0] = cleanLines[0].removePrefix("\uFEFF")
            val dataLines = cleanLines.drop(1).filter { it.isNotBlank() }
            val total = dataLines.size
            var imported = 0
            var failed = 0

            data class CsvRow(
                val index: Int,
                val name: String,
                val address: String,
                val lat: Double?,
                val lng: Double?,
            )

            data class CsvOutcome(
                val index: Int,
                val name: String,
                val address: String,
                val lat: Double?,
                val lng: Double?,
                val success: Boolean,
            )

            val rows = dataLines.mapIndexed { index, line ->
                val cols = parseCsvLine(line)
                CsvRow(
                    index = index,
                    name = cols.getOrNull(0)?.trim().orEmpty(),
                    address = cols.getOrNull(1)?.trim().orEmpty(),
                    lat = cols.getOrNull(2)?.trim()?.toDoubleOrNull(),
                    lng = cols.getOrNull(3)?.trim()?.toDoubleOrNull(),
                )
            }

            val pendingRows = rows.filter { row ->
                row.address.isNotBlank() && (row.lat == null || row.lng == null)
            }

            val asyncResults = withContext(Dispatchers.IO) {
                coroutineScope {
                    pendingRows.map { row ->
                        async { row to addressResolver.resolve(row.address, _routeConfig.value.city) }
                    }.awaitAll()
                }
            }

            val outcomes = mutableListOf<CsvOutcome>()
            rows.forEach { row ->
                if (row.address.isBlank()) {
                    outcomes.add(
                        CsvOutcome(
                            index = row.index,
                            name = row.name,
                            address = row.address,
                            lat = null,
                            lng = null,
                            success = false,
                        )
                    )
                } else if (row.lat != null && row.lng != null) {
                    outcomes.add(
                        CsvOutcome(
                            index = row.index,
                            name = row.name,
                            address = row.address,
                            lat = row.lat,
                            lng = row.lng,
                            success = true,
                        )
                    )
                }
            }

            asyncResults.forEach { (row, resolved) ->
                when (resolved) {
                    is AddressResolver.Result.Ok -> outcomes.add(
                        CsvOutcome(
                            index = row.index,
                            name = row.name.ifBlank { resolved.name.orEmpty() },
                            address = resolved.address,
                            lat = resolved.lat,
                            lng = resolved.lng,
                            success = true,
                        )
                    )
                    is AddressResolver.Result.Fail -> outcomes.add(
                        CsvOutcome(
                            index = row.index,
                            name = row.name,
                            address = row.address,
                            lat = null,
                            lng = null,
                            success = false,
                        )
                    )
                }
            }

            val updatedStops = _sessionStops.value.toMutableList()
            outcomes.sortedBy { it.index }.forEachIndexed { index, outcome ->
                _importState.value = ImportState.Importing(index + 1, total)
                if (!outcome.success) {
                    failed++
                    return@forEachIndexed
                }
                val stop = Stop(
                    id = -(updatedStops.size + 1L),
                    routeId = 0L,
                    label = (updatedStops.size + 1).toString(),
                    rawOcrText = "",
                    address = outcome.address,
                    lat = outcome.lat ?: 0.0,
                    lng = outcome.lng ?: 0.0,
                    order = updatedStops.size + 1,
                )
                updatedStops.add(stop)
                imported++
            }

            _sessionStops.value = updatedStops
            _importState.value = ImportState.Done(imported, failed)
        }
    }

    fun openSessionPanel() {
        if (_scanState.value is ScanState.Idle && _sessionStops.value.isNotEmpty()) {
            _sessionPanelState.value = _sessionPanelState.value.copy(isOpen = true)
        }
    }

    fun closeSessionPanel() {
        _sessionPanelState.value = SessionPanelState()
    }

    fun selectStop(stopId: Long) {
        val current = _sessionPanelState.value.selectedStopId
        _sessionPanelState.value = _sessionPanelState.value.copy(
            selectedStopId = if (current == stopId) null else stopId,
        )
    }

    fun removeSelectedStop() {
        val selectedId = _sessionPanelState.value.selectedStopId ?: return
        val remaining = _sessionStops.value.filterNot { it.id == selectedId }
            .mapIndexed { index, stop -> stop.copy(label = (index + 1).toString(), order = index + 1) }
        _sessionStops.value = remaining
        _sessionPanelState.value = _sessionPanelState.value.copy(selectedStopId = null)
    }

    fun shouldAnalyze(): Boolean {
        return _scanState.value is ScanState.Scanning && scanInProgress.compareAndSet(false, true)
    }

    fun onOcrResult(text: String) {
        scanInProgress.set(false)
        if (_scanState.value !is ScanState.Scanning) return

        if (text.isBlank()) {
            failWith("No text detected")
            return
        }

        _lastOcrText.value = text
        _scanState.value = ScanState.Extracting

        viewModelScope.launch {
            when (val resolved = addressResolver.resolve(text, _routeConfig.value.city)) {
                is AddressResolver.Result.Fail -> failWith(resolved.reason)
                is AddressResolver.Result.Ok -> {
                    val stop = newSessionStop(resolved, rawOcrText = text)
                    _sessionStops.value = _sessionStops.value + stop
                    succeedWith(stop)
                }
            }
        }
    }

    fun onOcrFailure() {
        scanInProgress.set(false)
        if (_scanState.value is ScanState.Scanning) {
            failWith("No text detected")
        }
    }

    fun onManualAddress(address: String) {
        if (_scanState.value !is ScanState.Idle) return
        if (address.isBlank()) return

        _scanState.value = ScanState.Extracting

        viewModelScope.launch {
            when (val resolved = addressResolver.resolve(address, _routeConfig.value.city)) {
                is AddressResolver.Result.Fail -> failWith(resolved.reason)
                is AddressResolver.Result.Ok -> {
                    val stop = newSessionStop(resolved, rawOcrText = address)
                    _sessionStops.value = _sessionStops.value + stop
                    succeedWith(stop)
                }
            }
        }
    }

    private fun newSessionStop(resolved: AddressResolver.Result.Ok, rawOcrText: String): Stop {
        val nextIndex = _sessionStops.value.size + 1
        return Stop(
            id = -nextIndex.toLong(),
            routeId = 0L,
            label = nextIndex.toString(),
            rawOcrText = rawOcrText,
            address = resolved.address,
            lat = resolved.lat,
            lng = resolved.lng,
            order = nextIndex,
        )
    }

    private fun succeedWith(stop: Stop) {
        _scanState.value = ScanState.Success(stop)
        viewModelScope.launch {
            delay(1500)
            _scanState.value = ScanState.Idle
        }
    }

    private fun failWith(reason: String) {
        _scanState.value = ScanState.Failure(reason)
        viewModelScope.launch {
            delay(1500)
            _scanState.value = ScanState.Idle
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when {
                line[i] == '"' && !inQuotes -> inQuotes = true
                line[i] == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                line[i] == '"' && inQuotes -> inQuotes = false
                line[i] == ';' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(line[i])
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

/** A session stop paired with its real 1-based position in the session. */
data class NumberedStop(val number: Int, val stop: Stop)

/**
 * The most recent [windowSize] stops as a sliding window, each tagged with its real
 * position in the session (e.g. after 5 scans → stops numbered 3, 4, 5), ordered
 * oldest→newest. Drives the camera overlay's "last three scans" summary.
 */
fun recentStopsWindow(stops: List<Stop>, windowSize: Int = 3): List<NumberedStop> {
    val window = stops.takeLast(windowSize)
    val firstNumber = stops.size - window.size + 1
    return window.mapIndexed { index, stop -> NumberedStop(firstNumber + index, stop) }
}
