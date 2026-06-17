package com.example.router_app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.router_app.data.format.AddressResolver
import com.example.router_app.data.local.AppDatabase
import com.example.router_app.data.local.Route
import com.example.router_app.data.local.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RouteDetailUiState(
    val route: Route? = null,
    val stops: List<Stop> = emptyList(),
    val isLoading: Boolean = false,
)

class RouteDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val addressResolver = AddressResolver()

    private val _uiState = MutableStateFlow(RouteDetailUiState())
    val uiState: StateFlow<RouteDetailUiState> = _uiState

    private val _deletingStopIds = MutableStateFlow<Set<Long>>(emptySet())
    val deletingStopIds: StateFlow<Set<Long>> = _deletingStopIds

    // Stops with an edit (re-geocode) in flight, so the UI can disable controls
    // and show progress while the network call runs.
    private val _editingStopIds = MutableStateFlow<Set<Long>>(emptySet())
    val editingStopIds: StateFlow<Set<Long>> = _editingStopIds

    // One-shot outcomes of an edit, consumed by the screen to show a snackbar and
    // close the dialog on success.
    private val _editEvents = MutableSharedFlow<EditResult>()
    val editEvents: SharedFlow<EditResult> = _editEvents

    sealed interface EditResult {
        data class Success(val stopId: Long) : EditResult
        data class Error(val stopId: Long, val reason: String) : EditResult
    }

    fun load(routeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val route = db.routeDao().getById(routeId)
            val stops = db.stopDao().getByRouteId(routeId)
            _uiState.value = RouteDetailUiState(route = route, stops = stops, isLoading = false)
        }
    }

    fun removeStop(stopId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingStopIds.value += stopId
            db.stopDao().deleteById(stopId)
            // Reload stops after deletion
            val updatedStops = db.stopDao().getByRouteId(
                _uiState.value.route?.id ?: return@launch
            )
            _uiState.value = _uiState.value.copy(stops = updatedStops)
            _deletingStopIds.value -= stopId
        }
    }

    /**
     * Re-resolve [newText] (format → geocode, using the route's city as fallback) and,
     * only on a confirmed geocode, persist the stop's new address/coordinates. On any
     * failure the stop is left untouched and an [EditResult.Error] is emitted.
     */
    fun editStop(stopId: Long, newText: String) {
        if (newText.isBlank()) return
        val route = _uiState.value.route ?: return
        val current = _uiState.value.stops.firstOrNull { it.id == stopId } ?: return
        if (_editingStopIds.value.contains(stopId)) return

        viewModelScope.launch {
            _editingStopIds.value += stopId
            val resolved = withContext(Dispatchers.IO) {
                addressResolver.resolve(newText, route.city)
            }
            when (resolved) {
                is AddressResolver.Result.Ok -> {
                    val updated = current.copy(
                        address = resolved.address,
                        lat = resolved.lat,
                        lng = resolved.lng,
                    )
                    withContext(Dispatchers.IO) { db.stopDao().update(updated) }
                    _uiState.value = _uiState.value.copy(
                        stops = _uiState.value.stops.map { if (it.id == stopId) updated else it },
                    )
                    _editingStopIds.value -= stopId
                    _editEvents.emit(EditResult.Success(stopId))
                }
                is AddressResolver.Result.Fail -> {
                    _editingStopIds.value -= stopId
                    _editEvents.emit(EditResult.Error(stopId, resolved.reason))
                }
            }
        }
    }
}
