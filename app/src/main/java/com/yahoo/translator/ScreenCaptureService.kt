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
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    companion object {
        const val CH = "yahoo"
        const val NID = 1
        const val ACT = "START"
        const val EX_CODE = "code"
        const val EX_DATA = "data"
        @Volatile var isRunning = false
        private var inst: ScreenCaptureService? = null
        fun capture(): Bitmap? = inst?.doCapture()
    }
    
    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var ir: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val projCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Logger.log("MediaProjection onStop")
            cleanup()
        }
    }
    
    override fun onBind(i: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        inst = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "Yahoo", NotificationManager.IMPORTANCE_LOW)
            )
        }
        Logger.log("Service onCreate, SDK=${Build.VERSION.SDK_INT}")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("Service onStartCommand")
        startForeground(NID, NotificationCompat.Builder(this, CH)
            .setContentTitle("Yahoo!")
            .setContentText("返回APP点击截取")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build())
        Logger.log("前台服务已启动")
        
        if (intent?.action == ACT) {
            val code = intent.getIntExtra(EX_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EX_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EX_DATA)
            
            Logger.log("code=$code, hasData=${data!=null}")
            if (data != null && code == Activity.RESULT_OK) {
                startProj(code, data)
            } else {
                Logger.log("无效数据，停止服务")
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    private fun startProj(code: Int, data: Intent) {
        Logger.log("startProj开始")
        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            Logger.log("获取MediaProjection...")
            mp = pm.getMediaProjection(code, data)
            
            if (mp == null) {
                Logger.log("MediaProjection为null")
                stopSelf()
                return
            }
            Logger.log("MediaProjection获取成功")
            
            // 立即注册回调（Android 14强制要求，必须在createVirtualDisplay之前）
            Logger.log("注册回调...")
            mp!!.registerCallback(projCallback, handler)
            Logger.log("回调注册成功")
            
            // 获取屏幕尺寸
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            Logger.log("屏幕: ${m.widthPixels}x${m.heightPixels}, dpi=${m.densityDpi}")
            
            // 创建ImageReader
            Logger.log("创建ImageReader...")
            ir = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
            Logger.log("ImageReader创建成功")
            
            // 创建VirtualDisplay
            Logger.log("创建VirtualDisplay...")
            vd = mp!!.createVirtualDisplay(
                "YahooCapture",
                m.widthPixels, m.heightPixels, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                ir!!.surface,
                null,
                handler
            )
            Logger.log("VirtualDisplay创建成功")
            
            isRunning = true
            Logger.log("截屏服务完全启动!")
            
        } catch (e: Exception) {
            Logger.log("startProj异常: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
        }
    }
    
    private fun doCapture(): Bitmap? {
        Logger.log("doCapture开始")
        if (ir == null) {
            Logger.log("ImageReader为null")
            return null
        }
        
        try {
            Thread.sleep(200)
            val img = ir?.acquireLatestImage()
            if (img == null) {
                Logger.log("acquireLatestImage返回null")
                return null
            }
            
            Logger.log("获取到图像: ${img.width}x${img.height}")
            val buf = img.planes[0].buffer
            val ps = img.planes[0].pixelStride
            val rs = img.planes[0].rowStride
            val bw = img.width + (rs - ps * img.width) / ps
            val bmp = Bitmap.createBitmap(bw, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            val r = Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            img.close()
            Logger.log("截屏成功: ${r.width}x${r.height}")
            return r
        } catch (e: Exception) {
            Logger.log("doCapture异常: ${e.message}")
            return null
        }
    }
    
    private fun cleanup() {
        Logger.log("cleanup")
        isRunning = false
        inst = null
        try { vd?.release() } catch (_: Exception) {}
        try { ir?.close() } catch (_: Exception) {}
        try { mp?.unregisterCallback(projCallback) } catch (_: Exception) {}
        try { mp?.stop() } catch (_: Exception) {}
    }
    
    override fun onDestroy() {
        Logger.log("Service onDestroy")
        cleanup()
        super.onDestroy()
    }
}
