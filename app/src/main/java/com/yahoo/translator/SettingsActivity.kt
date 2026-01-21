package com.yahoo.translator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var modelList = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        
        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnCheck = findViewById<Button>(R.id.btnCheck)
        val spModel = findViewById<Spinner>(R.id.spModel)
        val sbOpacity = findViewById<SeekBar>(R.id.sbOpacity)
        val etOpacity = findViewById<EditText>(R.id.etOpacity)
        val swInvert = findViewById<Switch>(R.id.swInvert)
        val swDevMode = findViewById<Switch>(R.id.swDevMode)
        val swHidePrivacy = findViewById<Switch>(R.id.swHidePrivacy)
        val llLogButtons = findViewById<LinearLayout>(R.id.llLogButtons)
        
        // 加载设置
        etBaseUrl.setText(prefs.getString("base_url", "https://api.openai.com/v1/"))
        etApiKey.setText(prefs.getString("api_key", ""))
        sbOpacity.progress = prefs.getInt("opacity", 80)
        etOpacity.setText(sbOpacity.progress.toString())
        swInvert.isChecked = prefs.getBoolean("invert_color", false)
        swDevMode.isChecked = prefs.getBoolean("dev_mode", false)
        swHidePrivacy.isChecked = prefs.getBoolean("hide_privacy", false)
        llLogButtons.visibility = if (swDevMode.isChecked) View.VISIBLE else View.GONE
        
        // 模型下拉框
        val savedModel = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        modelList.add(savedModel)
        updateModelSpinner(spModel, savedModel)
        
        // 透明度联动
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                etOpacity.setText(progress.toString())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        etOpacity.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = etOpacity.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 80
                sbOpacity.progress = v
                etOpacity.setText(v.toString())
            }
        }
        
        // 开发者模式
        swDevMode.setOnCheckedChangeListener { _, checked ->
            llLogButtons.visibility = if (checked) View.VISIBLE else View.GONE
        }
        
        // 检查按钮
        btnCheck.setOnClickListener {
            val url = etBaseUrl.text.toString().trim()
            val key = etApiKey.text.toString().trim()
            if (url.isEmpty() || key.isEmpty()) {
                Toast.makeText(this, "请填写 URL 和 Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            btnCheck.isEnabled = false
            btnCheck.text = "检查中..."
            AppLogger.log("检查 API 连接")
            
            scope.launch {
                try {
                    ApiClient.init(url, key)
                    val result = ApiClient.testConnection()
                    if (result.isSuccess) {
                        val models = result.getOrNull() ?: emptyList()
                        modelList.clear()
                        modelList.addAll(models)
                        updateModelSpinner(spModel, savedModel)
                        btnCheck.text = "✅"
                        AppLogger.log("API 检查成功，${models.size} 个模型")
                        Toast.makeText(this@SettingsActivity, "连接成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        throw result.exceptionOrNull() ?: Exception("未知错误")
                    }
                } catch (e: Exception) {
                    btnCheck.text = "❌"
                    AppLogger.log("API 检查失败: ${e.message}")
                    Toast.makeText(this@SettingsActivity, "失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                btnCheck.isEnabled = true
            }
        }
        
        // 日志按钮
        findViewById<Button>(R.id.btnAppLog).setOnClickListener {
            startActivity(Intent(this, AppLogActivity::class.java))
        }
        findViewById<Button>(R.id.btnAiLog).setOnClickListener {
            startActivity(Intent(this, AILogActivity::class.java))
        }
        
        // 保存
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val url = etBaseUrl.text.toString().trim()
            val key = etApiKey.text.toString().trim()
            val model = spModel.selectedItem?.toString() ?: "gpt-4o-mini"
            val opacity = sbOpacity.progress
            
            prefs.edit()
                .putString("base_url", url)
                .putString("api_key", key)
                .putString("model", model)
                .putInt("opacity", opacity)
                .putBoolean("invert_color", swInvert.isChecked)
                .putBoolean("dev_mode", swDevMode.isChecked)
                .putBoolean("hide_privacy", swHidePrivacy.isChecked)
                .apply()
            
            AppLogger.setHidePrivacy(swHidePrivacy.isChecked)
            AppLogger.logApi(key, url)
            AppLogger.log("设置已保存")
            
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun updateModelSpinner(sp: Spinner, selected: String) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        val idx = modelList.indexOf(selected)
        if (idx >= 0) sp.setSelection(idx)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
