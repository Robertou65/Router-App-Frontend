package com.example.router_app.data.ocr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
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

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!shouldAnalyze()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                onResult(result.text)
            }
            .addOnFailureListener { error ->
                onFailure(error)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
