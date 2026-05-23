package com.example.router_app.data.parser

import java.util.logging.Logger

class AddressParser {
    private val logger = Logger.getLogger(AddressParser::class.java.name)

    enum class Confidence {
        HIGH,
        MEDIUM,
        LOW,
    }

    data class ParseResult(
        val address: String,
        val confidence: Confidence,
    )

    fun parse(rawText: String): ParseResult {
        // Step 1 — Extract using Destino keyword
        val index = rawText.indexOf("Destino:", ignoreCase = true)
        val hasDestino = index != -1
        val blockStart = if (hasDestino) index + "Destino:".length else 0
        val subText = rawText.substring(blockStart)

        val stopKeywords = listOf(
            "Referencia:", "Origen:", "Notas:", "EAD:", "FACTURA", "PESO:",
            "VALOR:", "ENVÍO:", "ENVIO:", "CONTENIDO:", "PO:", "TN:", "ST:", "Intento"
        )

        var stopIdx = subText.length
        for (kw in stopKeywords) {
            val idx = subText.indexOf(kw, ignoreCase = true)
            if (idx != -1 && idx < stopIdx) {
                stopIdx = idx
            }
        }
        val extractedBlock = subText.substring(0, stopIdx)

        // Step 2 — Clean fields
        var cleaned = extractedBlock
        // Phone number: pattern Tel:?\s*\d+ or / Tel: followed by digits
        cleaned = cleaned.replace(Regex("(?i)(?:/\\s*)?\\b(?:Tel|Tél|Telefono|Phone):?\\s*\\d+\\b"), "")
        // Postal code: pattern C.P. \d+ or CP \d+
        cleaned = cleaned.replace(Regex("(?i)\\bC\\.?P\\.?\\s*\\d+\\b"), "")
        // Standalone 5-6 digit numbers
        cleaned = cleaned.replace(Regex("\\b\\d{5,6}\\b"), "")
        // The word Colombia
        cleaned = cleaned.replace(Regex("(?i)\\bColombia\\b"), "")

        cleaned = cleanCommasAndWhitespace(cleaned)

        // Step 6 — Fix critical OCR character errors (apply 1 -> l correction on words first)
        cleaned = correctOneToL(cleaned)

        // Find street type
        val streetTypeRegex = Regex(
            "\\b(Avenida\\s+Carrera|Avenida\\s+Calle|Av\\.\\s*Cr\\.?|Av\\s+Cr\\.?|Av\\.\\s*Cl\\.?|Av\\s+Cl\\.?|Transversal|Diagonal|Circular|Carrera|Avenida|Trans|Diag|Cra\\.|Cr\\.|Cl\\.|Cll|Ave|Dg\\.|Tv\\.|Tr\\.|Cir|Calle|Cra|AK|AC|KR|Kr|Cr|Cl|CL|Ca|Av|Dg|Tv|Tr|KF|CF|K)\\b",
            RegexOption.IGNORE_CASE
        )

        val match = streetTypeRegex.find(cleaned)
        if (match == null) {
            // Low confidence fallback (ensure Colombia is appended)
            var finalLowAddress = cleaned
            if (finalLowAddress.isNotEmpty()) {
                finalLowAddress = "$finalLowAddress, Colombia"
            } else {
                finalLowAddress = "Colombia"
            }
            finalLowAddress = cleanCommasAndWhitespace(finalLowAddress)
            logger.warning("Low confidence address parse: $finalLowAddress")
            return ParseResult(
                address = finalLowAddress,
                confidence = Confidence.LOW
            )
        }

        val matchedStreet = match.value
        val endStreetIdx = match.range.last + 1
        var suffix = cleaned.substring(endStreetIdx)

        // OCR Character Errors in street type
        var correctedStreet = matchedStreet.replace("0", "O")
        if (correctedStreet.equals("KF", ignoreCase = true)) {
            correctedStreet = "KR"
        } else if (correctedStreet.equals("CF", ignoreCase = true)) {
            correctedStreet = "CR"
        }

        // Step 1 — Normalize street type
        val canonicalStreetType = getCanonicalStreetType(correctedStreet)

        // Step 2 — Normalize "No." separator in suffix
        suffix = suffix.replace(Regex("(\\d+)\\s+No\\.?\\s+(\\d+)"), "$1 # $2")

        // Parse numbers in suffix
        val numTokenRegex = Regex("(\\d+)(?:\\s*([A-Za-z1]))?\\b")
        val numMatches = numTokenRegex.findAll(suffix).toList()

        var num1 = ""
        var let1 = ""
        var num2 = ""
        var let2 = ""
        var num3 = ""
        var lastNumEnd = 0

        if (numMatches.isNotEmpty()) {
            val m1 = numMatches[0]
            num1 = m1.groupValues[1]
            let1 = m1.groupValues[2]
            lastNumEnd = m1.range.last + 1

            if (numMatches.size > 1) {
                val m2 = numMatches[1]
                num2 = m2.groupValues[1]
                let2 = m2.groupValues[2]
                lastNumEnd = m2.range.last + 1

                if (numMatches.size > 2) {
                    val m3 = numMatches[2]
                    num3 = m3.groupValues[1]
                    lastNumEnd = m3.range.last + 1
                }
            }
        }

        // Step 5 — Normalize quadrant suffix
        val quadRegex = Regex(
            "\\b(SUR|Sur|sur|S|NORTE|Norte|norte|N|ESTE|Este|este|E|OESTE|Oeste|oeste|O|OCCIDENTE|Occidente|OCC|Occ|ORIENTE|Oriente|OR|Or)\\b"
        )
        val quadMatch = quadRegex.find(suffix, lastNumEnd)
        val hasQuad = if (quadMatch != null) {
            val intermediate = suffix.substring(lastNumEnd, quadMatch.range.first)
            intermediate.none { it.isLetterOrDigit() }
        } else {
            false
        }
        val quadrantRaw = if (hasQuad) quadMatch!!.value else ""
        val canonicalQuadrant = if (hasQuad) getCanonicalQuadrant(quadrantRaw) else ""
        val quadEndIndex = if (hasQuad && quadMatch != null) quadMatch.range.last + 1 else lastNumEnd

        // Extract remaining text
        val remaining = suffix.substring(quadEndIndex)

        // Step 7 — Parse Neighborhood, City, Country
        val cities = listOf(
            "Bogota", "Bogotá", "Medellin", "Medellín", "Cali", "Barranquilla", "Cartagena",
            "Bucaramanga", "Pereira", "Manizales", "Cucuta", "Cúcuta", "Ibague", "Ibagué",
            "Santa Marta", "Villavicencio"
        )
        val canonicalCities = mapOf(
            "bogota" to "Bogotá",
            "bogotá" to "Bogotá",
            "medellin" to "Medellín",
            "medellín" to "Medellín",
            "cali" to "Cali",
            "barranquilla" to "Barranquilla",
            "cartagena" to "Cartagena",
            "bucaramanga" to "Bucaramanga",
            "pereira" to "Pereira",
            "manizales" to "Manizales",
            "cucuta" to "Cúcuta",
            "cúcuta" to "Cúcuta",
            "ibague" to "Ibagué",
            "ibagué" to "Ibagué",
            "santa marta" to "Santa Marta",
            "villavicencio" to "Villavicencio"
        )

        var neighborhood = ""
        var detectedCity = ""

        val parts = remaining.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        for (part in parts) {
            var foundCityKey: String? = null
            for (city in cities) {
                val cityRegex = Regex("\\b${city}\\b", RegexOption.IGNORE_CASE)
                if (cityRegex.containsMatchIn(part)) {
                    foundCityKey = city.lowercase()
                    detectedCity = canonicalCities[foundCityKey]!!
                    if (neighborhood.isEmpty()) {
                        neighborhood = part.replace(cityRegex, "").trim()
                    }
                    break
                }
            }
            if (foundCityKey == null && neighborhood.isEmpty()) {
                neighborhood = part
            }
        }
        neighborhood = cleanCommasAndWhitespace(neighborhood)

        // Step 4 — Normalize letter suffixes
        val normLet1 = normalizeLetter(let1)
        val normLet2 = normalizeLetter(let2)

        // Step 3 — Normalize # block spacing
        val part1 = if (normLet1.isNotEmpty()) "$num1$normLet1" else num1
        val part2 = if (normLet2.isNotEmpty()) "$num2$normLet2" else num2
        val part3 = num3

        val numberPart = when {
            part1.isNotEmpty() && part2.isNotEmpty() && part3.isNotEmpty() -> {
                "$part1 # $part2 - $part3"
            }
            part1.isNotEmpty() && part2.isNotEmpty() -> {
                "$part1 # $part2"
            }
            part1.isNotEmpty() -> {
                part1
            }
            else -> ""
        }

        // Rebuild final address string
        val addressParts = mutableListOf<String>()
        val streetAndNumber = buildString {
            append(canonicalStreetType)
            if (numberPart.isNotEmpty()) {
                append(" ")
                append(numberPart)
            }
            if (canonicalQuadrant.isNotEmpty()) {
                append(" ")
                append(canonicalQuadrant)
            }
        }
        addressParts.add(streetAndNumber)
        if (neighborhood.isNotEmpty()) {
            addressParts.add(neighborhood)
        }
        if (detectedCity.isNotEmpty()) {
            addressParts.add(detectedCity)
        }
        addressParts.add("Colombia")

        val finalAddress = cleanCommasAndWhitespace(addressParts.joinToString(", "))

        // Confidence scoring
        val hasValidHashStructure = num1.isNotEmpty() && num2.isNotEmpty() && num3.isNotEmpty()
        val confidence = if (hasDestino) {
            if (hasValidHashStructure && detectedCity.isNotEmpty()) {
                Confidence.HIGH
            } else {
                Confidence.MEDIUM
            }
        } else {
            logger.warning("Low confidence address parse: $finalAddress")
            Confidence.LOW
        }

        return ParseResult(
            address = finalAddress,
            confidence = confidence
        )
    }

