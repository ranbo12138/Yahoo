package com.yahoo.translator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class TextBlock(val text: String, val bounds: Rect)

object OcrHelper {
    enum class Language { JAPANESE, KOREAN }
    
    suspend fun recognize(bmp: Bitmap, lang: Language, preprocess: Boolean = true): String {
        val processed = if (preprocess) ImageProcessor.preprocess(bmp) else bmp
        val image = InputImage.fromBitmap(processed, 0)
        val recognizer = when (lang) {
            Language.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            Language.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
        val result = recognizer.process(image).await()
        return result.text
    }
    
    suspend fun recognizeWithBounds(bmp: Bitmap, lang: Language): List<TextBlock> {
        val image = InputImage.fromBitmap(bmp, 0)
        val recognizer = when (lang) {
            Language.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            Language.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
        val result = recognizer.process(image).await()
        return result.textBlocks.mapNotNull { block ->
            block.boundingBox?.let { TextBlock(block.text, it) }
        }
    }
}
