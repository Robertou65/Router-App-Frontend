package com.example.router_app.data.geocoding

sealed class GeocodingResult {
    data class Success(val lat: Double, val lng: Double) : GeocodingResult()
    data class Error(val type: ErrorType) : GeocodingResult()

    enum class ErrorType {
        AddressNotFound,
        ApiKeyError,
        ConnectionError,
    }
}
