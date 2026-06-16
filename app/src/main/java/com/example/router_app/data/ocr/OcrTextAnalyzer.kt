package com.example.router_app.data.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrTextAnalyzer(
    private val shouldAnalyze: () -> Boolean,
    private val onResult: (String) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun analyze(imageProxy: ImageProxy) {
        if (!shouldAnalyze()) {
            imageProxy.close()
            return
        }

        val cropped = runCatching {
            val upright = imageProxy.toUprightBitmap()
            upright.cropTo(ScanRegion.LEFT, ScanRegion.TOP, ScanRegion.RIGHT, ScanRegion.BOTTOM)
        }.getOrElse { error ->
            imageProxy.close()
            onFailure(error)
            return
        }

        // The bitmap is already rotated upright, so OCR uses 0° rotation.
        val inputImage = InputImage.fromBitmap(cropped, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { result -> onResult(result.text) }
            .addOnFailureListener { error -> onFailure(error) }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Converts the frame to a Bitmap rotated to the device's upright orientation. */
    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val raw = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    /** Crops to the fractional rectangle, clamped so we never read outside the bitmap. */
    private fun Bitmap.cropTo(left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val x = (width * left).toInt().coerceIn(0, width - 1)
        val y = (height * top).toInt().coerceIn(0, height - 1)
        val w = (width * (right - left)).toInt().coerceIn(1, width - x)
        val h = (height * (bottom - top)).toInt().coerceIn(1, height - y)
        return Bitmap.createBitmap(this, x, y, w, h)
    }
}
