package com.example.router_app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StopDao {
    @Insert
    suspend fun insert(stop: Stop): Long

    @Query("SELECT * FROM stops WHERE routeId = :routeId ORDER BY `order` ASC")
    suspend fun getByRouteId(routeId: Long): List<Stop>

    @Query("DELETE FROM stops WHERE routeId = :routeId")
    suspend fun deleteByRouteId(routeId: Long)
}
