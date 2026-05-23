package com.example.router_app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.router_app.data.local.AppDatabase
import com.example.router_app.data.local.RouteSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RouteHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val routeDao = AppDatabase.getInstance(application).routeDao()
    private val stopDao = AppDatabase.getInstance(application).stopDao()

    private val _routes = MutableStateFlow<List<RouteSummary>>(emptyList())
    val routes: StateFlow<List<RouteSummary>> = _routes

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _deletingRouteIds = MutableStateFlow<Set<Long>>(emptySet())
    val deletingRouteIds: StateFlow<Set<Long>> = _deletingRouteIds

    private var routesJob: Job? = null

    init {
        observeRoutes()
    }

    private fun observeRoutes() {
        routesJob?.cancel()
        routesJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            routeDao.getAllWithStopCount().collectLatest { routes ->
                _routes.value = routes
                _isLoading.value = false
            }
        }
    }

    fun deleteRoute(routeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingRouteIds.value = _deletingRouteIds.value + routeId
            routeDao.deleteById(routeId)
            stopDao.deleteByRouteId(routeId)
            _deletingRouteIds.value = _deletingRouteIds.value - routeId
        }
    }
}
