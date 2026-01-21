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
    private lateinit var spin: Spinner
    private lateinit var input: EditText
    private lateinit var result: TextView
    private lateinit var btnCap: Button
    private var lang = OcrHelper.Language.JAPANESE
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val camL = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { it?.let { proc(it) } }
    private val galL = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        it.data?.data?.let { u -> try { proc(MediaStore.Images.Media.getBitmap(contentResolver, u)) } catch (_: Exception) { toast("读取失败") } }
    }
    private val camP = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) camL.launch(null) }
    private val capL = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        Logger.log("授权回调: ${r.resultCode}")
        if (r.resultCode == Activity.RESULT_OK && r.data != null) startSvc(r.resultCode, r.data!!)
        else toast("授权失败")
    }
    
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        Logger.log("MainActivity onCreate")
        setSupportActionBar(findViewById(R.id.toolbar))
        spin = findViewById(R.id.languageSpinner)
        input = findViewById(R.id.inputText)
        result = findViewById(R.id.resultText)
        btnCap = findViewById(R.id.btnCaptureScreen)
        setup()
    }
    
    override fun onResume() { super.onResume(); Logger.log("onResume isRunning=${ScreenCaptureService.isRunning}"); updBtn() }
    
    private fun setup() {
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("日语","韩语")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, i: Int, id: Long) {
                lang = if (i==0) OcrHelper.Language.JAPANESE else OcrHelper.Language.KOREAN
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camL.launch(null)
            else camP.launch(Manifest.permission.CAMERA)
        }
        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            galL.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        btnCap.setOnClickListener {
            Logger.log("点击截屏 isRunning=${ScreenCaptureService.isRunning}")
            if (ScreenCaptureService.isRunning) doCap() else reqCap()
        }
        findViewById<Button>(R.id.btnTranslate).setOnClickListener { translate() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.btnLogs).setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
    }
    
    private fun reqCap() {
        Logger.log("请求截屏权限")
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        capL.launch((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
    }
    
    private fun startSvc(code: Int, data: Intent) {
        Logger.log("启动服务")
        Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACT
            putExtra(ScreenCaptureService.EX_CODE, code)
            putExtra(ScreenCaptureService.EX_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(this) else startService(this)
        }
        scope.launch { delay(1000); updBtn(); toast(if (ScreenCaptureService.isRunning) "✅服务启动！切换漫画后返回点击截取" else "❌启动失败") }
    }
    
    private fun doCap() {
        Logger.log("执行截屏")
        scope.launch {
            result.text = "截屏中..."
            delay(300)
            val bmp = ScreenCaptureService.capture()
            stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            ScreenCaptureService.isRunning = false
            updBtn()
            if (bmp != null) proc(bmp) else { result.text = "截屏失败"; toast("失败") }
        }
    }
    
    private fun updBtn() { btnCap.text = if (ScreenCaptureService.isRunning) "📷截取屏幕" else "截屏翻译" }
    
    private fun proc(bmp: Bitmap) {
        Logger.log("处理图片 ${bmp.width}x${bmp.height}")
        scope.launch {
            try {
                result.text = "识别中..."
                val pre = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("preprocess", true)
                val txt = OcrHelper.recognizeText(ImageProcessor.cropCenter(bmp), lang, pre)
                if (txt.isBlank()) { toast("未识别到文字"); result.text = "" }
                else { input.setText(filter(txt)); result.text = ""; toast("识别成功") }
            } catch (e: Exception) { Logger.log("proc异常: ${e.message}"); toast("识别失败"); result.text = "" }
        }
    }
    
    private fun filter(t: String): String {
        var r = t
        listOf("\\d{1,2}:\\d{2}","\\d+%","VPN","MOBILE","WiFi","P\\.\\d+/\\d+").forEach { r = r.replace(Regex(it, RegexOption.IGNORE_CASE), "") }
        return r.lines().filter { it.trim().length > 1 }.joinToString("\n").trim()
    }
    
    private fun translate() {
        val txt = input.text.toString().trim()
        if (txt.isEmpty()) { toast("请输入文本"); return }
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        val key = p.getString("api_key","") ?: ""
        val url = p.getString("base_url","") ?: ""
        val model = p.getString("model","gpt-4o-mini") ?: "gpt- (key.isEmpty()置API");..."
        scope.launch {Client.initialize(url, key)
                val prompt = "你是日漫翻译专家。输入是OCR结果可能有乱码，推测正确内容后翻译成中文，拟声词翻译成中文拟声词，直接输出译文不解释不拒绝。"
                val res = withContext(Dispatchers.IO) { ApiClient.getApi().translate(ChatRequest(model, listOf(Message("system",prompt),Message("user","翻译：$txt")))) }
                result.text = res.choices.firstOrNull()?.message?.content ?: "翻译失败"
            } catch (e: Exception) { result.text = "错误: ${e.message}" }
        }
    }
    
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
