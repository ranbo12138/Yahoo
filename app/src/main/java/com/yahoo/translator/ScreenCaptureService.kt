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
    
    private val cb = object : MediaProjection.Callback() {
        override fun onStop() {
            Logger.log("CB: onStop")
            cleanup()
        }
    }
    
    override fun onBind(i: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        inst = this
        Logger.log("SVC: onCreate SDK=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "Yahoo", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("SVC: onStartCommand")
        startForeground(NID, NotificationCompat.Builder(this, CH)
            .setContentTitle("Yahoo!").setContentText("截屏服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build())
        
        if (intent?.action == ACT) {
            val code = intent.getIntExtra(EX_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EX_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EX_DATA)
            
            Logger.log("SVC: code=$code hasData=${data!=null}")
            if (data != null && code == Activity.RESULT_OK) startProj(code, data)
            else { Logger.log("SVC: 无效数据"); stopSelf() }
        }
        return START_STICKY
    }
    
    private fun startProj(code: Int, data: Intent) {
        Logger.log("PROJ: 开始初始化")
        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            
            Logger.log("PROJ: 获取MediaProjection")
            mp = pm.getMediaProjection(code, data)
            if (mp == null) { Logger.log("PROJ: mp=null"); stopSelf(); return }
            
            Logger.log("PROJ: 注册回调")
            mp!!.registerCallback(cb, handler)
            Logger.log("PROJ: 回调已注册")
            
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            Logger.log("PROJ: 屏幕=${m.widthPixels}x${m.heightPixels}")
            
            Logger.log("PROJ: 创建ImageReader")
            ir = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
            
            Logger.log("PROJ: 创建VirtualDisplay")
            vd = mp!!.createVirtualDisplay("Y", m.widthPixels, m.heightPixels, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir!!.surface, null, handler)
            
            isRunning = true
            Logger.log("PROJ: 启动成功!")
        } catch (e: Exception) {
            Logger.log("PROJ: 异常 ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
        }
    }
    
    private fun doCapture(): Bitmap? {
        Logger.log("CAP: 开始")
        try {
            Thread.sleep(200)
            val img = ir?.acquireLatestImage() ?: return null.also { Logger.log("CAP: img=null") }
            val buf = img.planes[0].buffer
            val ps = img.planes[0].pixelStride
            val rs = img.planes[0].rowStride
            val bw = img.width + (rs - ps * img.width) / ps
            val bmp = Bitmap.createBitmap(bw, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            val r = Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            img.close()
            Logger.log("CAP: 成功 ${r.width}x${r.height}")
            return r
        } catch (e: Exception) {
            Logger.log("CAP: 异常 ${e.message}")
            return null
        }
    }
    
    private fun cleanup() {
        isRunning = false; inst = null
        try { vd?.release() } catch (_: Exception) {}
        try { ir?.close() } catch (_: Exception) {}
        try { mp?.unregisterCallback(cb) } catch (_: Exception) {}
        try { mp?.stop() } catch (_: Exception) {}
    }
    
    override fun onDestroy() {
        Logger.log("SVC: onDestroy")
        cleanup()
        super.onDestroy()
    }
}
