package com.example.router_app.ui.camera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.router_app.data.ai.AiAddressExtractor
import com.example.router_app.data.ai.AiExtractionResult
import com.example.router_app.data.geocoding.GeocodingRepository
import com.example.router_app.data.geocoding.GeocodingResult
import com.example.router_app.data.local.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CameraViewModel(
    private val aiAddressExtractor: AiAddressExtractor = AiAddressExtractor(),
    private val geocodingRepository: GeocodingRepository = GeocodingRepository(),
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
        object Geocoding : ScanState()
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

            dataLines.forEachIndexed { index, line ->
                _importState.value = ImportState.Importing(index + 1, total)
                val cols = parseCsvLine(line)
                val nameCol = cols.getOrNull(0)?.trim().orEmpty()
                val addressCol = cols.getOrNull(1)?.trim().orEmpty()
                val lat = cols.getOrNull(2)?.trim()?.toDoubleOrNull()
                val lng = cols.getOrNull(3)?.trim()?.toDoubleOrNull()
                if (addressCol.isBlank()) {
                    failed++
                    return@forEachIndexed
                }
                val label = nameCol.ifBlank { "Package #${_sessionStops.value.size + 1}" }
                if (lat != null && lng != null) {
                    val stop = Stop(
                        id = -(_sessionStops.value.size + 1L),
                        routeId = 0L,
                        label = label,
                        rawOcrText = "",
                        address = addressCol,
                        lat = lat,
                        lng = lng,
                        order = _sessionStops.value.size + 1,
                    )
                    _sessionStops.value = _sessionStops.value + stop
                    imported++
                } else {
                    val geoAddress = buildGeocodingAddress(addressCol, _routeConfig.value.city)
                    when (val result = geocodingRepository.geocodeAddress(geoAddress)) {
                        is GeocodingResult.Success -> {
                            val stop = Stop(
                                id = -(_sessionStops.value.size + 1L),
                                routeId = 0L,
                                label = label,
                                rawOcrText = "",
                                address = addressCol,
                                lat = result.lat,
                                lng = result.lng,
                                order = _sessionStops.value.size + 1,
                            )
                            _sessionStops.value = _sessionStops.value + stop
                            imported++
                        }
                        is GeocodingResult.Error -> failed++
                    }
                }
            }
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
            .mapIndexed { index, stop ->
                stop.copy(
                    label = "Package #${index + 1}",
                    order = index + 1,
                )
            }
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
            val extracted = aiAddressExtractor.extract(text, _routeConfig.value.city)
            val address = when (extracted) {
                is AiExtractionResult.Success -> extracted.address
                is AiExtractionResult.Error -> {
                    val reason = when (extracted.type) {
                        AiExtractionResult.ErrorType.AddressNotFound -> "Address not found"
                        AiExtractionResult.ErrorType.AuthError -> "Server auth error"
                        AiExtractionResult.ErrorType.Timeout -> "Server timeout"
                        AiExtractionResult.ErrorType.ConnectionError -> "Connection error"
                    }
                    failWith(reason)
                    return@launch
                }
            }

            _scanState.value = ScanState.Geocoding

            val geoAddress = buildGeocodingAddress(address, _routeConfig.value.city)
            when (val result = geocodingRepository.geocodeAddress(geoAddress)) {
                is GeocodingResult.Success -> {
                    val stop = Stop(
                        id = -(_sessionStops.value.size + 1L),
                        routeId = 0L,
                        label = "Package #${_sessionStops.value.size + 1}",
                        rawOcrText = text,
                        address = address,
                        lat = result.lat,
                        lng = result.lng,
                        order = _sessionStops.value.size + 1,
                    )
                    _sessionStops.value = _sessionStops.value + stop
                    succeedWith(stop)
                }
                is GeocodingResult.Error -> {
                    val reason = when (result.type) {
                        GeocodingResult.ErrorType.AddressNotFound -> "Address not found"
                        GeocodingResult.ErrorType.ApiKeyError -> "API key error"
                        GeocodingResult.ErrorType.ConnectionError -> "Connection error"
                    }
                    failWith(reason)
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

        _scanState.value = ScanState.Geocoding

        viewModelScope.launch {
            val geoAddress = buildGeocodingAddress(address, _routeConfig.value.city)
            when (val result = geocodingRepository.geocodeAddress(geoAddress)) {
                is GeocodingResult.Success -> {
                    val stop = Stop(
                        id = -(_sessionStops.value.size + 1L),
                        routeId = 0L,
                        label = "Package #${_sessionStops.value.size + 1}",
                        rawOcrText = address,
                        address = address,
                        lat = result.lat,
                        lng = result.lng,
                        order = _sessionStops.value.size + 1,
                    )
                    _sessionStops.value = _sessionStops.value + stop
                    succeedWith(stop)
                }
                is GeocodingResult.Error -> {
                    val reason = when (result.type) {
                        GeocodingResult.ErrorType.AddressNotFound -> "Address not found"
                        GeocodingResult.ErrorType.ApiKeyError -> "API key error"
                        GeocodingResult.ErrorType.ConnectionError -> "Connection error"
                    }
                    failWith(reason)
                }
            }
        }
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

    private fun buildGeocodingAddress(address: String, city: String): String {
        val lower = address.lowercase()
        return if (lower.contains(city.lowercase()) || lower.contains("colombia")) {
            address
        } else {
            "$address, $city, Colombia"
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
