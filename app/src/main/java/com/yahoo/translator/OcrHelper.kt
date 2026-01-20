package com.yahoo.translator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrHelper {
    
    enum class Language { JAPANESE, KOREAN }
    
    suspend fun recognizeText(
        bitmap: Bitmap,
        language: Language,
        preprocess: Boolean = true
    ): String {
        return try {
            val processed = if (preprocess) {
                ImageProcessor.preprocess(bitmap)
            } else bitmap
            
            val image = InputImage.fromBitmap(processed, 0)
            
            val recognizer = when (language) {
                Language.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                Language.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
            
            val result = recognizer.process(image).await()
            Logger.log("OCR成功 (${language.name}): ${result.text.take(50)}...")
            result.text
        } catch (e: Exception) {
            Logger.log("OCR失败: ${e.message}")
            throw e
        }
    }
}
