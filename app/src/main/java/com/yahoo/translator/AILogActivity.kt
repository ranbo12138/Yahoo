package com.yahoo.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AILogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_log)
        
        val llLogs = findViewById<LinearLayout>(R.id.llLogs)
        refreshLogs(llLogs)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        
        findViewById<ImageButton>(R.id.btnCopy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ai_logs", AILogger.export()))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<ImageButton>(R.id.btnExport).setOnClickListener {
            try {
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Yahoo_AI_${time}.txt")
                file.writeText(AILogger.export())
                Toast.makeText(this, "已导出: ${file.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener {
            AILogger.clear()
            refreshLogs(llLogs)
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun refreshLogs(ll: LinearLayout) {
        ll.removeAllViews()
        val logs = AILogger.getLogs()
        
        if (logs.isEmpty()) {
            ll.addView(TextView(this).apply { text = "暂无日志"; setPadding(16, 16, 16, 16) })
            return
        }
        
        for (entry in logs) {
            ll.addView(createLogCard(entry))
        }
    }
    
    private fun createLogCard(entry: AILogEntry): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            elevation = 2f
            
            addView(LinearLayout(context).apply {
                addView(TextView(context).apply {
                    text = entry.method
                    setTextColor(Color.parseColor("#2196F3"))
                    textSize = 14f
                })
                addView(TextView(context).apply {
                    text = "  ${entry.time}"
                    setTextColor(Color.GRAY)
                    textSize = 12f
                })
            })
            
            addView(TextView(context).apply {
                text = entry.url
                textSize = 11f
                setTextColor(Color.parseColor("#666666"))
            })
            
            addView(TextView(context).apply {
                text = "Status: ${entry.status}  ${entry.duration}ms"
                textSize = 12f
                setTextColor(if (entry.status == 200) Color.parseColor("#4CAF50") else Color.RED)
            })
            
            val detailView = TextView(context).apply {
                text = "── Request ──\n${entry.requestBody.take(200)}\n\n── Response ──\n${entry.responseBody.take(300)}"
                textSize = 10f
                visibility = View.GONE
                setPadding(0, 8, 0, 0)
            }
            addView(detailView)
            
            setOnClickListener {
                detailView.visibility = if (detailView.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
    }
}
