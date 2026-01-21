package com.yahoo.translator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageProcessor {
    fun preprocess(bmp: Bitmap, enable: Boolean = true): Bitmap {
        if (!enable) return bmp
        return try {
            var r = toGray(bmp)
            r = contrast(r, 2.0f)
            r = binarize(r, 180)
            Logger.log("预处理完成")
            r
        } catch (e: Exception) {
            Logger.log("预处理失败: ${e.message}")
            bmp
        }
    }
    
    private fun toGray(src: Bitmap): Bitmap {
        val r = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(r).drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        })
        return r
    }
    
    private fun contrast(src: Bitmap, c: Float): Bitmap {
        val r = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val t = (1f - c) / 2f * 255f
        Canvas(r).drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                c,0f,0f,0f,t, 0f,c,0f,0f,t, 0f,0f,c,0f,t, 0f,0f,0f,1f,0f
            )))
        })
        return r
    }
    
    private fun binarize(src: Bitmap, th: Int): Bitmap {
        val r = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until src.width) {
            for (y in 0 until src.height) {
                val g = Color.red(src.getPixel(x, y))
                r.setPixel(x, y, if (g > th) Color.WHITE else Color.BLACK)
            }
        }
        return r
    }
    
    fun cropCenter(bmp: Bitmap, top: Float = 0.1f, bot: Float = 0.1f): Bitmap {
        val t = (bmp.height * top).toInt()
        val b = (bmp.height * bot).toInt()
        val h = bmp.height - t - b
        return if (h > 0) Bitmap.createBitmap(bmp, 0, t, bmp.width, h) else bmp
    }
}
