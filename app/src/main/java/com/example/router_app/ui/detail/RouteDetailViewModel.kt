package com.example.router_app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.router_app.data.local.AppDatabase
import com.example.router_app.data.local.Route
import com.example.router_app.data.local.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RouteDetailUiState(
    val route: Route? = null,
    val stops: List<Stop> = emptyList(),
    val isLoading: Boolean = false,
)

class RouteDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(RouteDetailUiState())
    val uiState: StateFlow<RouteDetailUiState> = _uiState

    fun load(routeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val route = db.routeDao().getById(routeId)
            val stops = db.stopDao().getByRouteId(routeId)
            _uiState.value = RouteDetailUiState(route = route, stops = stops, isLoading = false)
        }
    }
}
