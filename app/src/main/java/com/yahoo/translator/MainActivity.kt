package com.yahoo.translator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var languageSpinner: Spinner
    private lateinit var inputText: EditText
    private lateinit var resultText: TextView
    private var selectedLanguage = OcrHelper.Language.JAPANESE
    
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processImage(it) }
    }
    
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                processImage(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "读取图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Logger.log("读取图片失败: ${e.message}")
            }
        }
    }
    
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        languageSpinner = findViewById(R.id.languageSpinner)
        inputText = findViewById(R.id.inputText)
        resultText = findViewById(R.id.resultText)
        
        setupLanguageSpinner()
        
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            takePhoto()
        }
        
        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            pickImage()
        }
        
        findViewById<Button>(R.id.btnTranslate).setOnClickListener {
            translate()
        }
        
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
    
    private fun setupLanguageSpinner() {
        val languages = arrayOf("日语", "韩语")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedLanguage = when (position) {
                    0 -> OcrHelper.Language.JAPANESE
                    1 -> OcrHelper.Language.KOREAN
                    else -> OcrHelper.Language.JAPANESE
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun takePhoto() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                cameraLauncher.launch(null)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
    
    private fun processImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                resultText.text = "识别中..."
                val text = OcrHelper.recognizeText(bitmap, selectedLanguage)
                
                if (text.isBlank()) {
                    Toast.makeText(this@MainActivity, "未识别到文字", Toast.LENGTH_SHORT).show()
                    resultText.text = ""
                } else {
                    inputText.setText(text)
                    Toast.makeText(this@MainActivity, "识别成功", Toast.LENGTH_SHORT).show()
                    resultText.text = ""
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                resultText.text = ""
            }
        }
    }
    
    private fun translate() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        val config = ConfigManager.getConfig(this)
        if (!config.isValid()) {
            Toast.makeText(this, "请先在设置中配置 API", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                resultText.text = "翻译中..."
                val result = TranslationService.translate(text, config)
                resultText.text = result
            } catch (e: Exception) {
                resultText.text = "翻译失败: ${e.message}"
                Toast.makeText(this@MainActivity, "翻译失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
