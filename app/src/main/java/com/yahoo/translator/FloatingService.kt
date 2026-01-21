package com.yahoo.translator

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FloatingService : Service() {
    companion object {
        const val CH = "yahoo_float"
        const val NID = 2
        const val ACT_START = "START"
        const val ACT_STOP = "STOP"
        const val EX_CODE = "code"
        const val EX_DATA = "data"
        var isRunning = false
    }
    
    private lateinit var wm: WindowManager
    private var floatView: View? = null
    private var menuView: View? = null
    private var scanView: ScanLineView? = null
    private var overlayViews = mutableListOf<View>()
    
    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var ir: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var lang = OcrHelper.Language.JAPANESE
    private var screenW = 0
    private var screenH = 0
    private var isMenuOpen = false
    
    private val projCb = object : MediaProjection.Callback() {
        override fun onStop() { Logger.log("FLOAT: proj stopped") }
    }
    
    override fun onBind(i: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        screenW = m.widthPixels
        screenH = m.heightPixels
        
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "悬浮球", NotificationManager.IMPORTANCE_LOW)
            )
        }
        Logger.log("FLOAT: onCreate ${screenW}x${screenH}")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_START -> {
                startForeground(NID, createNotification())
                val code = intent.getIntExtra(EX_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EX_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EX_DATA)
                
                if (data != null && code == Activity.RESULT_OK) {
                    initProjection(code, data)
                    showFloatBall()
                    isRunning = true
                    Logger.log("FLOAT: 启动成功")
                }
            }
            ACT_STOP -> stopSelf()
        }
        return START_STICKY
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("Yahoo! 悬浮球")
            .setContentText("点击悬浮球进行翻译")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }
    
    private fun initProjection(code: Int, data: Intent) {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mp = pm.getMediaProjection(code, data)
        mp?.registerCallback(projCb, handler)
        
        ir = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        vd = mp?.createVirtualDisplay("YahooFloat", screenW, screenH,
            resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir?.surface, null, handler)
        Logger.log("FLOAT: projection ready")
    }
    
    private fun showFloatBall() {
        val size = (50 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - size - 20
            y = screenH / 3
        }
        
        floatView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setBackgroundColor(Color.parseColor("#2196F3"))
            setPadding(10, 10, 10, 10)
        }
        
        var lastX = 0
        var lastY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        
        floatView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = params.x
                    lastY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy > 100) moved = true
                    params.x = lastX + dx.toInt()
                    params.y = lastY + dy.toInt()
                    wm.updateViewLayout(floatView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMenu(params.x, params.y + size)
                    true
                }
                else -> false
            }
        }
        
        wm.addView(floatView, params)
    }
    
    private fun toggleMenu(x: Int, y: Int) {
        if (isMenuOpen) {
            hideMenu()
        } else {
            showMenu(x, y)
        }
    }
    
    private fun showMenu(x: Int, y: Int) {
        val menuW = (150 * resources.displayMetrics.density).toInt()
        val menuH = (180 * resources.displayMetrics.density).toInt()
        
        val params = WindowManager.LayoutParams(
            menuW, menuH,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        
        menuView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            elevation = 8f
            
            addButton("🔍 扫描翻译") { hideMenu(); startScan() }
            addButton("🌐 ${if (lang == OcrHelper.Language.JAPANESE) "日语" else "韩语"}") { 
                lang = if (lang == OcrHelper.Language.JAPANESE) OcrHelper.Language.KOREAN else OcrHelper.Language.JAPANESE
                hideMenu()
                Toast.makeText(this@FloatingService, "已切换到${if (lang == OcrHelper.Language.JAPANESE) "日语" else "韩语"}", Toast.LENGTH_SHORT).show()
            }
            addButton("⚙️ 设置") { hideMenu(); openSettings() }
            addButton("❌ 关闭") { hideMenu(); stopSelf() }
        }
        
        wm.addView(menuView, params)
        isMenuOpen = true
    }
    
    private fun LinearLayout.addButton(text: String, onClick: () -> Unit) {
        addView(Button(context).apply {
            this.text = text
            textSize = 14f
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        })
    }
    
    private fun hideMenu() {
        menuView?.let { wm.removeView(it) }
        menuView = null
        isMenuOpen = false
    }
    
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
    
    private fun startScan() {
        Logger.log("FLOAT: 开始扫描")
        clearOverlays()
        
        // 显示扫描线
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        scanView = ScanLineView(this)
        wm.addView(scanView, params)
        
        scanView?.startScan(2000) {
            // 扫描完成，截屏识别
            scope.launch {
                try {
                    val bmp = captureScreen()
                    wm.removeView(scanView)
                    scanView = null
                    
                    if (bmp != null) {
                        processAndOverlay(bmp)
                    } else {
                        Logger.log("FLOAT: 截屏失败")
                    }
                } catch (e: Exception) {
                    Logger.log("FLOAT: 扫描异常 ${e.message}")
                }
            }
        }
    }
    
    private fun captureScreen(): Bitmap? {
        Thread.sleep(100)
        val img = ir?.acquireLatestImage() ?: return null
        return try {
            val buf = img.planes[0].buffer
            val ps = img.planes[0].pixelStride
            val rs = img.planes[0].rowStride
            val bw = img.width + (rs - ps * img.width) / ps
            val bmp = Bitmap.createBitmap(bw, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
        } finally {
            img.close()
        }
    }
    
    private suspend fun processAndOverlay(bmp: Bitmap) {
        Logger.log("FLOAT: 处理图片")
        try {
            val blocks = OcrHelper.recognizeWithBounds(bmp, lang)
            Logger.log("FLOAT: 识别到 ${blocks.size} 个文本块")
            
            for (block in blocks) {
                if (block.text.isBlank()) continue
                val translated = translateText(block.text)
                if (translated.isNotBlank()) {
                    showOverlay(block.bounds, translated)
                }
            }
        } catch (e: Exception) {
            Logger.log("FLOAT: 处理异常 ${e.message}")
        }
    }
    
    private suspend fun translateText(text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val p = getSharedPreferences("settings", MODE_PRIVATE)
                val key = p.getString("api_key", "") ?: ""
                val url = p.getString("base_url", "") ?: ""
                val model = p.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
                if (key.isEmpty() || url.isEmpty()) return@withContext ""
                
                ApiClient.init(url, key)
                val prompt = "翻译成中文，直接输出译文："
                val res = ApiClient.get().translate(ChatRequest(model, listOf(
                    Message("system", prompt),
                    Message("user", text)
                )))
                res.choices.firstOrNull()?.message?.content ?: ""
            } catch (e: Exception) {
                Logger.log("FLOAT: 翻译异常 ${e.message}")
                ""
            }
        }
    }
    
    private fun showOverlay(bounds: Rect, text: String) {
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }
        
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            textSize = 12f
            setPadding(4, 2, 4, 2)
        }
        
        wm.addView(tv, params)
        overlayViews.add(tv)
        
        // 5秒后自动移除
        handler.postDelayed({ removeOverlay(tv) }, 5000)
    }
    
    private fun removeOverlay(v: View) {
        try {
            wm.removeView(v)
            overlayViews.remove(v)
        } catch (_: Exception) {}
    }
    
    private fun clearOverlays() {
        overlayViews.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayViews.clear()
    }
    
    override fun onDestroy() {
        Logger.log("FLOAT: onDestroy")
        isRunning = false
        floatView?.let { wm.removeView(it) }
        menuView?.let { wm.removeView(it) }
        scanView?.let { wm.removeView(it) }
        clearOverlays()
        vd?.release()
        ir?.close()
        mp?.unregisterCallback(projCb)
        mp?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
