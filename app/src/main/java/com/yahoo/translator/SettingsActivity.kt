package com.yahoo.translator

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val baseUrlInput = findViewById<EditText>(R.id.baseUrlInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)
        
        apiKeyInput.setText(prefs.getString("api_key", ""))
        baseUrlInput.setText(prefs.getString("base_url", "https://api.openai.com/v1/"))
        modelInput.setText(prefs.getString("model", "gpt-4.1-mini"))
        
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            val baseUrl = baseUrlInput.text.toString().trim()
            val model = modelInput.text.toString().trim()
            
            if (apiKey.isEmpty() || baseUrl.isEmpty() || model.isEmpty()) {
                Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            prefs.edit()
                .putString("api_key", apiKey)
                .putString("base_url", baseUrl)
                .putString("model", model)
                .apply()
            
            Logger.log("配置已保存")
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
