package com.yahoo.translator

import android.graphics.*

object ImageProcessor {
    fun preprocess(bmp: Bitmap): Bitmap {
        return try {
            var r = toGray(bmp)
            r = contrast(r, 1.5f)
            r
        } catch (_: Exception) { bmp }
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
    
    fun crop(bmp: Bitmap, top: Float = 0.08f, bot: Float = 0.1f): Bitmap {
        val t = (bmp.height * top).toInt()
        val h = bmp.height - t - (bmp.height * bot).toInt()
        return if (h > 0) Bitmap.createBitmap(bmp, 0, t, bmp.width, h) else bmp
    }
}
