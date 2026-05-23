package com.example.router_app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StopDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var routeDao: RouteDao
    private lateinit var stopDao: StopDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routeDao = db.routeDao()
        stopDao = db.stopDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertQueryAndDeleteStops() = runBlocking {
        val routeId = routeDao.insert(Route(name = "Route - Stops", createdAt = 1_700_000_000_002))
        stopDao.insert(
            Stop(
                routeId = routeId,
                label = "Package #2",
                rawOcrText = "RAW2",
                address = "Address 2",
                lat = 2.0,
                lng = -2.0,
                order = 2,
            ),
        )
        stopDao.insert(
            Stop(
                routeId = routeId,
                label = "Package #1",
                rawOcrText = "RAW1",
                address = "Address 1",
                lat = 1.0,
                lng = -1.0,
                order = 1,
            ),
        )

        val stops = stopDao.getByRouteId(routeId)
        assertEquals(2, stops.size)
        assertEquals(1, stops.first().order)

        stopDao.deleteByRouteId(routeId)
        assertTrue(stopDao.getByRouteId(routeId).isEmpty())
    }
}
