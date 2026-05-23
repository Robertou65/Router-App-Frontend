package com.example.router_app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stops",
    foreignKeys = [
        ForeignKey(
            entity = Route::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routeId")],
)
data class Stop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val label: String,
    val rawOcrText: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val order: Int,
)
