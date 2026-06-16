package com.example.router_app.data.ocr

/**
 * The region of the camera frame the OCR is allowed to "see", expressed as
 * fractions (0..1) of the frame. The same fractions drive the on-screen
 * viewfinder rectangle so what the user frames is what gets read — like the
 * focus box in Google Lens. A centered horizontal band fits a label's
 * destination line while excluding the sender/origin block above it.
 */
object ScanRegion {
    const val LEFT = 0.06f
    const val TOP = 0.30f
    const val RIGHT = 0.94f
    const val BOTTOM = 0.44f
}
