package com.example.router_app.data.format

import com.example.router_app.data.geocoding.GeocodingRepository
import com.example.router_app.data.geocoding.GeocodingResult

/**
 * The shared format→geocode chain: turn raw label/manual text into a clean Colombian
 * address and resolve it to coordinates. Reused by the camera scan flow
 * ([com.example.router_app.ui.camera.CameraViewModel]) and route editing
 * ([com.example.router_app.ui.detail.RouteDetailViewModel]) so both behave identically.
 */
class AddressResolver(
    private val geocodingRepository: GeocodingRepository = GeocodingRepository(),
) {
    sealed class Result {
        data class Ok(
            val name: String?,
            val address: String,
            val lat: Double,
            val lng: Double,
        ) : Result()

        data class Fail(val reason: String) : Result()
    }

    suspend fun resolve(rawText: String, fallbackCity: String): Result {
        val parsed = when (val result = ColombianAddressFormatter.format(rawText, fallbackCity)) {
            is ColombianAddressFormatter.Result.Success -> result.address
            ColombianAddressFormatter.Result.NoAddressFound -> return Result.Fail("No address detected")
        }
        return when (val geo = geocodingRepository.geocodeAddress(parsed.geocodeQuery)) {
            is GeocodingResult.Success -> Result.Ok(
                name = parsed.recipientName,
                address = parsed.displayAddress,
                lat = geo.lat,
                lng = geo.lng,
            )
            is GeocodingResult.Error -> Result.Fail(
                when (geo.type) {
                    GeocodingResult.ErrorType.AddressNotFound -> "Address not found"
                    GeocodingResult.ErrorType.ApiKeyError -> "Google API key error"
                    GeocodingResult.ErrorType.ConnectionError -> "Connection error"
                },
            )
        }
    }
}
