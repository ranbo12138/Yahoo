package com.yahoo.translator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageProcessor {
    
    fun preprocess(bitmap: Bitmap, enablePreprocess: Boolean = true): Bitmap {
        if (!enablePreprocess) return bitmap
        
        return try {
            var result = bitmap
            result = toGrayscale(result)
            result = adjustContrast(result, 1.5f)
            result = sharpen(result)
            Logger.log("图片预处理完成: ${result.width}x${result.height}")
            result
        } catch (e: Exception) {
            Logger.log("图片预处理失败: ${e.message}")
            bitmap
        }
    }
    
    private fun toGrayscale(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }
    
    private fun adjustContrast(src: Bitmap, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val t = (1f - contrast) / 2f * 255f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }
    
    private fun sharpen(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, -50f,
            0f, 1.5f, 0f, 0f, -50f,
            0f, 0f, 1.5f, 0f, -50f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }
    
    fun cropCenter(bitmap: Bitmap, topCrop: Float = 0.1f, bottomCrop: Float = 0.1f): Bitmap {
        val top = (bitmap.height * topCrop).toInt()
        val bottom = (bitmap.height * bottomCrop).toInt()
        val newHeight = bitmap.height - top - bottom
        
        return if (newHeight > 0) {
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, newHeight)
        } else {
            bitmap
        }
    }
    
    fun calculateSimilarity(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) {
            return 0f
        }
        
        val sampleSize = 100
        val stepX = bitmap1.width / sampleSize
        val stepY = bitmap1.height / sampleSize
        
        var matchCount = 0
        var totalCount = 0
        
        for (x in 0 until bitmap1.width step maxOf(stepX, 1)) {
            for (y in 0 until bitmap1.height step maxOf(stepY, 1)) {
                val pixel1 = bitmap1.getPixel(x, y)
                val pixel2 = bitmap2.getPixel(x, y)
                if (pixel1 == pixel2) matchCount++
                totalCount++
            }
        }
        
        return if (totalCount > 0) matchCount.toFloat() / totalCount else 0f
    }
}
