package com.example.router_app.data.format

/**
 * Turns the raw, noisy text that ML Kit reads off an XCargo label into a clean
 * Colombian address that the Google Geocoding API can actually resolve.
 *
 * A label looks roughly like this (order of lines varies with OCR):
 *
 *     Operado por: XCARGO SAS
 *     ROBERT HERNAN HERNANDEZ ALVIS :
 *     Destino: KR 18b # 32 - 06 SUR Quiroga Central, Rafael
 *     Uribe Uribe, Bogota DC, C.P. 111811, Colombia / Tel:
 *     3005286287
 *     Origin: 16192 Coastal Highway. Lewes. Delaware. 11958 USA
 *
 * The only part we care about is the destination nomenclature
 * ("KR 18b # 32 - 06 SUR"). Everything else — courier headers, the Delaware
 * warehouse "Origin", barcodes, weight, checkboxes — is discarded.
 */
object ColombianAddressFormatter {

    sealed class Result {
        data class Success(val address: ParsedAddress) : Result()
        data object NoAddressFound : Result()
    }

    data class ParsedAddress(
        val recipientName: String?,
        val streetAddress: String,
        val neighborhood: String?,
        val city: String,
    ) {
        /** Human readable, e.g. "Carrera 18B # 32-06 Sur, Quiroga Central, Bogotá". */
        val displayAddress: String
            get() = listOfNotNull(
                streetAddress,
                neighborhood?.takeIf { it.isNotBlank() },
                city,
            ).joinToString(", ")

        /** What we hand to Google. Country is always Colombia for this app. */
        val geocodeQuery: String
            get() = "$displayAddress, Colombia"
    }

    /** Colombian street-type abbreviations mapped to their canonical written form. */

    private val viaTypes: Map<List<String>, String> = mapOf(
        listOf("CALLE", "CALL", "CLL", "CLLE", "CL") to "Cl.",
        listOf("CARRERA", "CARR", "CRA", "KRA", "CR", "KR", "CARRE") to "Cra.",
        listOf("AVENIDA", "AVE", "AV") to "Av.",
        listOf("AVENIDA CARRERA","AK", "AV. CARRERA") to "Ak.",
        listOf("AC", "AV. CALLE", "AVENIDA CALLE") to "Ac.",
        listOf("DIAGONAL", "DIAG", "DG") to "Dg.",
        listOf("TRANSVERSAL", "TRANSV", "TRAV", "TV") to "Tv."
    )

    private val quadrants: Map<String, String> = mapOf(
        "SUR" to "Sur",
        "NORTE" to "Norte",
        "ESTE" to "Este",
        "OESTE" to "Oeste", "OCCIDENTE" to "Oeste",
    )

    /** City spellings that need accents/cleanup; everything else is title-cased generically. */
    private val knownCities: Map<String, String> = mapOf(
        "BOGOTA" to "Bogotá", "BOGOTÁ" to "Bogotá",
        "MEDELLIN" to "Medellín", "MEDELLÍN" to "Medellín",
        "CALI" to "Cali", "BARRANQUILLA" to "Barranquilla", "CARTAGENA" to "Cartagena",
        "CUCUTA" to "Cúcuta", "CÚCUTA" to "Cúcuta", "BUCARAMANGA" to "Bucaramanga",
        "PEREIRA" to "Pereira", "MANIZALES" to "Manizales", "IBAGUE" to "Ibagué",
        "SANTA MARTA" to "Santa Marta", "VILLAVICENCIO" to "Villavicencio", "PASTO" to "Pasto",
        "SOACHA" to "Soacha", "BELLO" to "Bello", "ITAGUI" to "Itagüí", "ENVIGADO" to "Envigado",
    )

    /** Courier / sender words that must never end up in the recipient name. */
    private val nameNoise: Set<String> = setOf(
        "BEYOND", "BORDER", "INTERNATIONAL", "COURIER", "XCARGO", "SAS", "S.A.S", "S.A.S.",
        "OPERADO", "ADMITIDO", "POR", "REMITENTE", "DESTINATARIO", "ENVIA", "ENVIO",
    )

    /** Flat lookup: every spelling variant -> canonical written form ("KR" -> "Cra."). */
    private val viaTypeLookup: Map<String, String> =
        viaTypes.flatMap { (aliases, canonical) -> aliases.map { it to canonical } }.toMap()

