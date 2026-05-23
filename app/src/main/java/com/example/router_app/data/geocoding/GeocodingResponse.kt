package com.example.router_app.data.geocoding

data class GeocodingResponse(
    val status: String,
    val results: List<GeocodingResultItem> = emptyList(),
)

data class GeocodingResultItem(
    val geometry: GeocodingGeometry,
)

data class GeocodingGeometry(
    val location: GeocodingLocation,
)

data class GeocodingLocation(
    val lat: Double,
    val lng: Double,
)
