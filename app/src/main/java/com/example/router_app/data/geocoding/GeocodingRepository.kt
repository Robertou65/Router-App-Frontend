package com.example.router_app.data.geocoding

import com.example.router_app.BuildConfig
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class GeocodingRepository(
    private val api: GeocodingApiService = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeocodingApiService::class.java),
) {
    suspend fun geocodeAddress(address: String): GeocodingResult {
        return try {
            val response = api.geocode(address, BuildConfig.GOOGLE_MAPS_API_KEY)
            when (response.status) {
                "OK" -> {
                    val location = response.results.firstOrNull()?.geometry?.location
                    if (location == null) {
                        GeocodingResult.Error(GeocodingResult.ErrorType.AddressNotFound)
                    } else {
                        GeocodingResult.Success(location.lat, location.lng)
                    }
                }
                "ZERO_RESULTS" -> GeocodingResult.Error(GeocodingResult.ErrorType.AddressNotFound)
                "REQUEST_DENIED" -> GeocodingResult.Error(GeocodingResult.ErrorType.ApiKeyError)
                else -> GeocodingResult.Error(GeocodingResult.ErrorType.ConnectionError)
            }
        } catch (error: IOException) {
            GeocodingResult.Error(GeocodingResult.ErrorType.ConnectionError)
        } catch (error: HttpException) {
            GeocodingResult.Error(GeocodingResult.ErrorType.ConnectionError)
        }
    }
}
