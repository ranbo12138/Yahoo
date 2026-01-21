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
        const val ACTION_STOP = "STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        
        @Volatile
        var isRunning = false
        
        private var instance: ScreenCaptureService? = null
        
        fun captureScreen(): Bitmap? {
            Logger.log("尝试截屏, instance=${instance != null}, isRunning=$isRunning")
            return instance?.doCapture()
        }
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        Logger.log("ScreenCaptureService onCreate")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("ScreenCaptureService onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                
                Logger.log("resultCode=$resultCode, data=${data != null}")
                
                if (data != null && resultCode == Activity.RESULT_OK) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startCapture(resultCode, data)
                } else {
                    Logger.log("截屏权限数据无效")
                    stopSelf()
                }
            }
            ACTION_STOP -> stopCapture()
            else -> Logger.log("未知 action: ${intent?.action}")
        }
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "截屏服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于截取屏幕进行翻译"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yahoo! 截屏服务")
            .setContentText("点击返回APP进行截取")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Logger.log("MediaProjection 创建失败")
                stopSelf()
                return
            }
            
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
            
            Logger.log("屏幕尺寸: ${screenWidth}x${screenHeight}, density=$screenDensity")
            
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "YahooCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )
            
            if (virtualDisplay == null) {
                Logger.log("VirtualDisplay 创建失败")
                stopSelf()
                return
            }
            
            isRunning = true
            Logger.log("截屏服务启动成功")
            
        } catch (e: Exception) {
            Logger.log("启动截屏服务异常: ${e.message}")
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun doCapture(): Bitmap? {
        Logger.log("执行截屏, imageReader=${imageReader != null}")
        
        if (imageReader == null) {
            Logger.log("imageReader 为空")
            return null
        }
        
        // 等待一帧
        Thread.sleep(100)
        
        val image = imageReader?.acquireLatestImage()
        if (image == null) {
            Logger.log("acquireLatestImage 返回 null")
            return null
        }
        
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            
            val bitmapWidth = image.width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            
            val result = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            if (bitmap != result) bitmap.recycle()
            
            Logger.log("截屏成功: ${result.width}x${result.height}")
            result
        } catch (e: Exception) {
            Logger.log("截屏处理异常: ${e.message}")
            null
        } finally {
            image.close()
        }
    }
    
    private fun stopCapture() {
        Logger.log("停止截屏服务")
        isRunning = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    override fun onDestroy() {
        Logger.log("ScreenCaptureService onDestroy")
        instance = null
        isRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
