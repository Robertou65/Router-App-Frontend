package com.example.router_app.ui.camera

import com.example.router_app.data.local.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentStopsWindowTest {

    private fun stops(count: Int): List<Stop> =
        (1..count).map { i ->
            Stop(
                id = i.toLong(),
                routeId = 0L,
                label = "Package #$i",
                rawOcrText = "",
                address = "Address $i",
                lat = 0.0,
                lng = 0.0,
                order = i,
            )
        }

    @Test
    fun `shows the last three stops with their real positions`() {
        val window = recentStopsWindow(stops(5))

        assertEquals(3, window.size)
        assertEquals(listOf(3, 4, 5), window.map { it.number })
        // Ordered oldest -> newest within the window.
        assertEquals(listOf("Address 3", "Address 4", "Address 5"), window.map { it.stop.address })
    }

    @Test
    fun `each new scan slides the window forward by one`() {
        assertEquals(listOf(7, 8, 9), recentStopsWindow(stops(9)).map { it.number })
        assertEquals(listOf(8, 9, 10), recentStopsWindow(stops(10)).map { it.number })
    }

    @Test
    fun `fewer than the window size shows all stops numbered from one`() {
        assertEquals(listOf(1, 2), recentStopsWindow(stops(2)).map { it.number })
    }

    @Test
    fun `empty session yields an empty window`() {
        assertEquals(emptyList<NumberedStop>(), recentStopsWindow(emptyList()))
    }
}
