package com.superdriver.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device OCR used only as a fallback, when the accessibility tree of Uber
 * Driver cannot be read (some screens are drawn on a canvas).
 *
 * The model is bundled in the APK: no Play Services, no internet, no server.
 * It recognizes the Latin script, so it is reliable for digits and for English
 * service names, but not for Arabic words — which is why it is never the first
 * choice.
 */
class ScreenOcr {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    /** Returns the recognized lines, top to bottom, left to right. */
    fun read(bitmap: Bitmap, onResult: (List<String>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = ArrayList<String>()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text
                        if (text.isNotBlank()) lines.add(text)
                    }
                }
                onResult(lines)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun close() {
        recognizer.close()
    }
}
