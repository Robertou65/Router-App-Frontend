package com.example.router_app.data.ai

sealed class AiExtractionResult {
    data class Success(val address: String, val lat: Double, val lng: Double) : AiExtractionResult()
    data class Error(val type: ErrorType) : AiExtractionResult()

    enum class ErrorType {
        AddressNotFound,
        AuthError,
        ConnectionError,
        Timeout,
    }
}
