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
    private val cache = object : LinkedHashMap<String, GeocodingResult.Success>(
        16, 0.75f, true  // access-order = true → LRU
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, GeocodingResult.Success>
        ): Boolean = size > 50
    }

    suspend fun geocodeAddress(address: String): GeocodingResult {
        val key = address.trim().lowercase()

        // Check cache first
        val cached = synchronized(cache) { cache[key] }
        cached?.let { return it }

        // Cache miss — call the API
        return try {
            val response = api.geocode(address, BuildConfig.GOOGLE_MAPS_API_KEY)
            when (response.status) {
                "OK" -> {
                    val location = response.results.firstOrNull()?.geometry?.location
                    if (location == null) {
                        GeocodingResult.Error(GeocodingResult.ErrorType.AddressNotFound)
                    } else {
                        val result = GeocodingResult.Success(location.lat, location.lng)
                        synchronized(cache) { cache[key] = result }
                        result
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
