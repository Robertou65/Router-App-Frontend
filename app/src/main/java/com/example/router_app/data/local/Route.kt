package com.example.router_app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    // City the route was created for; used as the fallback when formatting manual
    // addresses that don't carry their own city (e.g. "cl 26 51 20").
    val city: String = "Bogotá",
)
