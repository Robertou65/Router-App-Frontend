package com.example.router_app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressParserTest {
    @Test
    fun testStandardLabelFormat() {
        val parser = AddressParser()
        val input = "Destino: KR 18b # 32 - 06 SUR Quiroga Central, Rafael Uribe Uribe, Bogota DC, C.P. 111811, Colombia / Tel: 3005286287 Referencia: Casa esquinera"
        val expected = "Carrera 18B # 32 - 06 Sur, Quiroga Central, Bogotá, Colombia"
        val result = parser.parse(input)
        assertEquals(expected, result.address)
        assertEquals(AddressParser.Confidence.HIGH, result.confidence)
    }

    @Test
    fun testFullWordNoHash() {
        val parser = AddressParser()
        val input = "Destino: Carrera 18b 32 06 Sur Chapinero, Bogota EAD: 2026-04-14"
        val expected = "Carrera 18B # 32 - 06 Sur, Chapinero, Bogotá, Colombia"
        val result = parser.parse(input)
        assertEquals(expected, result.address)
    }

    @Test
    fun testCalleAbbreviatedNoSeparator() {
        val parser = AddressParser()
        val input = "Destino: Cl 10 No. 43E - 31 El Poblado, Medellín Referencia: Edificio azul"
        val expected = "Calle 10 # 43E - 31, El Poblado, Medellín, Colombia"
        val result = parser.parse(input)
        assertEquals(expected, result.address)
    }

    @Test
    fun testOcrDroppedAllSeparators() {
        val parser = AddressParser()
        val input = "Destino: Kr 18B 32 06 Sur Quiroga Bogota Referencia: ..."
        val expected = "Carrera 18B # 32 - 06 Sur, Quiroga, Bogotá, Colombia"
        val result = parser.parse(input)
        assertEquals(expected, result.address)
    }

    @Test
    fun testNoDestinoKeywordFallback() {
        val parser = AddressParser()
        val input = "Kr 18b # 32 - 06 SUR Quiroga Central Bogota"
        val expected = "Carrera 18B # 32 - 06 Sur, Quiroga Central, Bogotá, Colombia"
        val result = parser.parse(input)
        assertEquals(expected, result.address)
        assertEquals(AddressParser.Confidence.LOW, result.confidence)
    }

    @Test
    fun testAvenidaCarreraCompoundType() {
        val parser = AddressParser()
        val input = "Destino: Av Cr 30 # 45 - 10 Teusaquillo, Bogota EAD:"
        val expected = "Avenida Carrera 30 # 45 - 10, Teusaquillo, Bogotá, Colombia"
        val result = parser.parse(input)
        assertEquals(expected, result.address)
    }
}
