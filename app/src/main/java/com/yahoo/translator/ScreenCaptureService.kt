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
            Logger.log("Projection stopped")
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
        Logger.log("Service onCreate")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("Service onStartCommand")
        startForeground(NID, NotificationCompat.Builder(this, CH)
            .setContentTitle("Yahoo!")
            .setContentText("返回APP点击截取")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build())
        
        if (intent?.action == ACT) {
            val code = intent.getIntExtra(EX_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EX_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EX_DATA)
            
            Logger.log("code=$code, hasData=${data!=null}, SDK=${Build.VERSION.SDK_INT}")
            if (data != null && code == Activity.RESULT_OK) startProj(code, data)
            else { Logger.log("无效数据"); stopSelf() }
        }
        return START_STICKY
    }
    
    private fun startProj(code: Int, data: Intent) {
        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mp = pm.getMediaProjection(code, data)
            if (mp == null) { Logger.log("MP null"); stopSelf(); return }
            
            // 所有版本都注册回调（Android 14+ 强制要求）
            mp!!.registerCallback(projCallback, handler)
            Logger.log("回调已注册")
            
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            Logger.log("屏幕: ${m.widthPixels}x${m.heightPixels}")
            
            ir = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
            vd = mp!!.createVirtualDisplay("Y", m.widthPixels, m.heightPixels, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir!!.surface, null, handler)
            
            isRunning = true
            Logger.log("截屏服务启动成功!")
        } catch (e: Exception) {
            Logger.log("startProj异常: ${e.message}")
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun doCapture(): Bitmap? {
        Logger.log("doCapture")
        try {
            Thread.sleep(200)
            val img = ir?.acquireLatestImage() ?: return null.also { Logger.log("img null") }
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
        isRunning = false
        inst = null
        vd?.release()
        ir?.close()
        try { mp?.unregisterCallback(projCallback) } catch (_: Exception) {}
        mp?.stop()
    }
    
    override fun onDestroy() {
        Logger.log("Service onDestroy")
        cleanup()
        super.onDestroy()
    }
}
