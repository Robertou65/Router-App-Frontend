package com.example.router_app.data.geocoding

import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {
    @GET("maps/api/geocode/json")
    suspend fun geocode(
        @Query("address") address: String,
        @Query("key") apiKey: String,
        // Restrict results to Colombia so abbreviated nomenclature (e.g. "Carrera 18B
        // # 32-06 Sur") doesn't resolve to a same-named street in another country.
        @Query("components") components: String = "country:CO",
        @Query("region") region: String = "co",
        @Query("language") language: String = "es",
    ): GeocodingResponse
}