    // Longest first so multi-word types ("AVENIDA CARRERA") win over their prefixes ("AV").
    private val typeAlternation = viaTypeLookup.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    /**
     * Matches Colombian nomenclature: <type> <primary>[letter][ BIS] # <secondary>[letter] - <tertiary> [quadrant].
     * The "#" (No./Nº/Nro.) and the "-" are both optional, so a deliver can type a
     * sloppy manual address ("cl 26 51 20") and still get "Cl. 26 # 51-20".
     */
    private val addressRegex = Regex(
        "\\b($typeAlternation)\\.?\\s+" +
            "(\\d{1,3}[A-Za-z]?)(\\s*BIS)?\\s*" +
            "(?:#|N[o°º]\\.?|Nro\\.?)?\\s*" +
            "(\\d{1,3}[A-Za-z]?)\\s*-?\\s*(\\d{1,3})" +
            "\\s*(SUR|NORTE|ESTE|OESTE|OCCIDENTE)?",
        RegexOption.IGNORE_CASE,
    )

    private val phoneRegex = Regex("(?:\\+?57[\\s-]?)?(3\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{4})")

    /** Sections that mark the end of the destination address on the label. */
    private val tailStopMarkers = listOf(
        "/ Tel", "Tel:", "Tel.", "Cel", "Referencia", "Origin", "Origen",
        "TN:", "PO:", "FACTURA", "CONTENIDO", "ENVÍO", "ENVIO", "PESO",
    )

    fun format(rawOcrText: String, fallbackCity: String): Result {
        val text = rawOcrText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return Result.NoAddressFound

        val match = addressRegex.find(text) ?: return Result.NoAddressFound
        val (typeRaw, primary, bis, secondary, tertiary, quadrantRaw) = match.destructured

        val street = buildString {
            append(viaTypeLookup[typeRaw.uppercase()] ?: typeRaw.replaceFirstChar { it.uppercase() })
            append(' ').append(primary.uppercase())
            if (bis.isNotBlank()) append(" Bis")
            append(" # ").append(secondary.uppercase()).append('-').append(tertiary)
            quadrants[quadrantRaw.uppercase()]?.let { append(' ').append(it) }
        }

        val (neighborhood, city) = extractNeighborhoodAndCity(
            text.substring(match.range.last + 1),
            fallbackCity,
        )

        return Result.Success(
            ParsedAddress(
                recipientName = extractRecipientName(text),
                streetAddress = street,
                neighborhood = neighborhood,
                city = city,
            ),
        )
    }

    /**
     * The text after the street nomenclature looks like
     * " Quiroga Central, Rafael Uribe Uribe, Bogota DC, C.P. 111811, Colombia / Tel: ...".
     * The first comma segment is the neighborhood; the last real segment is the city.
     */
    private fun extractNeighborhoodAndCity(afterStreet: String, fallbackCity: String): Pair<String?, String> {
        var tail = afterStreet
        for (marker in tailStopMarkers) {
            val idx = tail.indexOf(marker, ignoreCase = true)
            if (idx >= 0) tail = tail.substring(0, idx)
        }

        val segments = tail.split(',')
            .map { it.trim() }
            .filterNot { seg ->
                val u = seg.uppercase()
                seg.isBlank() || u.startsWith("C.P") || u.startsWith("CP ") ||
                    u.contains("POSTAL") || u == "COLOMBIA" || Regex("^\\d{4,6}$").matches(seg)
            }

        val city = segments.lastOrNull()?.let { normalizeCity(it) } ?: normalizeCity(fallbackCity)
        val neighborhood = if (segments.size >= 2) cleanSegment(segments.first()) else null
        return neighborhood to city
    }

    private fun normalizeCity(raw: String): String {
        val cleaned = raw
            .replace(Regex("(?i)\\bD\\.?\\s*C\\.?\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '.', ' ')
        knownCities[cleaned.uppercase()]?.let { return it }
        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun cleanSegment(raw: String): String? =
        raw.split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
            .trim()
            .takeIf { it.isNotBlank() }

    private fun extractRecipientName(text: String): String? {
        val beforeDestino = text.substringBefore("Destino", "")
        if (beforeDestino.isBlank()) return null

        val words = Regex("[A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ.]+")
            .findAll(beforeDestino)
            .map { it.value.trim('.', ':', ' ') }
            .filter { it.length > 1 && it.uppercase() !in nameNoise }
            .toList()
            .takeLast(4)

        if (words.size < 2) return null
        return words.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
