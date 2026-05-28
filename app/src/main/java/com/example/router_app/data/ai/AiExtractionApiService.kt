package com.example.router_app.data.ai

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ExtractRequest(val ocr_text: String, val city: String)
data class ExtractResponse(val address: String, val lat: Double, val lng: Double, val success: Boolean)

interface AiExtractionApiService {
    @POST("extract-address")
    suspend fun extractAddress(
        @Header("X-API-Key") apiKey: String,
        @Body request: ExtractRequest,
    ): ExtractResponse
}
