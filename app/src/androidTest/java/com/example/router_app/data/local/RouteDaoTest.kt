package com.example.router_app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var routeDao: RouteDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routeDao = db.routeDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndQueryRoute() = runBlocking {
        val route = Route(name = "Route - Test", createdAt = 1_700_000_000_000)
        val id = routeDao.insert(route)

        val byId = routeDao.getById(id)
        assertNotNull(byId)
        assertEquals(id, byId!!.id)

        val all = routeDao.getAll()
        assertEquals(1, all.size)
        assertEquals(id, all.first().id)
    }

    @Test
    fun deleteRoute() = runBlocking {
        val id = routeDao.insert(Route(name = "Route - Delete", createdAt = 1_700_000_000_001))
        val route = routeDao.getById(id)!!

        routeDao.delete(route)

        assertTrue(routeDao.getAll().isEmpty())
    }
}
