package com.yahoo.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_log)
        
        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        refreshLogs(tvLogs)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        
        findViewById<ImageButton>(R.id.btnCopy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("logs", AppLogger.export()))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<ImageButton>(R.id.btnExport).setOnClickListener {
            try {
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Yahoo_App_${time}.txt")
                file.writeText(AppLogger.export())
                Toast.makeText(this, "已导出: ${file.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener {
            AppLogger.clear()
            refreshLogs(tvLogs)
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun refreshLogs(tv: TextView) {
        val logs = AppLogger.getLogs()
        tv.text = if (logs.isEmpty()) "暂无日志" else logs.joinToString("\n")
    }
}
