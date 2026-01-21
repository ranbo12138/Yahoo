package com.yahoo.translator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Logger.log("授权回调: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_v4)
        
        Logger.log("MainActivity onCreate")
        
        findViewById<Button>(R.id.btnStartFloat).setOnClickListener {
            checkAndStart()
        }
        
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        
        updateStatus()
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun updateStatus() {
        findViewById<TextView>(R.id.tvStatus).text = if (FloatingService.isRunning) 
            "✅ 悬浮球运行中" else "⏹️ 悬浮球未启动"
        findViewById<Button>(R.id.btnStartFloat).text = if (FloatingService.isRunning)
            "关闭悬浮球" else "启动悬浮球"
    }
    
    private fun checkAndStart() {
        if (FloatingService.isRunning) {
            stopService(Intent(this, FloatingService::class.java))
            FloatingService.isRunning = false
            updateStatus()
            return
        }
        
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                Uri.parse("package:$packageName")))
            return
        }
        
        // 请求截屏权限
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(pm.createScreenCaptureIntent())
    }
    
    private fun startFloatingService(code: Int, data: Intent) {
        Intent(this, FloatingService::class.java).apply {
            action = FloatingService.ACT_START
            putExtra(FloatingService.EX_CODE, code)
            putExtra(FloatingService.EX_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(this) else startService(this)
        }
        Toast.makeText(this, "悬浮球已启动", Toast.LENGTH_SHORT).show()
        updateStatus()
    }
}
