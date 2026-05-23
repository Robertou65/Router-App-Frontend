package com.example.router_app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: Route): Long

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    suspend fun getAll(): List<Route>

    @Query(
        """
        SELECT routes.id, routes.name, routes.createdAt, COUNT(stops.id) AS stopCount
        FROM routes
        LEFT JOIN stops ON routes.id = stops.routeId
        GROUP BY routes.id
        ORDER BY routes.createdAt DESC
        """,
    )
    fun getAllWithStopCount(): Flow<List<RouteSummary>>

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Route?

    @Delete
    suspend fun delete(route: Route)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteById(routeId: Long)
}
