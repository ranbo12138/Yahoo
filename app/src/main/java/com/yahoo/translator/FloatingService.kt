package com.yahoo.translator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FloatingService : Service() {
    companion object {
        const val CH = "yahoo_float"
        const val NID = 2
        const val ACT_START = "START"
        const val EX_CODE = "code"
        const val EX_DATA = "data"
        var isRunning = false
    }
    
    private lateinit var wm: WindowManager
    private var floatView: View? = null
    private var menuView: View? = null
    private var scanView: View? = null
    private var overlayViews = mutableListOf<View>()
    
    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var ir: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var lang = OcrHelper.Language.JAPANESE
    private var screenW = 0
    private var screenH = 0
    private var density = 1f
    private var isMenuOpen = false
    
    private var lastBitmap: Bitmap? = null
    private var lastTexts = mutableSetOf<String>()
    
    // 设置
    private var bgOpacity = 80
    private var invertColor = false
    
    private val projCb = object : MediaProjection.Callback() {
        override fun onStop() { AppLogger.log("悬浮球: 投影停止") }
    }
    
    override fun onBind(i: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m = resources.displayMetrics
        screenW = m.widthPixels
        screenH = m.heightPixels
        density = m.density
        
        loadSettings()
        
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH, "悬浮球", NotificationManager.IMPORTANCE_LOW)
            )
        }
        AppLogger.log("悬浮球: 创建 ${screenW}x${screenH}")
    }
    
    private fun loadSettings() {
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        bgOpacity = p.getInt("opacity", 80)
        invertColor = p.getBoolean("invert_color", false)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACT_START) {
            startForeground(NID, NotificationCompat.Builder(this, CH)
                .setContentTitle("Yahoo!").setContentText("悬浮球运行中")
                .setSmallIcon(android.R.drawable.ic_menu_compass).build())
            
            val code = intent.getIntExtra(EX_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EX_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EX_DATA)
            
            if (data != null && code == Activity.RESULT_OK) {
                initProjection(code, data)
                showFloatBall()
                isRunning = true
                AppLogger.log("悬浮球: 启动成功")
            }
        }
        return START_STICKY
    }
    
    private fun initProjection(code: Int, data: Intent) {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mp = pm.getMediaProjection(code, data)
        mp?.registerCallback(projCb, handler)
        ir = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        vd = mp?.createVirtualDisplay("YahooFloat", screenW, screenH,
            resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir?.surface, null, handler)
    }
    
    private fun showFloatBall() {
        val size = (25 * density).toInt()  // 25dp
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
        
        floatView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2196F3"))
            }
        }
        
        var lastX = 0; var lastY = 0
        var downX = 0f; var downY = 0f
        var moved = false
        
        floatView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = params.x; lastY = params.y
                    downX = event.rawX; downY = event.rawY
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (dx * dx + dy * dy > 100) moved = true
                    params.x = lastX + dx.toInt(); params.y = lastY + dy.toInt()
                    wm.updateViewLayout(floatView, params); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) toggleMenu(params.x, params.y + size); true }
                else -> false
            }
        }
        wm.addView(floatView, params)
    }
    
    private fun toggleMenu(x: Int, y: Int) {
        if (isMenuOpen) hideMenu() else showMenu(x, y)
    }
    
    private fun showMenu(x: Int, y: Int) {
        val menuW = (130 * density).toInt()
        val menuH = (150 * density).toInt()
        
        val params = WindowManager.LayoutParams(
            menuW, menuH,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - menuW / 2).coerceIn(0, screenW - menuW)
            this.y = y.coerceIn(0, screenH - menuH)
        }
        
        menuView = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            elevation = 8f
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 8, 8, 8)
                addBtn("🔍 扫描") { hideMenu(); startScan() }
                addBtn("🌐 ${if (lang == OcrHelper.Language.JAPANESE) "日语" else "韩语"}") {
                    lang = if (lang == OcrHelper.Language.JAPANESE) OcrHelper.Language.KOREAN else OcrHelper.Language.JAPANESE
                    hideMenu()
                    Toast.makeText(this@FloatingService, "切换到${if (lang == OcrHelper.Language.JAPANESE) "日语" else "韩语"}", Toast.LENGTH_SHORT).show()
                }
                addBtn("🗑️ 清除") { hideMenu(); clearOverlays() }
                addBtn("❌ 关闭") { hideMenu(); stopSelf() }
            })
        }
        wm.addView(menuView, params)
        isMenuOpen = true
    }
    
    private fun LinearLayout.addBtn(text: String, onClick: () -> Unit) {
        addView(TextView(context).apply {
            this.text = text
            textSize = 13f
            setPadding(12, 10, 12, 10)
            setOnClickListener { onClick() }
        })
    }
    
    private fun hideMenu() {
        menuView?.let { wm.removeView(it) }; menuView = null; isMenuOpen = false
    }
    
    private fun startScan() {
        AppLogger.log("悬浮球: 开始扫描")
        showScanLine()
        handler.postDelayed({
            hideScanLine()
            scope.launch { doCapture() }
        }, 2000)
    }
    
    private fun showScanLine() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        
        scanView = object : View(this) {
            private var lineY = 0f
            private val linePaint = Paint().apply {
                color = Color.parseColor("#2196F3")
                strokeWidth = 2 * density
            }
            private val trailPaint = Paint()
            
            init {
                android.animation.ValueAnimator.ofFloat(0f, screenH.toFloat()).apply {
                    duration = 2000
                    addUpdateListener { lineY = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            
            override fun onDraw(canvas: Canvas) {
                if (lineY <= 0) return
                val trailH = 60 * density
                val top = (lineY - trailH).coerceAtLeast(0f)
                trailPaint.shader = LinearGradient(0f, top, 0f, lineY,
                    Color.TRANSPARENT, Color.parseColor("#402196F3"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, top, width.toFloat(), lineY, trailPaint)
                canvas.drawLine(0f, lineY, width.toFloat(), lineY, linePaint)
            }
        }
        wm.addView(scanView, params)
    }
    
    private fun hideScanLine() {
        scanView?.let { wm.removeView(it) }; scanView = null
    }
    
    private suspend fun doCapture() {
        try {
            Thread.sleep(100)
            val img = ir?.acquireLatestImage() ?: return
            val buf = img.planes[0].buffer
            val ps = img.planes[0].pixelStride
            val rs = img.planes[0].rowStride
            val bw = img.width + (rs - ps * img.width) / ps
            val bmp = Bitmap.createBitmap(bw, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            val result = Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            img.close()
            
            if (lastBitmap != null && isSimilar(lastBitmap!!, result, 0.9f)) {
                AppLogger.log("悬浮球: 画面相似，跳过")
                return
            }
            lastBitmap = result
            
            AppLogger.log("悬浮球: 处理图片")
            processAndOverlay(result)
        } catch (e: Exception) {
            AppLogger.log("悬浮球: 截屏异常 ${e.message}")
        }
    }
    
    private fun isSimilar(b1: Bitmap, b2: Bitmap, threshold: Float): Boolean {
        if (b1.width != b2.width || b1.height != b2.height) return false
        var match = 0; var total = 0
        val step = 50
        for (x in 0 until b1.width step step) {
            for (y in 0 until b1.height step step) {
                if (b1.getPixel(x, y) == b2.getPixel(x, y)) match++
                total++
            }
        }
        return match.toFloat() / total >= threshold
    }
    
    private suspend fun processAndOverlay(bmp: Bitmap) {
        loadSettings() // 重新加载设置
        clearOverlays()
        val blocks = OcrHelper.recognizeWithBounds(bmp, lang)
        AppLogger.log("悬浮球: ${blocks.size} 个文本块")
        
        for (block in blocks) {
            if (block.text.isBlank() || block.text.length < 2) continue
            if (lastTexts.contains(block.text)) continue
            
            val translated = translateText(block.text)
            if (translated.isNotBlank()) {
                lastTexts.add(block.text)
                showOverlay(block.bounds, translated)
            }
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
                val res = ApiClient.get().translate(ChatRequest(model, listOf(
                    Message("system", "翻译成中文，直接输出："),
                    Message("user", text)
                )))
                res.choices.firstOrNull()?.message?.content ?: ""
            } catch (e: Exception) { "" }
        }
    }
    
    private fun showOverlay(bounds: Rect, text: String) {
        // 计算字体大小（根据 bounds 高度自适应）
        val fontSize = (bounds.height() / density / 2.5f).coerceIn(8f, 16f)
        
        // 计算背景颜色（带透明度）
        val alpha = (bgOpacity * 255 / 100)
        val bgColor = if (invertColor) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
        val textColor = if (invertColor) Color.WHITE else Color.BLACK
        
        val params = WindowManager.LayoutParams(
            bounds.width().coerceAtLeast((40 * density).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left; y = bounds.top
        }
        
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            setBackgroundColor(bgColor)
            textSize = fontSize
            setPadding(4, 2, 4, 2)
        }
        wm.addView(tv, params)
        overlayViews.add(tv)
    }
    
    private fun clearOverlays() {
        overlayViews.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayViews.clear()
        lastTexts.clear()
    }
    
    override fun onDestroy() {
        AppLogger.log("悬浮球: 销毁")
        isRunning = false
        floatView?.let { wm.removeView(it) }
        hideMenu(); hideScanLine(); clearOverlays()
        vd?.release(); ir?.close()
        mp?.unregisterCallback(projCb); mp?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
