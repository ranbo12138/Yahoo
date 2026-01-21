package com.yahoo.translator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator

class ScanLineView(context: Context) : View(context) {
    private var lineY = 0f
    private var animator: ValueAnimator? = null
    private var onScanComplete: (() -> Unit)? = null
    
    private val linePaint = Paint().apply {
        color = Color.parseColor("#2196F3") // 蓝色
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    
    private val trailPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lineY <= 0) return
        
        // 绘制拖影（渐变效果）
        val trailHeight = 80f * resources.displayMetrics.density
        val trailTop = (lineY - trailHeight).coerceAtLeast(0f)
        val gradient = LinearGradient(
            0f, trailTop, 0f, lineY,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#402196F3")),
            null, Shader.TileMode.CLAMP
        )
        trailPaint.shader = gradient
        canvas.drawRect(0f, trailTop, width.toFloat(), lineY, trailPaint)
        
        // 绘制扫描线
        canvas.drawLine(0f, lineY, width.toFloat(), lineY, linePaint)
    }
    
    fun startScan(durationMs: Long = 2000, onComplete: () -> Unit) {
        onScanComplete = onComplete
        animator?.cancel()
        
        animator = ValueAnimator.ofFloat(0f, height.toFloat()).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                lineY = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onScanComplete?.invoke()
                }
            })
            start()
        }
    }
    
    fun stopScan() {
        animator?.cancel()
        lineY = 0f
        invalidate()
    }
}
