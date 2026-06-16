package com.example.router_app.data.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColombianAddressFormatterTest {

    // Representative of what ML Kit reads off a real XCargo label (line breaks included,
    // address wraps across two lines just like on the printed sticker).
    private val labelOcr = """
        Admitido por: BEYOND BORDER
        INTERNATIONAL COURIER S.A.S
        Operado por: XCARGO SAS
        ROBERT HERNAN HERNANDEZ ALVIS :
        Destino: KR 18b # 32 - 06 SUR Quiroga Central, Rafael
        Uribe Uribe, Bogota DC, C.P. 111811, Colombia / Tel:
        3005286287
        Referencia: Casa esquinera gris con local de senthia y un asadero en
        Origin: 16192 Coastal Highway. Lewes. Delaware. 11958 USA
        CONTENIDO: power supply.plastic mattress,
        ENVÍO: Car
        PESO: 162 g
        TN: 26137882442363220
        PO: F755542602270SYPxeA
        Bogota DC-Rafael Uribe Uribe
        ST: 19 - 1
        RAFAEL URIBE ZONA 3
    """.trimIndent()

    @Test
    fun `parses the real label into a clean Colombian address`() {
        val result = ColombianAddressFormatter.format(labelOcr, fallbackCity = "Medellín")
        assertTrue(result is ColombianAddressFormatter.Result.Success)
        val address = (result as ColombianAddressFormatter.Result.Success).address

        assertEquals("Cra. 18B # 32-06 Sur", address.streetAddress)
        assertEquals("Quiroga Central", address.neighborhood)
        assertEquals("Bogotá", address.city)
        assertEquals("Robert Hernan Hernandez Alvis", address.recipientName)
        assertEquals(
            "Cra. 18B # 32-06 Sur, Quiroga Central, Bogotá, Colombia",
            address.geocodeQuery,
        )
    }

    @Test
    fun `formats sloppy manual entry without # or - separators`() {
        // Deliver types numbers separated by plain spaces; city comes from the route.
        val a = ColombianAddressFormatter.format("cl 54 26 54", fallbackCity = "Bogotá")
        assertEquals(
            "Cl. 54 # 26-54, Bogotá, Colombia",
            (a as ColombianAddressFormatter.Result.Success).address.geocodeQuery,
        )

        val b = ColombianAddressFormatter.format("dg 29b 34 81", fallbackCity = "Bogotá")
        assertEquals(
            "Dg. 29B # 34-81, Bogotá, Colombia",
            (b as ColombianAddressFormatter.Result.Success).address.geocodeQuery,
        )

        val c = ColombianAddressFormatter.format("cl 26 51 20", fallbackCity = "Bogotá")
        assertEquals(
            "Cl. 26 # 51-20",
            (c as ColombianAddressFormatter.Result.Success).address.streetAddress,
        )
    }

    @Test
    fun `ignores the USA origin warehouse address`() {
        val result = ColombianAddressFormatter.format(labelOcr, fallbackCity = "Medellín")
        val address = (result as ColombianAddressFormatter.Result.Success).address
        // The Delaware "Origin" must never leak into the geocode query.
        assertTrue("Delaware" !in address.geocodeQuery)
        assertTrue("USA" !in address.geocodeQuery)
        assertTrue(address.city != "Lewes")
    }

    @Test
    fun `expands common street-type abbreviations`() {
        val cl = ColombianAddressFormatter.format("CL 10 # 43E - 31", "Cali")
        assertEquals(
            "Cl. 10 # 43E-31",
            (cl as ColombianAddressFormatter.Result.Success).address.streetAddress,
        )

        val ak = ColombianAddressFormatter.format("AK 68 # 24-50", "Bogotá")
        assertEquals(
            "Ak. 68 # 24-50",
            (ak as ColombianAddressFormatter.Result.Success).address.streetAddress,
        )
    }

    @Test
    fun `falls back to configured city for bare manual entry`() {
        val result = ColombianAddressFormatter.format("Cra 7 # 45-10", fallbackCity = "Medellín")
        val address = (result as ColombianAddressFormatter.Result.Success).address
        assertEquals("Cra. 7 # 45-10", address.streetAddress)
        assertEquals("Medellín", address.city)
        assertEquals(null, address.neighborhood)
    }

    @Test
    fun `returns NoAddressFound when there is no nomenclature`() {
        val result = ColombianAddressFormatter.format("PESO: 162 g\nENVÍO: Car", "Bogotá")
        assertEquals(ColombianAddressFormatter.Result.NoAddressFound, result)
    }
}
