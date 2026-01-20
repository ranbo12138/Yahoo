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
        
        scope.launch {
            delay(500)
            updateCaptureButton()
            toast("截屏服务已启动，点击按钮截取当前屏幕")
        }
    }
    
    private fun stopCaptureAndProcess() {
        scope.launch {
            resultText.text = "截屏中..."
            
            val bitmap = ScreenCaptureService.captureScreen()
            
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            updateCaptureButton()
            
            if (bitmap != null) {
                processImage(bitmap)
            } else {
                resultText.text = "截屏失败"
            }
        }
    }
    
    private fun updateCaptureButton() {
        btnCaptureScreen.text = if (ScreenCaptureService.isRunning) "截取并翻译" else "截屏翻译"
    }
    
    private fun processImage(bitmap: Bitmap) {
        scope.launch {
            try {
                resultText.text = "识别中..."
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val preprocess = prefs.getBoolean("preprocess", true)
                
                val text = OcrHelper.recognizeText(bitmap, selectedLanguage, preprocess)
                
                if (text.isBlank()) {
                    toast("未识别到文字")
                    resultText.text = ""
                } else {
                    inputText.setText(text)
                    resultText.text = ""
                    toast("识别成功")
                }
            } catch (e: Exception) {
                toast("识别失败: ${e.message}")
                resultText.text = ""
            }
        }
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
                val request = ChatRequest(model, listOf(
                    Message("system", "你是专业的翻译。将输入的文本翻译成简体中文，保持口语化，直接输出译文。"),
                    Message("user", text)
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
