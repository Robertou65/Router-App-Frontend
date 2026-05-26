package com.example.router_app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.router_app.data.ai.AiAddressExtractor
import com.example.router_app.data.ai.AiExtractionResult
import com.example.router_app.data.geocoding.GeocodingRepository
import com.example.router_app.data.geocoding.GeocodingResult
import com.example.router_app.data.local.Stop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CameraViewModel(
    private val aiAddressExtractor: AiAddressExtractor = AiAddressExtractor(),
    private val geocodingRepository: GeocodingRepository = GeocodingRepository(),
) : ViewModel() {
    data class SessionPanelState(
        val isOpen: Boolean = false,
        val selectedStopId: Long? = null,
    )

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
            val extracted = aiAddressExtractor.extract(text)
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

            when (val result = geocodingRepository.geocodeAddress(address)) {
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
            when (val result = geocodingRepository.geocodeAddress(address)) {
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
}
