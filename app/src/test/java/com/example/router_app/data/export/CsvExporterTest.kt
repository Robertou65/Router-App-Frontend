package com.example.router_app.data.export

import com.example.router_app.data.local.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    @Test
    fun testExportToCsvMatchesSpec() {
        val stops = listOf(
            Stop(
                routeId = 0L,
                label = "Package #1",
                rawOcrText = "",
                address = "Carrera 18B # 32 - 06 Sur, Bogotá, Colombia",
                lat = 4.577832,
                lng = -74.114020,
                order = 1
            ),
            Stop(
                routeId = 0L,
                label = "Package #2",
                rawOcrText = "",
                address = "Calle 10 # 43E - 31, Medellín, Colombia",
                lat = 6.2087,
                lng = -75.574,
                order = 2
            )
        )

        val expected = "\uFEFFName;Address;Latitude;Longitude;Phone;Group;Notes\n" +
                "Package #1;Carrera 18B # 32 - 06 Sur, Bogotá, Colombia;4.577832;-74.114020;;;\n" +
                "Package #2;Calle 10 # 43E - 31, Medellín, Colombia;6.208700;-75.574000;;;"

        val actual = CsvExporter.exportToCsv(stops)
        assertEquals(expected, actual)
    }
}
