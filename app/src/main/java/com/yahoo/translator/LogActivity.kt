package com.yahoo.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogActivity : AppCompatActivity() {
    private lateinit var logText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        
        logText = findViewById(R.id.logText)
        
        refreshLogs()
        
        findViewById<Button>(R.id.btnCopyLogs).setOnClickListener {
            copyLogs()
        }
        
        findViewById<Button>(R.id.btnExportLogs).setOnClickListener {
            exportLogs()
        }
        
        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            Logger.clear()
            refreshLogs()
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun refreshLogs() {
        val logs = Logger.getLogs()
        logText.text = if (logs.isEmpty()) {
            "暂无日志"
        } else {
            logs.joinToString("\n")
        }
    }
    
    private fun copyLogs() {
        val logs = Logger.getLogs()
        if (logs.isEmpty()) {
            Toast.makeText(this, "暂无日志", Toast.LENGTH_SHORT).show()
            return
        }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Yahoo Logs", logs.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportLogs() {
        val logs = Logger.getLogs()
        if (logs.isEmpty()) {
            Toast.makeText(this, "暂无日志", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Yahoo_${timestamp}_logs.md"
            
            val docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(docsDir, fileName)
            
            val content = buildString {
                appendLine("# Yahoo! 日志")
                appendLine()
                appendLine("**导出时间**: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine()
                appendLine("---")
                appendLine()
                logs.forEach { log ->
                    appendLine("- $log")
                }
            }
            
            file.writeText(content)
            
            Toast.makeText(this, "已导出到:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
            Logger.log("日志已导出: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            Logger.log("导出失败: ${e.message}")
        }
    }
}
