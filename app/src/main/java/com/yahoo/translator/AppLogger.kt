package com.yahoo.translator

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val PREF = "app_logs"
    private const val KEY = "logs"
    private const val MAX = 100
    
    private var prefs: SharedPreferences? = null
    private var hidePrivacy = false
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        hidePrivacy = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("hide_privacy", false)
        
        // 记录启动信息
        val pm = ctx.packageManager
        val pi = pm.getPackageInfo(ctx.packageName, 0)
        log("=== Yahoo! v${pi.versionName} ===")
        log("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        log("系统: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    }
    
    fun setHidePrivacy(hide: Boolean) { hidePrivacy = hide }
    
    fun log(msg: String) {
        android.util.Log.d("Yahoo", msg)
        try {
            val entry = "[${fmt.format(Date())}] $msg"
            val logs = getLogs().toMutableList()
            // 去重：相同消息不重复记录
            if (logs.isNotEmpty() && logs.last().substringAfter("] ") == msg) return
            logs.add(entry)
            while (logs.size > MAX) logs.removeAt(0)
            prefs?.edit()?.putString(KEY, logs.joinToString("\n"))?.commit()
        } catch (_: Exception) {}
    }
    
    fun logApi(key: String, url: String) {
        val maskedKey = if (hidePrivacy) "sk-****" else key.take(10) + "****"
        val maskedUrl = if (hidePrivacy) "****" else url
        log("API配置: Key=$maskedKey, URL=$maskedUrl")
    }
    
    fun getLogs(): List<String> {
        val raw = prefs?.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }
    
    fun clear() { prefs?.edit()?.remove(KEY)?.commit() }
    
    fun export(): String {
        return getLogs().joinToString("\n")
    }
}