    private fun cleanCommasAndWhitespace(str: String): String {
        var res = str
        res = res.replace(Regex("[\\s\\n\\r]+"), " ")
        res = res.replace(Regex("\\s*,\\s*"), ", ")
        while (res.contains(", ,")) {
            res = res.replace(", ,", ",")
        }
        res = res.replace(Regex(",\\s*,"), ", ")
        res = res.trim().trim(',', ' ').trim()
        return res
    }

    private fun correctOneToL(input: String): String {
        val wordRegex = Regex("[A-Za-z1]+")
        return input.replace(wordRegex) { matchResult ->
            val word = matchResult.value
            if (word.any { it.isLetter() }) {
                word.replace('1', 'l')
            } else {
                word
            }
        }
    }

    private fun normalizeLetter(let: String): String {
        if (let.isEmpty()) return ""
        val corrected = if (let == "1") "l" else let
        return corrected.uppercase()
    }

    private fun getCanonicalStreetType(matched: String): String {
        val clean = matched.lowercase().replace(Regex("\\s+"), " ").removeSuffix(".")
        return when {
            clean.startsWith("avenida carrera") || clean.startsWith("av cr") || clean == "ak" || clean == "ac" -> "Avenida Carrera"
            clean.startsWith("avenida calle") || clean.startsWith("av cl") -> "Avenida Calle"
            clean.startsWith("carrera") || clean.startsWith("cra") || clean.startsWith("cr") || clean == "kr" || clean == "kr" || clean == "k" -> "Carrera"
            clean.startsWith("calle") || clean.startsWith("cl") || clean == "cll" || clean == "ca" -> "Calle"
            clean.startsWith("avenida") || clean.startsWith("av") || clean == "ave" -> "Avenida"
            clean.startsWith("diagonal") || clean.startsWith("dg") || clean.startsWith("diag") -> "Diagonal"
            clean.startsWith("transversal") || clean.startsWith("tv") || clean.startsWith("tr") || clean.startsWith("trans") -> "Transversal"
            clean.startsWith("circular") || clean.startsWith("cir") -> "Circular"
            else -> matched
        }
    }

    private fun getCanonicalQuadrant(quad: String): String {
        return when (quad.uppercase()) {
            "SUR", "S" -> "Sur"
            "NORTE", "N" -> "Norte"
            "ESTE", "E" -> "Este"
            "OESTE", "O" -> "Oeste"
            "OCCIDENTE", "OCC" -> "Occidente"
            "ORIENTE", "OR" -> "Oriente"
            else -> quad.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
    }
}
