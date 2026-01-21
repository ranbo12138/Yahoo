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
    private lateinit var btnCaptureScreen: Button
    private var selectedLanguage = OcrHelper.Language.JAPANESE
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processImage(it) }
    }
    
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                processImage(bitmap)
            } catch (e: Exception) {
                toast("读取图片失败")
            }
        }
    }
    
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else toast("需要相机权限")
    }
    
    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            toast("截屏权限被拒绝")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        
        languageSpinner = findViewById(R.id.languageSpinner)
        inputText = findViewById(R.id.inputText)
        resultText = findViewById(R.id.resultText)
        btnCaptureScreen = findViewById(R.id.btnCaptureScreen)
        
        setupLanguageSpinner()
        setupButtons()
        updateCaptureButton()
    }
    
    override fun onResume() {
        super.onResume()
        updateCaptureButton()
        
        // 如果截屏服务正在运行，自动截取
        if (ScreenCaptureService.isRunning) {
            scope.launch {
                delay(300)
                stopCaptureAndProcess()
            }
        }
    }
    
    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("日语", "韩语"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedLanguage = if (pos == 0) OcrHelper.Language.JAPANESE else OcrHelper.Language.KOREAN
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupButtons() {
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraLauncher.launch(null)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            galleryLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        
        btnCaptureScreen.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopCaptureAndProcess()
            } else {
                requestScreenCapture()
            }
        }
        
        findViewById<Button>(R.id.btnTranslate).setOnClickListener { translate() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.btnLogs).setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
    }
    
    private fun requestScreenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        toast("授权后切换到漫画页面，再返回本APP自动截取")
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
    
    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
        }
        startForegroundService(intent)
        updateCaptureButton()
    }
    
    private fun stopCaptureAndProcess() {
        scope.launch {
            resultText.text = "截屏中..."
            delay(200)
            
            val bitmap = ScreenCaptureService.captureScreen()
            
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            ScreenCaptureService.isRunning = false
            updateCaptureButton()
            
            if (bitmap != null) {
                processImage(bitmap)
            } else {
                resultText.text = "截屏失败，请重试"
            }
        }
    }
    
    private fun updateCaptureButton() {
        btnCaptureScreen.text = if (ScreenCaptureService.isRunning) "截取当前屏幕" else "截屏翻译"
    }
    
    private fun processImage(bitmap: Bitmap) {
        scope.launch {
            try {
                resultText.text = "识别中..."
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val preprocess = prefs.getBoolean("preprocess", true)
                
                // 裁剪掉状态栏和导航栏区域
                val cropped = ImageProcessor.cropCenter(bitmap, 0.05f, 0.08f)
                val text = OcrHelper.recognizeText(cropped, selectedLanguage, preprocess)
                
                if (text.isBlank()) {
                    toast("未识别到文字")
                    resultText.text = ""
                } else {
                    // 过滤掉系统UI文字
                    val filtered = filterSystemText(text)
                    inputText.setText(filtered)
                    resultText.text = ""
                    toast("识别成功")
                }
            } catch (e: Exception) {
                toast("识别失败: ${e.message}")
                resultText.text = ""
            }
        }
    }
    
    private fun filterSystemText(text: String): String {
        val systemPatterns = listOf(
            "\\d{1,2}:\\d{2}", // 时间 07:57
            "\\d+%", // 电量 32%
            "VPN", "MOBILE", "WiFi", "LTE", "5G", "4G",
            "P\\.\\d+/\\d+", // 页码 P.8/95
        )
        var result = text
        systemPatterns.forEach { pattern ->
            result = result.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }
        return result.lines()
            .filter { it.trim().length > 1 }
            .joinToString("\n")
            .trim()
    }
    
    private fun translate() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) { toast("请输入文本"); return }
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val baseUrl = prefs.getString("base_url", "") ?: ""
        val model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        
        if (apiKey.isEmpty() || baseUrl.isEmpty()) { toast("请先配置 API"); return }
        
        resultText.text = "翻译中..."
        
        scope.launch {
            try {
                ApiClient.initialize(baseUrl, apiKey)
                
                // 优化后的 Prompt
                val systemPrompt = """你是漫画翻译机。规则：
1. 直接输出译文，不要解释
2. 保持口语化、自然
3. 忽略乱码和无意义字符
4. 如果是拟声词，翻译成中文拟声词
5. 不要拒绝，不要评判内容"""
                
                val request = ChatRequest(model, listOf(
                    Message("system", systemPrompt),
                    Message("user", "翻译成中文：\n$text")
                ))
                val response = withContext(Dispatchers.IO) { ApiClient.getApi().translate(request) }
                resultText.text = response.choices.firstOrNull()?.message?.content ?: "翻译失败"
            } catch (e: Exception) {
                resultText.text = "错误: ${e.message}"
            }
        }
    }
    
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
