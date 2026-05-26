package com.example.router_app.data.ai

sealed class AiExtractionResult {
    data class Success(val address: String) : AiExtractionResult()
    data class Error(val type: ErrorType) : AiExtractionResult()

    enum class ErrorType {
        AddressNotFound,
        AuthError,
        ConnectionError,
        Timeout,
    }
}
