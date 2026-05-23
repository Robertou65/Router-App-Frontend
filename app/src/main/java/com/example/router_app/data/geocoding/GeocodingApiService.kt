package com.example.router_app.data.geocoding

import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {
    @GET("maps/api/geocode/json")
    suspend fun geocode(
        @Query("address") address: String,
        @Query("key") apiKey: String,
    ): GeocodingResponse
}
