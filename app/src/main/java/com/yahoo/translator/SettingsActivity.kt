package com.yahoo.translator

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
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
        val preprocessSwitch = findViewById<Switch>(R.id.preprocessSwitch)
        
        apiKeyInput.setText(prefs.getString("api_key", ""))
        baseUrlInput.setText(prefs.getString("base_url", "https://api.openai.com/v1/"))
        modelInput.setText(prefs.getString("model", "gpt-4o-mini"))
        preprocessSwitch.isChecked = prefs.getBoolean("preprocess", true)
        
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putString("api_key", apiKeyInput.text.toString().trim())
                .putString("base_url", baseUrlInput.text.toString().trim())
                .putString("model", modelInput.text.toString().trim())
                .putBoolean("preprocess", preprocessSwitch.isChecked)
                .apply()
            
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
