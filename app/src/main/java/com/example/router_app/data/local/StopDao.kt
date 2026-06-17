package com.example.router_app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface StopDao {
    @Insert
    suspend fun insert(stop: Stop): Long

    @Update
    suspend fun update(stop: Stop)

    @Query("SELECT * FROM stops WHERE routeId = :routeId ORDER BY `order` ASC")
    suspend fun getByRouteId(routeId: Long): List<Stop>

    @Query("DELETE FROM stops WHERE routeId = :routeId")
    suspend fun deleteByRouteId(routeId: Long)

    @Query("DELETE FROM stops WHERE id = :stopId")
    suspend fun deleteById(stopId: Long)
}
