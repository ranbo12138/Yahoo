package com.yahoo.translator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var inputText: EditText
    private lateinit var resultText: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        inputText = findViewById(R.id.inputText)
        resultText = findViewById(R.id.resultText)
        
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
    
    private fun translate() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
            return
        }
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val baseUrl = prefs.getString("base_url", "") ?: ""
        val model = prefs.getString("model", "gpt-4.1-mini") ?: "gpt-4.1-mini"
        
        if (apiKey.isEmpty() || baseUrl.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置 API", Toast.LENGTH_SHORT).show()
            return
        }
        
        resultText.text = "翻译中..."
        Logger.log("开始翻译: $text")
        
        scope.launch {
            try {
                ApiClient.initialize(baseUrl, apiKey)
                
                val request = ChatRequest(
                    model = model,
                    messages = listOf(
                        Message("system", "你是专业的日译中翻译。将日文翻译成简体中文，保持口语化，直接输出译文。"),
                        Message("user", text)
                    )
                )
                
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApi().translate(request)
                }
                
                val result = response.choices.firstOrNull()?.message?.content ?: "翻译失败"
                resultText.text = result
                Logger.log("翻译成功: $result")
                
            } catch (e: Exception) {
                val error = "错误: ${e.message}"
                resultText.text = error
                Logger.log(error)
                Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
