package com.example.router_app.data.export

import com.example.router_app.data.local.Stop
import java.util.Locale

object CsvExporter {
    fun exportToCsv(stops: List<Stop>): String {
        val header = "\uFEFFName;Address;Latitude;Longitude;Phone;Group;Notes"
        val rows = stops.map { stop ->
            val name = escape(stop.label)
            val address = escape(stop.address)
            val latitude = String.format(Locale.US, "%.6f", stop.lat)
            val longitude = String.format(Locale.US, "%.6f", stop.lng)
            "$name;$address;$latitude;$longitude;;;"
        }
        val lines = listOf(header) + rows
        return lines.joinToString("\n")
    }

    fun buildCsv(stops: List<Stop>): String {
        return exportToCsv(stops)
    }

    private fun escape(value: String): String {
        return if (value.contains(";")) {
            val escaped = value.replace("\"", "\"\"")
            "\"$escaped\""
        } else {
            value
        }
    }
}
