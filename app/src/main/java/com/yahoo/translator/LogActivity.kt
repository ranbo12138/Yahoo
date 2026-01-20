package com.yahoo.translator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        
        val logText = findViewById<TextView>(R.id.logText)
        
        refreshLogs(logText)
        
        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            Logger.clear()
            refreshLogs(logText)
        }
    }
    
    private fun refreshLogs(textView: TextView) {
        val logs = Logger.getLogs()
        textView.text = if (logs.isEmpty()) {
            "暂无日志"
        } else {
            logs.joinToString("\n")
        }
    }
}
