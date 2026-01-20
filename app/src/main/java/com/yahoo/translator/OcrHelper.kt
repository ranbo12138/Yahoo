package com.yahoo.translator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrHelper {
    
    enum class Language {
        JAPANESE, KOREAN
    }
    
    suspend fun recognizeText(bitmap: Bitmap, language: Language): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val recognizer = when (language) {
                Language.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                Language.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
            
            val result = recognizer.process(image).await()
            val text = result.text
            
            Logger.log("OCR识别成功 (${language.name}): ${text.take(50)}...")
            text
            
        } catch (e: Exception) {
            Logger.log("OCR识别失败: ${e.message}")
            throw e
        }
    }
}
