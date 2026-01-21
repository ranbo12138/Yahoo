package com.yahoo.translator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageProcessor {
    
    fun preprocess(bitmap: Bitmap, enable: Boolean = true): Bitmap {
        if (!enable) return bitmap
        return try {
            var result = toGrayscale(bitmap)
            result = adjustContrast(result, 2.0f)
            result = binarize(result, 180)
            Logger.log("预处理完成: ${result.width}x${result.height}")
            result
        } catch (e: Exception) {
            Logger.log("预处理失败: ${e.message}")
            bitmap
        }
    }
    
    private fun toGrayscale(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }
    
    private fun adjustContrast(src: Bitmap, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val t = (1f - contrast) / 2f * 255f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }
    
    private fun binarize(src: Bitmap, threshold: Int): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until src.width) {
            for (y in 0 until src.height) {
                val pixel = src.getPixel(x, y)
                val gray = Color.red(pixel)
                result.setPixel(x, y, if (gray > threshold) Color.WHITE else Color.BLACK)
            }
        }
        return result
    }
    
    fun cropCenter(bitmap: Bitmap, topCrop: Float = 0.1f, bottomCrop: Float = 0.1f): Bitmap {
        val top = (bitmap.height * topCrop).toInt()
        val bottom = (bitmap.height * bottomCrop).toInt()
        val newHeight = bitmap.height - top - bottom
        return if (newHeight > 0) Bitmap.createBitmap(bitmap, 0, top, bitmap.width, newHeight) else bitmap
    }
}
