package com.yahoo.translator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    
    companion object {
        const val CHANNEL_ID = "yahoo_capture"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        
        @Volatile var isRunning = false
        private var instance: ScreenCaptureService? = null
        
        fun captureScreen(): Bitmap? {
            Logger.log("captureScreen() called, instance=${instance != null}")
            return instance?.doCapture()
        }
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Logger.log("Service onCreate")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("Service onStartCommand: ${intent?.action}")
        
        if (intent?.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA)
            }
            
            Logger.log("resultCode=$resultCode, hasData=${data != null}")
            
            if (data != null && resultCode == Activity.RESULT_OK) {
                startForeground(NOTIFICATION_ID, createNotification())
                startProjection(resultCode, data)
            } else {
                Logger.log("无效的截屏数据")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "截屏服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yahoo! 运行中")
            .setContentText("返回APP点击按钮截取")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
    
    private fun startProjection(resultCode: Int, data: Intent) {
        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = pm.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Logger.log("MediaProjection 为 null")
                stopSelf()
                return
            }
            
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val d = metrics.densityDpi
            
            Logger.log("屏幕: ${w}x${h}, dpi=$d")
            
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "Yahoo", w, h, d,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, handler
            )
            
            isRunning = true
            Logger.log("截屏服务启动成功")
        } catch (e: Exception) {
            Logger.log("启动异常: ${e.message}")
            stopSelf()
        }
    }
    
    private fun doCapture(): Bitmap? {
        Logger.log("doCapture(), imageReader=${imageReader != null}")
        
        try {
            Thread.sleep(150)
            
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Logger.log("acquireLatestImage 返回 null")
                return null
            }
            
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            
            val bw = image.width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(bw, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            
            val result = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            Logger.log("截屏成功: ${result.width}x${result.height}")
            return result
        } catch (e: Exception) {
            Logger.log("截屏异常: ${e.message}")
            return null
        }
    }
    
    override fun onDestroy() {
        Logger.log("Service onDestroy")
        isRunning = false
        instance = null
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
