package com.yahoo.translator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var languageSpinner: Spinner
    private lateinit var inputText: EditText
    private lateinit var resultText: TextView
    private lateinit var btnCapture: Button
    private var selectedLang = OcrHelper.Language.JAPANESE
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { 
        it?.let { processImage(it) } 
    }
    
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        it.data?.data?.let { uri ->
            try {
                processImage(MediaStore.Images.Media.getBitmap(contentResolver, uri))
            } catch (e: Exception) { toast("读取失败") }
        }
    }
    
    private val camPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { 
        if (it) cameraLauncher.launch(null) else toast("需要相机权限")
    }
    
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Logger.log("截屏授权回调: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            toast("授权失败")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Logger.log("MainActivity onCreate")
        
        setSupportActionBar(findViewById(R.id.toolbar))
        languageSpinner = findViewById(R.id.languageSpinner)
        inputText = findViewById(R.id.inputText)
        resultText = findViewById(R.id.resultText)
        btnCapture = findViewById(R.id.btnCaptureScreen)
        
        setupSpinner()
        setupButtons()
        updateBtn()
    }
    
    override fun onResume() {
        super.onResume()
        Logger.log("onResume, isRunning=${ScreenCaptureService.isRunning}")
        updateBtn()
    }
    
    private fun setupSpinner() {
        languageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("日语", "韩语")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                selectedLang = if (pos == 0) OcrHelper.Language.JAPANESE else OcrHelper.Language.KOREAN
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }
    
    private fun setupButtons() {
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                cameraLauncher.launch(null)
            else camPermLauncher.launch(Manifest.permission.CAMERA)
        }
        
        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            galleryLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        
        btnCapture.setOnClickListener {
            Logger.log("点击截屏按钮, isRunning=${ScreenCaptureService.isRunning}")
            if (ScreenCaptureService.isRunning) {
                doCapture()
            } else {
                requestCapture()
            }
        }
        
        findViewById<Button>(R.id.btnTranslate).setOnClickListener { translate() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.btnLogs).setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
    }
    
    private fun requestCapture() {
        Logger.log("请求截屏权限")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(pm.createScreenCaptureIntent())
    }
    
    private fun startCaptureService(code: Int, data: Intent) {
        Logger.log("启动服务")
        Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, code)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(this) else startService(this)
        }
        
        scope.launch {
            delay(800)
            updateBtn()
            toast(if (ScreenCaptureService.isRunning) "服务已启动，切换到漫画后返回点击截取" else "启动失败，查看日志")
        }
    }
    
    private fun doCapture() {
        Logger.log("执行截屏")
        scope.launch {
            resultText.text = "截屏中..."
            delay(200)
            
            val bmp = ScreenCaptureService.captureScreen()
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            ScreenCaptureService.isRunning = false
            updateBtn()
            
            if (bmp != null) processImage(bmp) else { resultText.text = "截屏失败"; toast("失败") }
        }
    }
    
    private fun updateBtn() {
        btnCapture.text = if (ScreenCaptureService.isRunning) "📷 截取屏幕" else "截屏翻译"
    }
    
    private fun processImage(bmp: Bitmap) {
        scope.launch {
            try {
                resultText.text = "识别中..."
                val pre = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("preprocess", true)
                val cropped = ImageProcessor.cropCenter(bmp, 0.05f, 0.08f)
                val text = OcrHelper.recognizeText(cropped, selectedLang, pre)
                
                if (text.isBlank()) { toast("未识别到文字"); resultText.text = "" }
                else { inputText.setText(filterSys(text)); resultText.text = ""; toast("识别成功") }
            } catch (e: Exception) {
                Logger.log("识别异常: ${e.message}")
                toast("识别失败"); resultText.text = ""
            }
        }
    }
    
    private fun filterSys(t: String): String {
        var r = t
        listOf("\\d{1,2}:\\d{2}", "\\d+%", "VPN", "MOBILE", "WiFi", "P\\.\\d+/\\d+").forEach {
            r = r.replace(Regex(it, RegexOption.IGNORE_CASE), "")
        }
        return r.lines().filter { it.trim().length > 1 }.joinToString("\n").trim()
    }
    
    private fun translate() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) { toast("请输入文本"); return }
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val key = prefs.getString("api_key", "") ?: ""
        val url = prefs.getString("base_url", "") ?: ""
        val model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        
        if (key.isEmpty() || url.isEmpty()) { toast("请先配置API"); return }
        
        resultText.text = "翻译中..."
        Logger.log("开始翻译")
        
        scope.launch {
            try {
                ApiClient.initialize(url, key)
                
                // 优化后的 Prompt
                val prompt = """你是日漫翻译专家。规则：
1. 输入是OCR识别结果，可能有错字乱码
2. 推测原文正确内容后翻译成中文
3. 忽略无意义乱码
4. 拟声词翻译成中文拟声词
5. 直接输出译文，不解释不拒绝"""
                
                val req = ChatRequest(model, listOf(Message("system", prompt), Message("user", "翻译：$text")))
                val res = withContext(Dispatchers.IO) { ApiClient.getApi().translate(req) }
                resultText.text = res.choices.firstOrNull()?.message?.content ?: "翻译失败"
                Logger.log("翻译完成")
            } catch (e: Exception) {
                Logger.log("翻译异常: ${e.message}")
                resultText.text = "错误: ${e.message}"
            }
        }
    }
    
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
